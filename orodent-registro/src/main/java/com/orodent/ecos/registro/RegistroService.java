package com.orodent.ecos.registro;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.audit.AuditValues;

import java.sql.*;
import java.time.*;
import java.util.*;

/** Read-only queries. Pages contain whole transactions, never fragments of the same save. */
final class RegistroService {
    static final int PAGE_SIZE = 100;

    record Filter(
            LocalDate from, LocalDate to, String actorId, String kind, String customer, int page) {
        Filter {
            if (from != null && to != null && from.isAfter(to))
                throw new IllegalArgumentException(
                        "La data iniziale deve precedere quella finale.");
            customer = customer == null ? "" : customer.strip();
            if (page < 0 || !Set.of("", "INSERT", "UPDATE", "DELETE").contains(kind))
                throw new IllegalArgumentException("Filtro registro non valido.");
        }
    }

    record Actor(String id, String name) {
        public String toString() {
            return name;
        }
    }

    record Event(
            String id,
            LocalDateTime time,
            String actorId,
            String actor,
            String station,
            String source,
            String databaseUser,
            String customerId,
            String customer,
            int customers,
            int created,
            int modified,
            int deleted,
            String subject) {
        int changes() {
            return created + modified + deleted;
        }

        String description() {
            var counts = new ArrayList<String>();
            if (created > 0) counts.add(created + (created == 1 ? " creazione" : " creazioni"));
            if (modified > 0) counts.add(modified + (modified == 1 ? " modifica" : " modifiche"));
            if (deleted > 0)
                counts.add(deleted + (deleted == 1 ? " eliminazione" : " eliminazioni"));
            return subject + " · " + String.join(" · ", counts);
        }
    }

    record Totals(int events, int created, int modified, int deleted) {}

    record Page(List<Event> events, Totals totals) {}

    record Change(
            String table,
            String key,
            String customerId,
            String customer,
            String kind,
            Map<String, String> before,
            Map<String, String> after) {}

    private final ConnectionProvider connections;

    RegistroService(ConnectionProvider connections) {
        this.connections = connections;
    }

