package com.orodent.ecos.ton.features.catalog.repository;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import java.sql.*;
import java.util.*;

/** Complete typed snapshots, written atomically with the corresponding catalogue update. */
public final class CatalogHistoryRepository {
    private final Connection connection;

    public CatalogHistoryRepository(Connection connection) {
        this.connection = connection;
    }

    public void record(Entry entry, String source) throws SQLException {
        String projection =
                switch (entry.kind()) {
                    case LINES ->
                            "b.linea_id,b.versione_modifica,b.codice,b.nome,b.tecnologia,b.materia_prima,b.note,b.attivo";
                    case PRODUCTS ->
                            "b.prodotto_id,b.versione_modifica,b.codice,b.descrizione,b.linea_id,l.codice,b.colore_commerciale,b.note,b.attivo";
                    case ARTICLES ->
                            "b.articolo_id,b.versione_modifica,b.codice,b.descrizione,b.prodotto_id,p.codice,p.linea_id,l.codice,p.colore_commerciale,b.diametro_mm,b.altezza_mm,b.configurazione_predefinita_id,b.note,b.attivo";
                };
        String from =
                switch (entry.kind()) {
                    case LINES -> " FROM TON.LINEE b";
                    case PRODUCTS ->
                            " FROM TON.PRODOTTI b LEFT JOIN TON.LINEE l ON l.linea_id=b.linea_id";
                    case ARTICLES ->
                            " FROM TON.ARTICOLI b JOIN TON.PRODOTTI p ON"
                                + " p.prodotto_id=b.prodotto_id LEFT JOIN TON.LINEE l ON"
                                + " l.linea_id=p.linea_id";
                };
        try (var query =
                connection.prepareStatement(
                        "INSERT INTO "
                                + entry.kind().historyTable()
                                + "("
                                + columns(entry.kind())
                                + ",origine) SELECT "
                                + projection
                                + ",?"
                                + from
                                + " WHERE b."
                                + entry.kind().key()
                                + "=?")) {
            query.setString(1, source);
            query.setLong(2, entry.id());
            query.executeUpdate();
        }
    }

    public HistoryPage page(CatalogKind kind, long id, int offset, int limit) throws SQLException {
        long count =
                new CatalogRepository(connection)
                        .count(
                                "SELECT COUNT(*) FROM "
                                        + kind.historyTable()
                                        + " WHERE "
                                        + kind.key()
                                        + "=?",
                                List.of(id));
        var result = new ArrayList<Audit>();
        try (var query =
                connection.prepareStatement(
                        (kind == CatalogKind.ARTICLES
                                        ? "SELECT h.*,c.numero_versione AS preferred_number FROM "
                                                + kind.historyTable()
                                                + " h LEFT JOIN TON.CONFIGURAZIONI_ARTICOLO c ON"
                                                + " c.configurazione_id=h.configurazione_predefinita_id"
                                        : "SELECT h.* FROM " + kind.historyTable() + " h")
                                + " WHERE h."
                                + kind.key()
                                + "=? ORDER BY h.numero_revisione DESC OFFSET ? ROWS FETCH NEXT ?"
                                + " ROWS ONLY")) {
            query.setLong(1, id);
            query.setInt(2, offset);
            query.setInt(3, limit);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(map(kind, rows));
            }
        }
        return new HistoryPage(result, count, offset, limit);
    }

    public void verifyCurrent(Entry entry) throws SQLException {
        long count =
                new CatalogRepository(connection)
                        .count(
                                "SELECT COUNT(*) FROM "
                                        + entry.kind().historyTable()
                                        + " WHERE "
                                        + entry.kind().key()
                                        + "=? AND numero_revisione=?",
                                List.of(entry.id(), entry.revision()));
        if (count != 1)
            throw new SQLException(
                    "Lo storico dell'anagrafica non corrisponde alla revisione corrente. Verificare"
                        + " le scritture esterne all'applicazione.");
    }

    private static String columns(CatalogKind kind) {
        return switch (kind) {
            case LINES ->
                    "linea_id,numero_revisione,codice,nome,tecnologia,materia_prima,note,attivo";
            case PRODUCTS ->
                    "prodotto_id,numero_revisione,codice,descrizione,linea_id,linea_codice,colore_commerciale,note,attivo";
            case ARTICLES ->
                    "articolo_id,numero_revisione,codice,descrizione,prodotto_id,prodotto_codice,linea_id,linea_codice,colore_commerciale,diametro_mm,altezza_mm,configurazione_predefinita_id,note,attivo";
        };
    }

    private static Audit map(CatalogKind kind, ResultSet r) throws SQLException {
        int number = r.getInt("numero_revisione");
        var date = r.getTimestamp("registrato_il").toLocalDateTime();
        Metadata meta =
                new Metadata(
                        r.getLong(kind.key()),
                        r.getString("codice"),
                        r.getString(kind == CatalogKind.LINES ? "nome" : "descrizione"),
                        r.getString("note"),
                        r.getBoolean("attivo"),
                        number,
                        date,
                        date);
        Entry entry =
                switch (kind) {
                    case LINES ->
                            new Line(
                                    meta,
                                    r.getString("tecnologia"),
                                    r.getString("materia_prima"),
                                    0,
                                    false);
                    case PRODUCTS -> {
                        Long line = (Long) r.getObject("linea_id");
                        yield new Product(
                                meta,
                                line == null
                                        ? null
                                        : new Choice(
                                                line,
                                                r.getString("linea_codice"),
                                                "",
                                                true,
                                                line,
                                                r.getString("linea_codice"),
                                                null),
                                r.getString("colore_commerciale"),
                                0,
                                false);
                    }
                    case ARTICLES -> {
                        Long line = (Long) r.getObject("linea_id");
                        Choice product =
                                new Choice(
                                        r.getLong("prodotto_id"),
                                        r.getString("prodotto_codice"),
                                        "",
                                        true,
                                        line,
                                        r.getString("linea_codice"),
                                        r.getString("colore_commerciale"));
                        yield new Article(
                                meta,
                                product,
                                r.getBigDecimal("diametro_mm"),
                                r.getBigDecimal("altezza_mm"),
                                (Long) r.getObject("configurazione_predefinita_id"),
                                r.getObject("preferred_number") == null
                                        ? "Nessuna"
                                        : "v" + r.getInt("preferred_number"),
                                false);
                    }
                };
        return new Audit(entry, number, date, r.getString("origine"));
    }
}
