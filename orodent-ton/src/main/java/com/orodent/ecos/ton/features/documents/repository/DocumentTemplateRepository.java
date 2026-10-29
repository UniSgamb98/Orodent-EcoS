package com.orodent.ecos.ton.features.documents.repository;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;

import java.io.StringReader;
import java.sql.*;
import java.util.*;

/** SQL only, always using the transaction supplied by the service. No automatic write replay. */
public final class DocumentTemplateRepository {
    private static final String COLUMNS =
            "modello_documento_id,codice,numero_versione,nome,stato,versione_modifica,aggiornato_il";
    private final Connection c;

    public DocumentTemplateRepository(Connection c) {
        this.c = Objects.requireNonNull(c);
    }

    public Page page(String search, int offset, int limit) throws SQLException {
        String pattern =
                "%"
                        + search.toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        String sql =
                "SELECT "
                        + COLUMNS
                        + " FROM TON.MODELLI_DOCUMENTO m WHERE tipo='PRODUZIONE' AND"
                        + " numero_versione=(SELECT MAX(x.numero_versione) FROM"
                        + " TON.MODELLI_DOCUMENTO x WHERE x.codice=m.codice) AND (UPPER(codice)"
                        + " LIKE ? ESCAPE '\\' OR UPPER(nome) LIKE ? ESCAPE '\\') ORDER BY codice"
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        var rows = new ArrayList<Template>();
        try (var q = c.prepareStatement(sql)) {
            q.setString(1, pattern);
            q.setString(2, pattern);
            q.setInt(3, offset);
            q.setInt(4, limit + 1);
            try (var r = q.executeQuery()) {
                while (r.next()) rows.add(row(r));
            }
        }
        boolean more = rows.size() > limit;
        return new Page(more ? rows.subList(0, limit) : rows, offset, more);
    }

    public List<Template> versions(String code) throws SQLException {
        var rows = new ArrayList<Template>();
        try (var q =
                c.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + " FROM TON.MODELLI_DOCUMENTO WHERE codice=? AND tipo='PRODUZIONE'"
                                + " ORDER BY numero_versione DESC")) {
            q.setString(1, code);
            try (var r = q.executeQuery()) {
                while (r.next()) rows.add(row(r));
            }
        }
        return List.copyOf(rows);
    }

    public Detail load(long id) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT "
                                + COLUMNS
                                + ",contenuto_modello FROM TON.MODELLI_DOCUMENTO WHERE"
                                + " modello_documento_id=? AND tipo='PRODUZIONE'")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException("Modello non trovato. Ricarica l’elenco.");
                Clob content = r.getClob(8);
                try {
                    if (content.length() > MAX_CONTENT)
                        throw new IllegalArgumentException("Il modello supera il limite di 8 MB.");
                    return new Detail(row(r), content.getSubString(1, (int) content.length()));
                } finally {
                    content.free();
                }
            }
        }
    }

    public Detail insert(Draft draft, int number) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello)"
                            + " VALUES (?,?,?,'PRODUZIONE',?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, draft.code());
            q.setInt(2, number);
            q.setString(3, draft.name());
            q.setCharacterStream(4, new StringReader(draft.content()), draft.content().length());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                return load(keys.getLong(1));
            }
        }
    }

    public Detail save(Template expected, Draft draft, boolean approve) throws SQLException {
        String sql =
                "UPDATE TON.MODELLI_DOCUMENTO SET nome=?,contenuto_modello=?,stato=?,approvato_il="
                        + (approve ? "CURRENT_TIMESTAMP" : "NULL")
                        + ",versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                        + " WHERE modello_documento_id=? AND versione_modifica=? AND stato='BOZZA'"
                        + " AND codice=? AND tipo='PRODUZIONE'";
        try (var q = c.prepareStatement(sql)) {
            q.setString(1, draft.name());
            q.setCharacterStream(2, new StringReader(draft.content()), draft.content().length());
            q.setString(3, approve ? "APPROVATA" : "BOZZA");
            q.setLong(4, expected.id());
            q.setInt(5, expected.token());
            q.setString(6, draft.code());
            changed(q.executeUpdate());
        }
        return load(expected.id());
    }

    /** The first revision is the stable serialization point for all copies of a code. */
    public void lockFamily(String code) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.MODELLI_DOCUMENTO SET versione_modifica=versione_modifica WHERE"
                            + " codice=? AND numero_versione=1 AND tipo='PRODUZIONE'")) {
            q.setString(1, code);
            changed(q.executeUpdate());
        }
    }

    public void deleteDraft(Template expected) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "DELETE FROM TON.MODELLI_DOCUMENTO WHERE modello_documento_id=? AND"
                            + " versione_modifica=? AND codice=? AND stato='BOZZA' AND"
                            + " tipo='PRODUZIONE'")) {
            q.setLong(1, expected.id());
            q.setInt(2, expected.token());
            q.setString(3, expected.code());
            changed(q.executeUpdate());
        }
    }

    private static void changed(int count) {
        if (count != 1)
            throw new IllegalStateException(
                    "Il modello è stato modificato da un’altra postazione. Ricaricalo prima di"
                        + " salvare; le modifiche locali sono ancora nell’editor.");
    }

    private static Template row(ResultSet r) throws SQLException {
        return new Template(
                r.getLong(1),
                r.getString(2),
                r.getInt(3),
                r.getString(4),
                State.valueOf(r.getString(5)),
                r.getInt(6),
                r.getTimestamp(7).toLocalDateTime());
    }
}