    List<Actor> actors() {
        return connections.withConnection(
                c -> {
                    var values = new ArrayList<Actor>();
                    values.add(new Actor("", "Tutti gli operatori"));
                    values.add(new Actor("unknown", "Operatore non indicato"));
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT"
                                                + " A.ACTOR_ID,COALESCE(O.USERNAME,MAX(A.ACTOR_NAME),A.ACTOR_ID)"
                                                + " FROM AUDIT.OPERATIONS A LEFT JOIN"
                                                + " ECOS.OPERATORI O ON O.ID=A.ACTOR_ID WHERE"
                                                + " A.ACTOR_ID IS NOT NULL GROUP BY"
                                                + " A.ACTOR_ID,O.USERNAME ORDER BY 2")) {
                        while (r.next()) values.add(new Actor(r.getString(1), r.getString(2)));
                    }
                    return List.copyOf(values);
                });
    }

    Page load(Filter filter) {
        return connections.withTransaction(
                c -> {
                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    var args = new ArrayList<Object>();
                    String where = where(filter, args);
                    String sums =
                            "SUM(CASE WHEN C.CHANGE_KIND='INSERT' THEN 1 ELSE 0 END),SUM(CASE WHEN"
                                    + " C.CHANGE_KIND='UPDATE' THEN 1 ELSE 0 END),SUM(CASE WHEN"
                                    + " C.CHANGE_KIND='DELETE' THEN 1 ELSE 0 END)";
                    String source =
                            " FROM AUDIT.OPERATIONS O JOIN AUDIT.CHANGES C ON"
                                    + " C.OPERATION_ID=O.OPERATION_ID "
                                    + where;
                    Totals totals;
                    try (var q =
                            c.prepareStatement(
                                    "SELECT COUNT(DISTINCT O.OPERATION_ID)," + sums + source)) {
                        bind(q, args);
                        try (var r = q.executeQuery()) {
                            r.next();
                            totals = new Totals(r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4));
                        }
                    }
                    var events = new ArrayList<Event>();
                    String fields =
                            "O.OPERATION_ID,O.OCCURRED_AT,O.ACTOR_ID,O.ACTOR_NAME,O.STATION,O.SOURCE,O.DATABASE_USER";
                    try (var q =
                            c.prepareStatement(
                                    "SELECT "
                                            + fields
                                            + ",MAX(C.CUSTOMER_ID),MAX(C.CUSTOMER_NAME),COUNT(DISTINCT"
                                            + " C.CUSTOMER_ID),"
                                            + sums
                                            + ",MIN(C.TABLE_NAME),MAX(C.TABLE_NAME)"
                                            + source
                                            + " GROUP BY "
                                            + fields
                                            + " ORDER BY O.OCCURRED_AT DESC,O.OPERATION_ID DESC"
                                            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
                        bind(q, args);
                        q.setInt(args.size() + 1, Math.multiplyExact(filter.page(), PAGE_SIZE));
                        q.setInt(args.size() + 2, PAGE_SIZE);
                        try (var r = q.executeQuery()) {
                            while (r.next())
                                events.add(
                                        new Event(
                                                r.getString(1),
                                                r.getTimestamp(2).toLocalDateTime(),
                                                r.getString(3),
                                                Objects.toString(
                                                        r.getString(4), "Operatore non indicato"),
                                                r.getString(5),
                                                r.getString(6),
                                                r.getString(7),
                                                r.getString(8),
                                                r.getString(9),
                                                r.getInt(10),
                                                r.getInt(11),
                                                r.getInt(12),
                                                r.getInt(13),
                                                r.getString(14).equals(r.getString(15))
                                                        ? tableName(r.getString(14))
                                                        : "Più elementi"));
                        }
                    }
                    return new Page(List.copyOf(events), totals);
                });
    }

    boolean customerExists(String id) {
        return connections.withConnection(
                c -> {
                    try (var q = c.prepareStatement("SELECT 1 FROM CRM.CLIENTI WHERE ID=?")) {
                        q.setString(1, id);
                        try (var r = q.executeQuery()) {
                            return r.next();
                        }
                    }
                });
    }

    List<Change> details(String operationId) {
        return details(operationId, 0);
    }

    List<Change> details(String operationId, int page) {
        if (page < 0) throw new IllegalArgumentException("Pagina non valida");
        return connections.withConnection(
                c -> {
                    var changes = new ArrayList<Change>();
                    try (var q =
                            c.prepareStatement(
                                    "SELECT"
                                        + " TABLE_NAME,ROW_KEY,CUSTOMER_ID,CUSTOMER_NAME,CHANGE_KIND,BEFORE_VALUE,AFTER_VALUE"
                                        + " FROM AUDIT.CHANGES WHERE OPERATION_ID=? ORDER BY"
                                        + " CHANGE_ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
                        q.setString(1, operationId);
                        q.setInt(2, Math.multiplyExact(page, PAGE_SIZE));
                        q.setInt(3, PAGE_SIZE);
                        try (var r = q.executeQuery()) {
                            while (r.next())
                                changes.add(
                                        new Change(
                                                r.getString(1),
                                                r.getString(2),
                                                r.getString(3),
                                                r.getString(4),
                                                r.getString(5),
                                                AuditValues.decode(AuditValues.text(r.getClob(6))),
                                                AuditValues.decode(
                                                        AuditValues.text(r.getClob(7)))));
                        }
                    }
                    return List.copyOf(changes);
                });
    }

    private static String where(Filter f, List<Object> args) {
        var terms = new ArrayList<String>();
        if (f.from() != null) {
            terms.add("O.OCCURRED_AT>=?");
            args.add(Timestamp.valueOf(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            terms.add("O.OCCURRED_AT<?");
            args.add(Timestamp.valueOf(f.to().plusDays(1).atStartOfDay()));
        }
        if ("unknown".equals(f.actorId())) terms.add("O.ACTOR_ID IS NULL");
        else if (f.actorId() != null && !f.actorId().isBlank()) {
            terms.add("O.ACTOR_ID=?");
            args.add(f.actorId());
        }
        var changes = new ArrayList<String>();
        if (!f.kind().isEmpty()) {
            changes.add("F.CHANGE_KIND=?");
            args.add(f.kind());
        }
        if (!f.customer().isEmpty()) {
            changes.add("(LOWER(F.CUSTOMER_NAME) LIKE ? ESCAPE '!' OR F.CUSTOMER_ID=?)");
            args.add(
                    "%"
                            + f.customer()
                                    .toLowerCase(Locale.ROOT)
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
            args.add(f.customer());
        }
        if (!changes.isEmpty())
            terms.add(
                    "EXISTS (SELECT 1 FROM AUDIT.CHANGES F WHERE F.OPERATION_ID=O.OPERATION_ID AND "
                            + String.join(" AND ", changes)
                            + ")");
        return terms.isEmpty() ? "" : "WHERE " + String.join(" AND ", terms);
    }

    private static void bind(PreparedStatement q, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) q.setObject(i + 1, args.get(i));
    }

    static String tableName(String table) {
        return switch (table) {
            case "CRM.CLIENTI" -> "Anagrafica cliente";
            case "CRM.NOTE_CLIENTE" -> "Nota";
            case "CRM.INTERAZIONI" -> "Chiamata";
            case "ECOS.OPERATORI" -> "Operatore";
            case "CRM.CLIENTI_FORNI" -> "Forno del cliente";
            case "CRM.CLIENTI_FRESATORI" -> "Fresatore del cliente";
            case "CRM.CLIENTI_MATERIALI" -> "Materiale del cliente";
            case "CRM.CLIENTI_PREFERITI" -> "Preferito";
            case "CRM.CONTATTI_CLIENTE" -> "Contatto";
            case "CRM.INDIRIZZI_CLIENTE" -> "Indirizzo";
            case "CRM.TELEFONI_CLIENTE" -> "Telefono";
            case "CRM.EMAIL_CLIENTE" -> "Email";
            case "CRM.SITI_WEB_CLIENTE" -> "Sito web";
            case "CRM.VISTE_SALVATE" -> "Ricerca salvata";
            case "CRM.FORNI" -> "Catalogo forni";
            case "CRM.FRESATORI" -> "Catalogo fresatori";
            case "CRM.MATERIALI_DI_CONSUMO" -> "Catalogo materiali";
            case "CRM.CANALI_DI_ACQUISTO" -> "Canale di acquisto";
            case "CRM.TIPI_CLIENTE" -> "Tipo cliente";
            case "CRM.STATI_TRATTATIVA" -> "Stato trattativa";
            default -> table;
        };
    }

    static String fieldName(String key) {
        return switch (key) {
            case "CREATED_AT" -> "Creato il";
            case "UPDATED_AT" -> "Modificato il";
            case "VERSIONE_MODIFICA" -> "Revisione";
            case "DATA_CONTATTO" -> "Data contatto";
            case "PROSSIMO_CONTATTO" -> "Prossimo richiamo";
            case "ID" -> "Identificativo";
            default ->
                    key.substring(0, 1)
                            + key.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    static String kindName(String kind) {
        return switch (kind) {
            case "INSERT" -> "Creazione";
            case "DELETE" -> "Eliminazione";
            case "UPDATE" -> "Modifica";
            default -> "Tutte le azioni";
        };
    }
}
