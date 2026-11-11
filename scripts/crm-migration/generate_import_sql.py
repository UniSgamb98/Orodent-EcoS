#!/usr/bin/env python3
"""Genera gli script Derby per importare l'esportazione del vecchio CRM.

Il comando non modifica il database.  Produce file SQL ordinati, rieseguibili solo
su un database vuoto (gli operatori sono invece inseriti con una guardia), e un
report con le anomalie riscontrate.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import tempfile
import re
import sys
import uuid
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Iterable

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR
DEFAULT_INPUT_DIR = SCRIPT_DIR / "input"
DEFAULT_CLIENTS_FILE = DEFAULT_INPUT_DIR / "clients.txt"
DEFAULT_NOTES_FILE = DEFAULT_INPUT_DIR / "tutte_le_note.txt"
SPECIAL_NULLS = {"", "?", "??", "???", "BLANK", "NULL", "NULLO"}
UNKNOWN_OPERATOR = "NON SPECIFICATO CRM2"
NS = uuid.UUID("8a05d4bc-97cc-4df0-bf06-000000000000")
FIELDS = [
    "ragione_sociale", "persona_riferimento", "email_referente", "telefono", "paese", "regione", "citta",
    "indirizzo", "numero_civico", "provincia", "cap", "interessamento", "tipo_cliente", "partita_iva",
    "codice_fiscale", "titolare", "email_generica", "email_certificata", "sito_web", "note_id", "operatore",
    "volte_contattati", "ultima_chiamata", "prossima_chiamata", "coinvolgimento", "acquisizione", "checkpoint",
    "telefono2", "cellulare",
]
MARKER = re.compile(
    r"===== FILE:\s*(.+?)\.xml(?:\.txt)?\s*=====\s*(.*?)\s*===== END FILE:\s*\1\.xml(?:\.txt)?\s*=====",
    re.DOTALL,
)


@dataclass
class Call:
    note_id: str
    ordinal: int
    number: str | None
    data: str | None
    operatore: str | None
    previous_interest: str | None
    new_interest: str | None
    checkpoint: str | None
    durata: str | None
    messaggio: str | None
    text: str


@dataclass
class Client:
    rownum: int
    raw: dict[str, str | None]
    calls: list[Call] = field(default_factory=list)
    id: str = field(init=False)

    def __post_init__(self) -> None:
        key = self.raw.get("note_id") or f"riga-{self.rownum}-{self.raw.get('ragione_sociale') or ''}"
        self.id = uid("cliente", key)


@dataclass
class Diagnostics:
    invalid_dates: set[str] = field(default_factory=set)
    invalid_involvement_rows: list[int] = field(default_factory=list)
    xml_errors: list[str] = field(default_factory=list)
    duplicate_note_ids: list[str] = field(default_factory=list)
    unmatched_note_ids: list[str] = field(default_factory=list)
    missing_note_ids: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class ClientRowIssue:
    rownum: int
    customer: str
    field_count: int


class ClientFormatError(ValueError):
    def __init__(self, issues: list[ClientRowIssue]):
        super().__init__(f"{len(issues)} righe clienti hanno un numero di campi errato")
        self.issues = issues


def clean(value: str | None) -> str | None:
    if value is None:
        return None
    stripped = value.strip()
    if "\x00" in stripped:
        raise ValueError("Il backup contiene un carattere NUL non importabile.")
    return None if stripped.upper() in SPECIAL_NULLS else stripped


def normalized_operator(value: str | None) -> str | None:
    value = clean(value)
    return value.upper() if value else None


def uid(kind: str, key: str) -> str:
    return str(uuid.uuid5(NS, f"{kind}:{key}"))


def sql(value: str | None) -> str:
    value = clean(value)
    return "NULL" if value is None else "'" + value.replace("'", "''") + "'"


def valid_iso_date(value: str | None) -> str | None:
    value = clean(value)
    if not value:
        return None
    try:
        return date.fromisoformat(value).isoformat()
    except ValueError:
        return None


def sql_date(value: str | None, diagnostics: Diagnostics) -> str:
    parsed = valid_iso_date(value)
    if parsed:
        return f"DATE('{parsed}')"
    if clean(value):
        diagnostics.invalid_dates.add(clean(value) or "")
    return "NULL"


def parse_involvement(value: str | None) -> int | None:
    value = clean(value)
    if value is None:
        return None
    try:
        parsed = Decimal(value)
        if not parsed.is_finite():
            return None
        integer = int(parsed)
    except (InvalidOperation, ValueError, OverflowError):
        return None
    return integer if parsed == integer and 1 <= integer <= 5 else None


def parse_clients(path: Path, diagnostics: Diagnostics) -> list[Client]:
    clients: list[Client] = []
    issues: list[ClientRowIssue] = []
    for rownum, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        if not line.strip():
            continue
        parts = line.split(";")
        # È ammesso un solo separatore finale; altri campi mancanti/eccedenti sono segnalati.
        logical_length = len(parts)
        if logical_length == len(FIELDS) + 1 and parts[-1] == "":
            logical_length -= 1
        if logical_length != len(FIELDS):
            customer = clean(parts[0]) if parts else None
            issues.append(ClientRowIssue(rownum, customer or "<ragione sociale vuota>", logical_length))
            continue
        parts = (parts + [""] * len(FIELDS))[:len(FIELDS)]
        raw = {name: clean(parts[index]) for index, name in enumerate(FIELDS)}
        if raw["coinvolgimento"] and parse_involvement(raw["coinvolgimento"]) is None:
            diagnostics.invalid_involvement_rows.append(rownum)
        clients.append(Client(rownum, raw))
    if issues:
        raise ClientFormatError(issues)
    return clients


def print_client_format_error(error: ClientFormatError) -> None:
    print("ERRORE: generazione interrotta; righe clienti con numero di campi errato:", file=sys.stderr)
    for issue in error.issues:
        difference = issue.field_count - len(FIELDS)
        amount = abs(difference)
        noun = "campo" if amount == 1 else "campi"
        if difference > 0:
            problem = f"{amount} {noun} extra"
        else:
            problem = f"{amount} {noun} {'mancante' if amount == 1 else 'mancanti'}"
        print(
            f"- riga {issue.rownum}: {issue.customer} "
            f"(trovati {issue.field_count}, attesi {len(FIELDS)}: {problem})",
            file=sys.stderr,
        )
    print("Controllare eventuali caratteri ';' presenti dentro i campi testuali.", file=sys.stderr)


def parse_merged_notes(path: Path, diagnostics: Diagnostics) -> dict[str, list[Call]]:
    documents: dict[str, list[Call]] = {}
    data = path.read_bytes()
    text = data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")
    matches = MARKER.findall(text)
    if text.strip() and not matches:
        raise ValueError("Il file note non contiene i marcatori FILE / END FILE attesi.")
    for note_id, xml_text in matches:
        note_id = note_id.strip()
        if note_id in documents:
            diagnostics.duplicate_note_ids.append(note_id)
            continue
        try:
            root = ET.fromstring(xml_text.strip())
        except ET.ParseError as error:
            diagnostics.xml_errors.append(f"{note_id}: {error}")
            continue
        calls: list[Call] = []
        for ordinal, elem in enumerate(root.findall("chiamata")):
            calls.append(Call(
                note_id, ordinal, clean(elem.get("number")), clean(elem.get("data")),
                normalized_operator(elem.get("operatore")), clean(elem.get("previousInterest")),
                clean(elem.get("newInterest")), clean(elem.get("checkpoint")), clean(elem.get("durata")),
                clean(elem.get("messaggio")), "".join(elem.itertext()).strip(),
            ))
        documents[note_id] = calls
    return documents


def chronological_key(call: Call) -> tuple[str, int, int]:
    number = int(call.number) if (call.number or "").isdigit() else -1
    return (valid_iso_date(call.data) or "9999-12-31", number, call.ordinal)


def final_interest(client: Client) -> str | None:
    # Senza una data valida non è possibile stabilire che una chiamata sia cronologicamente l'ultima.
    changes = [call for call in client.calls if call.new_interest and valid_iso_date(call.data)]
    return max(changes, key=chronological_key).new_interest if changes else client.raw.get("interessamento")


def note_text(call: Call) -> str:
    lines: list[str] = []
    if call.data and not valid_iso_date(call.data):
        lines.append(f"Data originale non valida: {call.data}")
    if call.number:
        lines.append(f"[Chiamata #{call.number}]")
    for label, value in (
        ("Stato precedente", call.previous_interest), ("Nuovo stato", call.new_interest),
        ("Checkpoint", call.checkpoint), ("Durata", call.durata), ("Messaggio", call.messaggio),
    ):
        if value:
            lines.append(f"{label}: {value}")
    if lines and call.text:
        lines.append("")
    if call.text:
        lines.append(call.text)
    return "\n".join(lines)


def operator_expression(operator: str | None) -> str:
    operator = normalized_operator(operator) or UNKNOWN_OPERATOR
    return f"(SELECT ID FROM ECOS.OPERATORI WHERE UPPER(TRIM(USERNAME)) = {sql(operator)})"


def parse_notes(path: Path, diagnostics: Diagnostics) -> dict[str, list[Call]]:
    if path.is_file():
        return parse_merged_notes(path, diagnostics)
    documents = {}
    files = sorted((p for p in path.iterdir() if p.is_file() and p.suffix.casefold() == ".xml"), key=lambda p: p.name.casefold())
    if not files:
        raise ValueError("Nessun file XML nella cartella note. Usa --notes per il vecchio file unito, se disponibile.")
    for xml in files:
        note_id = xml.stem
        if note_id in documents:
            diagnostics.duplicate_note_ids.append(note_id)
            continue
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError as error:
            diagnostics.xml_errors.append(f"{note_id}: {error}")
            continue
        documents[note_id] = [Call(note_id, ordinal, clean(elem.get("number")), clean(elem.get("data")), normalized_operator(elem.get("operatore")), clean(elem.get("previousInterest")), clean(elem.get("newInterest")), clean(elem.get("checkpoint")), clean(elem.get("durata")), clean(elem.get("messaggio")), "".join(elem.itertext()).strip()) for ordinal, elem in enumerate(root.findall("chiamata"))]
    return documents


def require_length(value: str | None, limit: int, label: str) -> None:
    if value and len(value.encode("utf-16-le")) // 2 > limit:
        raise ValueError(f"{label}: superati {limit} caratteri. Nessun valore viene troncato automaticamente.")


def validate_clients(clients: list[Client]) -> None:
    if not clients:
        raise ValueError("Il backup non contiene anagrafiche.")
    seen = set()
    limits = {"ragione_sociale":255,"tipo_cliente":40,"partita_iva":32,"codice_fiscale":32,"persona_riferimento":255,"titolare":255,"paese":80,"regione":80,"provincia":75,"citta":120,"indirizzo":255,"numero_civico":30,"cap":20,"telefono":255,"telefono2":255,"cellulare":255,"email_referente":255,"email_generica":255,"email_certificata":255,"sito_web":255}
    for client in clients:
        if client.id in seen:
            raise ValueError(f"Identificativo cliente duplicato alla riga {client.rownum}: note_id ripetuto. Correggere il backup.")
        seen.add(client.id)
        if not client.raw.get("ragione_sociale"):
            raise ValueError(f"Ragione sociale vuota alla riga {client.rownum}.")
        for key, limit in limits.items():
            require_length(client.raw.get(key),limit,f"Riga {client.rownum}, {key}")
        require_length(final_interest(client),40,f"Riga {client.rownum}, stato trattativa")


def sql_clob(value: str) -> str:
    chunks = ["'" + value[i:i+8000].replace("'", "''") + "'" for i in range(0,len(value),8000)]
    return "CAST(" + (chunks[0] if chunks else "''") + " AS CLOB)" + "".join(" || " + chunk for chunk in chunks[1:])


def sql_timestamp(value: str | None, ordinal: int) -> str:
    parsed = valid_iso_date(value)
    if ordinal >= 86400:
        raise ValueError("Troppe chiamate per un singolo cliente.")
    if not parsed:
        # Contact date remains NULL. Creation time records the import, keeping the
        # source order so the calendar can identify the last undated interaction.
        return f"{{fn TIMESTAMPADD(SQL_TSI_SECOND, {ordinal}, CURRENT_TIMESTAMP)}}"
    return f"TIMESTAMP('{parsed} {ordinal//3600:02d}:{ordinal//60%60:02d}:{ordinal%60:02d}')"


def call_outcome(value: str | None) -> str | None:
    code = (value or "").strip().upper().replace(" ", "_")
    return code if code in {"NON_TROVATO","NON_INERENTE","PRESENTAZIONE","LISTINO","CAMPIONE","CLIENTE"} else None


def property_value(value: str) -> str:
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")


def generate(clients_file: Path, notes_file: Path, out_dir: Path, *, allow_anomalies: bool = False) -> dict[str, int]:
    # Remove the old executable manifest first: failed preparation must never leave an earlier import runnable.
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "migration.properties").unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(prefix="crm2-prepare-", dir=out_dir.parent) as temporary:
        staging = Path(temporary)
        try:
            counts = generate_files(clients_file,notes_file,staging,allow_anomalies=allow_anomalies)
        except Exception as error:
            for name in ("import_report.txt", "migration.properties"):
                if (staging/name).exists():
                    shutil.copy2(staging/name,out_dir/name)
            if type(error) is ValueError:
                raise ValueError(str(error).replace(str(staging), str(out_dir))) from error
            raise
        for file in staging.iterdir():
            shutil.copy2(file,out_dir/file.name)
        return counts


def write_sql(out_dir: Path, name: str, statements: Iterable[str]) -> int:
    rows = list(statements)
    header = "-- Auto-generato da scripts/generate_import_sql.py; eseguire nell'ordine indicato nel report.\n"
    (out_dir / name).write_text(header + "\n".join(rows) + ("\n" if rows else ""), encoding="utf-8", newline="\n")
    return len(rows)


def generate_files(clients_file: Path, notes_file: Path, out_dir: Path, *, allow_anomalies: bool = False) -> dict[str, int]:
    diagnostics = Diagnostics()
    clients = parse_clients(clients_file, diagnostics)
    notes = parse_notes(notes_file, diagnostics)
    for client in clients:
        client.calls = notes.get(client.raw.get("note_id") or "", [])
    out_dir.mkdir(parents=True, exist_ok=True)
    validate_clients(clients)
    diagnostics.unmatched_note_ids = sorted(set(notes) - {c.raw.get("note_id") for c in clients})
    diagnostics.missing_note_ids = sorted({c.raw["note_id"] for c in clients if c.raw.get("note_id") and c.raw["note_id"] not in notes and (c.raw.get("ultima_chiamata") or c.raw.get("volte_contattati") not in (None,"0"))})

    operators = sorted({operator or UNKNOWN_OPERATOR for client in clients for operator in
                        ([normalized_operator(client.raw.get("operatore"))] + [call.operatore for call in client.calls])})
    for operator in operators:
        require_length(operator, 80, "Nome operatore")
    counts: dict[str, int] = {}
    counts["import_operatori.sql"] = write_sql(out_dir, "import_operatori.sql", (
        f"INSERT INTO ECOS.OPERATORI (ID, USERNAME, ATTIVO) SELECT '{uid('operatore', op)}', {sql(op)}, 1 "
        f"FROM SYSIBM.SYSDUMMY1 WHERE NOT EXISTS (SELECT 1 FROM ECOS.OPERATORI WHERE UPPER(TRIM(USERNAME)) = {sql(op)});"
        for op in operators
    ))
    catalogue_rows = []
    for table, kind, values in (
        ("TIPI_CLIENTE", "tipo", {c.raw.get("tipo_cliente") for c in clients}),
        ("STATI_TRATTATIVA", "stato", {final_interest(c) for c in clients}),
    ):
        for value in sorted(v for v in values if v):
            catalogue_rows.append(f"INSERT INTO CRM.{table} (ID,NOME) SELECT '{uid(kind, value)}', {sql(value)} FROM SYSIBM.SYSDUMMY1 WHERE NOT EXISTS (SELECT 1 FROM CRM.{table} WHERE NOME={sql(value)});")
    counts["import_cataloghi.sql"] = write_sql(out_dir, "import_cataloghi.sql", catalogue_rows)
    counts["import_clienti.sql"] = write_sql(out_dir, "import_clienti.sql", (
        "INSERT INTO CRM.CLIENTI (ID, RAGIONE_SOCIALE, TIPO_CLIENTE, STATO_TRATTATIVA, COINVOLGIMENTO, "
        "PARTITA_IVA, CODICE_FISCALE, ACQUISIZIONE, OPERATORE_ID) VALUES "
        f"('{c.id}', {sql(c.raw.get('ragione_sociale'))}, {sql(c.raw.get('tipo_cliente'))}, {sql(final_interest(c))}, "
        f"{parse_involvement(c.raw.get('coinvolgimento')) or 'NULL'}, {sql(c.raw.get('partita_iva'))}, "
        f"{sql(c.raw.get('codice_fiscale'))}, {sql_date(c.raw.get('acquisizione'), diagnostics)}, "
        f"{operator_expression(c.raw.get('operatore'))});" for c in clients
    ))
    counts["import_contatti.sql"] = write_sql(out_dir, "import_contatti.sql", (
        f"INSERT INTO CRM.CONTATTI_CLIENTE (ID, CLIENTE_ID, DESCRIZIONE) VALUES "
        f"('{uid('contatto', c.id + ':' + label)}', '{c.id}', {sql(value)});"
        for c in clients for label, value in (("persona", c.raw.get("persona_riferimento")), ("titolare", c.raw.get("titolare"))) if value
    ))
    counts["import_indirizzi.sql"] = write_sql(out_dir, "import_indirizzi.sql", (
        "INSERT INTO CRM.INDIRIZZI_CLIENTE (ID, CLIENTE_ID, PAESE, REGIONE, PROVINCIA, CITTA, INDIRIZZO, NUMERO_CIVICO, CAP, PRINCIPALE) VALUES "
        f"('{uid('indirizzo', c.id)}', '{c.id}', {sql(c.raw.get('paese'))}, {sql(c.raw.get('regione'))}, "
        f"{sql(c.raw.get('provincia'))}, {sql(c.raw.get('citta'))}, {sql(c.raw.get('indirizzo'))}, "
        f"{sql(c.raw.get('numero_civico'))}, {sql(c.raw.get('cap'))}, 1);"
        for c in clients if any(c.raw.get(key) for key in ("paese", "regione", "provincia", "citta", "indirizzo", "numero_civico", "cap"))
    ))
    counts["import_telefoni.sql"] = write_sql(out_dir, "import_telefoni.sql", (
        f"INSERT INTO CRM.TELEFONI_CLIENTE (ID, CLIENTE_ID, DESCRIZIONE) VALUES ('{uid('telefono', c.id + ':' + label)}', '{c.id}', {sql(value)});"
        for c in clients for label, value in (("telefono", c.raw.get("telefono")), ("telefono2", c.raw.get("telefono2")), ("cellulare", c.raw.get("cellulare"))) if value
    ))
    counts["import_email.sql"] = write_sql(out_dir, "import_email.sql", (
        f"INSERT INTO CRM.EMAIL_CLIENTE (ID, CLIENTE_ID, CONTATTO_ID, DESCRIZIONE) VALUES ('{uid('email', c.id + ':' + label)}', '{c.id}', {sql(uid('contatto', c.id + ':persona')) if label == 'referente' and c.raw.get('persona_riferimento') else 'NULL'}, {sql(value)});"
        for c in clients for label, value in (("referente", c.raw.get("email_referente")), ("generica", c.raw.get("email_generica")), ("pec", c.raw.get("email_certificata"))) if value
    ))
    counts["import_siti.sql"] = write_sql(out_dir, "import_siti.sql", (
        f"INSERT INTO CRM.SITI_WEB_CLIENTE (ID, CLIENTE_ID, DESCRIZIONE) VALUES ('{uid('sito', c.id)}', '{c.id}', {sql(c.raw.get('sito_web'))});"
        for c in clients if c.raw.get("sito_web")
    ))

    note_rows: list[str] = []
    for client in clients:
        calls = sorted(client.calls, key=chronological_key)
        valid_calls = [call for call in calls if valid_iso_date(call.data)]
        latest = max(valid_calls, key=chronological_key) if valid_calls else (calls[-1] if calls else None)
        for index, call in enumerate(calls):
            base = f"{client.id}:{call.note_id}:{call.ordinal}"
            note_id, interaction_id = uid("nota", base), uid("interazione", base)
            next_contact = client.raw.get("prossima_chiamata") if call is latest else None
            note_rows.append(
                f"INSERT INTO CRM.NOTE_CLIENTE (ID, CLIENTE_ID, OPERATORE_ID, TESTO, CREATED_AT) VALUES "
                f"('{note_id}', '{client.id}', {operator_expression(call.operatore)}, {sql_clob(note_text(call))}, {sql_timestamp(call.data, index)});")
            note_rows.append(
                "INSERT INTO CRM.INTERAZIONI (ID, CLIENTE_ID, OPERATORE_ID, NOTA_ID, DATA_CONTATTO, PROSSIMO_CONTATTO, ESITO, CREATED_AT) VALUES "
                f"('{interaction_id}', '{client.id}', {operator_expression(call.operatore)}, '{note_id}', "
                f"{sql_date(call.data, diagnostics)}, {sql_date(next_contact, diagnostics)}, {sql(call_outcome(call.new_interest))}, {sql_timestamp(call.data, index)});")
        if not calls and (client.raw.get("ultima_chiamata") or client.raw.get("prossima_chiamata")):
            interaction_id = uid("interazione-sintetica", client.id)
            note_rows.append(
                "INSERT INTO CRM.INTERAZIONI (ID, CLIENTE_ID, OPERATORE_ID, NOTA_ID, DATA_CONTATTO, PROSSIMO_CONTATTO) VALUES "
                f"('{interaction_id}', '{client.id}', {operator_expression(client.raw.get('operatore'))}, NULL, "
                f"{sql_date(client.raw.get('ultima_chiamata'), diagnostics)}, {sql_date(client.raw.get('prossima_chiamata'), diagnostics)});")
        extra = {key: client.raw.get(key) for key in ("note_id", "volte_contattati", "checkpoint") if client.raw.get(key)}
        for key in ("acquisizione", "ultima_chiamata", "prossima_chiamata"):
            if client.raw.get(key) and not valid_iso_date(client.raw[key]):
                extra[key + " (valore originale non valido)"] = client.raw[key]
        if extra:
            text = "Riepilogo originale CRM2\n" + "\n".join(f"{key}: {value}" for key, value in extra.items())
            note_rows.append(f"INSERT INTO CRM.NOTE_CLIENTE (ID,CLIENTE_ID,OPERATORE_ID,TESTO) VALUES ('{uid('riepilogo-crm2',client.id)}','{client.id}',{operator_expression(client.raw.get('operatore'))},{sql_clob(text)});")
    counts["import_note_interazioni.sql"] = write_sql(out_dir, "import_note_interazioni.sql", note_rows)

    linked_ids = {c.raw.get("note_id") for c in clients if c.raw.get("note_id") in notes}
    call_count = sum(len(c.calls) for c in clients)
    report = [
        "IMPORT LEGACY CRM - REPORT\n",
        f"Clienti letti: {len(clients)}\n", f"Documenti XML letti: {len(notes)}\n",
        f"Documenti XML collegati: {len(linked_ids)}\n", f"Documenti XML non collegati: {len(set(notes) - linked_ids)}\n",
        f"Chiamate XML importate: {call_count}\n", f"Operatori distinti: {len(operators)}\n",
        "Righe clienti malformate: 0\n",
        f"Coinvolgimenti non validi: {len(diagnostics.invalid_involvement_rows)}\n",
        f"Date non valide: {len(diagnostics.invalid_dates)}",
        (f" ({', '.join(sorted(diagnostics.invalid_dates))})\n" if diagnostics.invalid_dates else "\n"),
        f"XML non validi: {len(diagnostics.xml_errors)}\n", f"NoteId duplicati: {len(diagnostics.duplicate_note_ids)}\n",
        "\nOrdine di esecuzione e statement:\n",
    ]
    report.extend(f"{index}. {name}: {counts[name]}\n" for index, name in enumerate(counts, 1))
    if diagnostics.duplicate_note_ids:
        report.append("\nNote XML duplicate: " + ", ".join(diagnostics.duplicate_note_ids) + "\n")
    if diagnostics.unmatched_note_ids:
        report.append("\nXML senza anagrafica: " + ", ".join(diagnostics.unmatched_note_ids) + "\n")
    if diagnostics.missing_note_ids:
        report.append("\nStorici XML mancanti per clienti gia contattati: " + ", ".join(diagnostics.missing_note_ids) + "\n")
    if diagnostics.invalid_involvement_rows:
        report.append("\nRighe con coinvolgimento non valido: " + ", ".join(map(str, diagnostics.invalid_involvement_rows)) + "\n")
    if diagnostics.xml_errors:
        report.append("\nErrori XML:\n" + "\n".join(diagnostics.xml_errors) + "\n")
    (out_dir / "import_report.txt").write_text("".join(report), encoding="utf-8", newline="\n")
    anomalies = bool(diagnostics.xml_errors or diagnostics.duplicate_note_ids or diagnostics.unmatched_note_ids or diagnostics.missing_note_ids or diagnostics.invalid_dates or diagnostics.invalid_involvement_rows)
    properties = {"format": "1", "source": "CRM2", "target": "OrodentEcoS", "schema.minimum": "13", "clients": str(len(clients)), "blocked": str(anomalies and not allow_anomalies).lower(), "anomalies.accepted": str(anomalies and allow_anomalies).lower(), "files.count": str(len(counts)), "operators.count": str(len(operators))}
    for index, (name, count) in enumerate(counts.items(), 1):
        properties.update({f"file.{index}.name": name, f"file.{index}.statements": str(count), f"file.{index}.sha256": hashlib.sha256((out_dir/name).read_bytes()).hexdigest()})
    for index, operator in enumerate(operators, 1):
        properties[f"operator.{index}.username"] = operator
    (out_dir / "migration.properties").write_text("\n".join(f"{key}={property_value(value)}" for key, value in properties.items()) + "\n", encoding="utf-8", newline="\n")
    if anomalies and not allow_anomalies:
        raise ValueError(f"Anomalie da correggere: consulta {out_dir / 'import_report.txt'}. Importazione bloccata; nessun database modificato.")
    return counts


def display_path(path: Path) -> str:
    """Mostra i file del progetto con un percorso breve e comprensibile."""
    if path == DEFAULT_CLIENTS_FILE or path == DEFAULT_NOTES_FILE:
        return f"input/{path.name}"
    try:
        return path.resolve().relative_to(ROOT.resolve()).as_posix()
    except ValueError:
        return str(path)


def require_input_files(paths: Iterable[Path]) -> None:
    missing = [path for path in paths if not path.is_file()]
    if not missing:
        return
    print("ERRORE: file di input non trovati:", file=sys.stderr)
    for path in missing:
        print(f"- {display_path(path)}", file=sys.stderr)
    raise SystemExit(2)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--clients", type=Path, default=DEFAULT_CLIENTS_FILE)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--notes", type=Path, help="File note unito con marcatori FILE / END FILE")
    source.add_argument("--notes-dir", type=Path, help="Cartella originale contenente i file XML")
    parser.add_argument("--output", type=Path, default=SCRIPT_DIR / "output")
    parser.add_argument("--allow-anomalies", action="store_true", help="Accetta esplicitamente le anomalie riportate (date ignote / XML scartati)")
    args = parser.parse_args()
    # Even a missing input must invalidate a previously prepared import.
    (args.output / "migration.properties").unlink(missing_ok=True)
    notes = args.notes or args.notes_dir or DEFAULT_INPUT_DIR / "note"
    require_input_files((args.clients,))
    if not notes.exists():
        parser.error(f"Origine note non trovata: {notes}")
    try:
        counts = generate(args.clients, notes, args.output, allow_anomalies=args.allow_anomalies)
        print(f"Preparazione completata: {sum(counts.values())} istruzioni. Report: {args.output / 'import_report.txt'}")
    except ClientFormatError as error:
        print_client_format_error(error)
        raise SystemExit(2) from error
    except (ValueError, OSError) as error:
        print(f"ERRORE: {error}", file=sys.stderr)
        raise SystemExit(2) from error


if __name__ == "__main__":
    main()
