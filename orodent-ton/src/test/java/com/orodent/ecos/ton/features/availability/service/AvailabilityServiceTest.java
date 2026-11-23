package com.orodent.ecos.ton.features.availability.service;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.ton.features.availability.model.Availability.Conflict;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Article;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;

import org.junit.jupiter.api.*;

import java.io.StringReader;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletionException;

class AvailabilityServiceTest {
    String url;
    ConnectionProvider db;
    AvailabilityService service;
    CatalogService catalog;
    Line line;
    Product product;
    Article article;

    @BeforeEach
    void open() {
        url = "jdbc:derby:memory:availability_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        service =
                new AvailabilityService(
                        db,
                        Runnable::run,
                        Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), ZoneOffset.UTC));
        catalog = new CatalogService(db, Runnable::run);
        line = (Line) catalog.create(new LineDraft("MST", "Thor", "", "", "", true)).join().entry();
        product =
                (Product)
                        catalog.create(ProductDraft.configured(line.id(), "A1", "", true))
                                .join()
                                .entry();
        article =
                (Article)
                        catalog.create(
                                        new ArticleDraft(
                                                "ZR9812MSTA1",
                                                "One",
                                                product.id(),
                                                new BigDecimal("98"),
                                                new BigDecimal("12"),
                                                "",
                                                true))
                                .join()
                                .entry();
    }

    @AfterEach
    void close() throws SQLException {
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    Preview preview(Kind kind, String csv, boolean sum, boolean zero) {
        if (kind == Kind.DISPONIBILITA) {
            var lines = csv.lines().toList();
            var withWarehouse = new StringBuilder(lines.getFirst() + ";Deposito\n");
            lines.stream().skip(1).forEach(line -> withWarehouse.append(line).append(";MAGC\n"));
            csv = withWarehouse.toString();
        }
        var doc = new CsvReader().read(new StringReader(csv), CsvReadOptions.semicolonSeparated());
        return service.preview(
                        kind,
                        doc,
                        "test.csv",
                        LocalDate.of(2026, 11, 21),
                        "Codice",
                        "Quantità",
                        kind == Kind.DISPONIBILITA ? "Deposito" : null,
                        Numbers.ITALIAN,
                        sum,
                        zero)
                .join();
    }

    void apply(Kind k, String rows, boolean sum, boolean zero) {
        service.apply(preview(k, "Codice;Quantità\n" + rows, sum, zero)).join();
    }

    @Test
    void independentSourcesReplaceSnapshotsAndDistinguishMissingFromZero() {
        var initial = service.load(Period.DAYS_90).join();
        assertNull(initial.rows().getFirst().stock());
        assertNull(initial.total(Row::orders));
        assertNull(initial.total(Row::sold));
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;20\n", false, false);
        apply(Kind.ORDINI, "ZR9812MSTA1;7\nZR9812MSTA1;3\n", true, false);
        var loaded = service.load(Period.DAYS_90).join();
        assertEquals(0, new BigDecimal("20").compareTo(loaded.total(Row::stock)));
        assertEquals(0, new BigDecimal("10").compareTo(loaded.total(Row::orders)));
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;4\n", false, false);
        loaded = service.load(Period.DAYS_90).join();
        assertEquals(0, new BigDecimal("4").compareTo(loaded.total(Row::stock)));
        assertEquals(0, new BigDecimal("10").compareTo(loaded.total(Row::orders)));
        apply(Kind.DISPONIBILITA, "UNKNOWN;12\n", false, false);
        loaded = service.load(Period.DAYS_90).join();
        assertEquals(0, loaded.rows().getFirst().stock().signum());
        assertEquals(1, loaded.unmatchedStock());
        apply(Kind.DISPONIBILITA, "UNKNOWN;12\n", false, true);
        assertEquals(0, service.load(Period.DAYS_90).join().rows().getFirst().stock().signum());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT COUNT(*) FROM TON.DISPONIBILITA_IMPORT")) {
                        r.next();
                        assertEquals(5, r.getInt(1));
                    }
                    return null;
                });
    }

    @Test
    void realCsvKeepsWarehousesSeparateAndReplacesTheEntireStockSnapshot() {
        String header =
                "Articolo;Descrizione;Quantità Attuale;ImpDaCli;Qta Venduta;Valore"
                    + " Venduto;Deposito\n";
        var document =
                new CsvReader()
                        .read(
                                new StringReader(
                                        header
                                                + article.code()
                                                + ";Thor;18;-1;5;360;MAGC\n"
                                                + article.code()
                                                + ";Thor;7;-2;99;123,45;MAGMOD\n"),
                                CsvReadOptions.semicolonSeparated());
        var preview =
                service.preview(
                                Kind.DISPONIBILITA,
                                document,
                                "stock.csv",
                                LocalDate.now(),
                                "Articolo",
                                "Quantità Attuale",
                                "Deposito",
                                Numbers.ITALIAN,
                                false,
                                false)
                        .join();
        assertTrue(preview.errors().isEmpty());
        assertEquals(0, preview.duplicates());
        assertTrue(preview.missingZero());
        service.apply(preview).join();
        var row = service.load(Period.DAYS_90).join().rows().getFirst();
        assertEquals(0, new BigDecimal("18").compareTo(row.stockAt("MAGC")));
        assertEquals(0, new BigDecimal("7").compareTo(row.stockAt("MAGMOD")));
        assertEquals(0, new BigDecimal("25").compareTo(row.stock()));
        assertNull(row.sold());
        String orders =
                "Cons. prev.;Articolo;Quantita;Consegnata;Da consegn.\n13/10/2026;"
                        + article.code()
                        + ";5;2;3\n13/10/2026;"
                        + article.code()
                        + ";10;9;1\n";
        var pending =
                new CsvReader().read(new StringReader(orders), CsvReadOptions.semicolonSeparated());
        service.apply(
                        service.preview(
                                        Kind.ORDINI,
                                        pending,
                                        "orders.csv",
                                        LocalDate.now(),
                                        "Articolo",
                                        "Da consegn.",
                                        null,
                                        Numbers.ITALIAN,
                                        true,
                                        true)
                                .join())
                .join();
        apply(Kind.DISPONIBILITA, article.code() + ";2\n", false, false);
        row = service.load(Period.DAYS_90).join().rows().getFirst();
        assertEquals(0, new BigDecimal("2").compareTo(row.stockAt("MAGC")));
        assertEquals(0, row.stockAt("MAGMOD").signum());
        assertEquals(0, new BigDecimal("4").compareTo(row.orders()));
        apply(Kind.DISPONIBILITA, "", false, false);
        row = service.load(Period.DAYS_90).join().rows().getFirst();
        assertEquals(0, row.stock().signum());
        assertEquals(0, row.stockAt("MAGC").signum());
        assertEquals(0, row.stockAt("MAGMOD").signum());
        assertEquals(0, new BigDecimal("4").compareTo(row.orders()));
    }

    @Test
    void oldSnapshotsRemainUnallocatedUntilACompleteWarehouseFileReplacesThem() {
        db.withTransaction(
                c -> {
                    long id;
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.DISPONIBILITA_IMPORT(tipo,nome_file,data_riferimento,codici_assenti_zero,righe)"
                                        + " VALUES"
                                        + " ('DISPONIBILITA','old.csv',CURRENT_DATE,FALSE,1)",
                                    Statement.RETURN_GENERATED_KEYS)) {
                        q.executeUpdate();
                        try (var r = q.getGeneratedKeys()) {
                            r.next();
                            id = r.getLong(1);
                        }
                    }
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.DISPONIBILITA_VALORI(import_id,codice,quantita)"
                                        + " VALUES (?,?,12)")) {
                        q.setLong(1, id);
                        q.setString(2, article.code());
                        q.executeUpdate();
                    }
                    try (var q =
                            c.prepareStatement(
                                    "UPDATE TON.DISPONIBILITA_FONTI SET import_id=? WHERE"
                                        + " tipo='DISPONIBILITA'")) {
                        q.setLong(1, id);
                        q.executeUpdate();
                    }
                    return null;
                });
        var legacy = service.load(Period.DAYS_90).join();
        var row = legacy.rows().getFirst();
        assertEquals(0, new BigDecimal("12").compareTo(row.stockAt(UNALLOCATED)));
        assertNull(row.stockAt("MAGC"));
        assertNull(row.stockAt("MAGMOD"));
        assertTrue(legacy.warehouses().stream().anyMatch(w -> w.code().equals(UNALLOCATED)));
        apply(Kind.DISPONIBILITA, article.code() + ";2\n", false, false);
        assertFalse(
                service.load(Period.DAYS_90).join().warehouses().stream()
                        .anyMatch(w -> w.code().equals(UNALLOCATED)));
        assertEquals(
                0,
                new BigDecimal("2")
                        .compareTo(service.load(Period.DAYS_90).join().total(Row::stock)));
    }

    @Test
    void missingDepotInvalidatesTheWholeFileWithoutReplacingEitherWarehouse() {
        apply(Kind.DISPONIBILITA, article.code() + ";8\n", false, false);
        var document =
                new CsvReader()
                        .read(
                                new StringReader(
                                        "Articolo;Quantità Attuale;Deposito\n"
                                                + article.code()
                                                + ";2;MAGC\n"
                                                + article.code()
                                                + ";3;\n"),
                                CsvReadOptions.semicolonSeparated());
        var preview =
                service.preview(
                                Kind.DISPONIBILITA,
                                document,
                                "bad.csv",
                                LocalDate.now(),
                                "Articolo",
                                "Quantità Attuale",
                                "Deposito",
                                Numbers.ITALIAN,
                                false,
                                true)
                        .join();
        assertFalse(preview.errors().isEmpty());
        assertThrows(CompletionException.class, () -> service.apply(preview).join());
        assertEquals(
                0,
                new BigDecimal("8")
                        .compareTo(
                                service.load(Period.DAYS_90)
                                        .join()
                                        .rows()
                                        .getFirst()
                                        .stockAt("MAGC")));
    }

    @Test
    void duplicateAndDecimalErrorsCannotPartiallyOverwriteData() {
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;8\n", false, false);
        var p =
                preview(
                        Kind.DISPONIBILITA,
                        "Codice;Quantità\nZR9812MSTA1;1\nZR9812MSTA1;2\n",
                        false,
                        false);
        assertFalse(p.errors().isEmpty());
        assertThrows(CompletionException.class, () -> service.apply(p).join());
        assertEquals(
                0,
                new BigDecimal("8")
                        .compareTo(service.load(Period.DAYS_90).join().rows().getFirst().stock()));
        var negative = preview(Kind.ORDINI, "Codice;Quantità\nZR9812MSTA1;-1\n", true, false);
        assertFalse(negative.errors().isEmpty());
        assertEquals(0, new BigDecimal("1234.5").compareTo(Numbers.ITALIAN.parse("1.234,5")));
        assertThrows(IllegalArgumentException.class, () -> Numbers.ITALIAN.parse("1.23"));
        assertThrows(IllegalArgumentException.class, () -> Numbers.POINT.parse("1,5"));
        assertThrows(IllegalArgumentException.class, () -> Numbers.POINT.parse("0.0001"));
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;-2\n", false, false);
        assertEquals(
                0,
                new BigDecimal("-2")
                        .compareTo(service.load(Period.DAYS_90).join().rows().getFirst().stock()));
    }

    @Test
    void staleImportRollsBackWithoutLosingTheOtherOperatorsSnapshot() {
        var stale = preview(Kind.DISPONIBILITA, "Codice;Quantità\nZR9812MSTA1;1\n", false, false);
        var winner = preview(Kind.DISPONIBILITA, "Codice;Quantità\nZR9812MSTA1;7\n", false, false);
        service.apply(winner).join();
        assertInstanceOf(
                Conflict.class,
                assertThrows(CompletionException.class, () -> service.apply(stale).join())
                        .getCause());
        assertEquals(
                0,
                new BigDecimal("7")
                        .compareTo(service.load(Period.DAYS_90).join().total(Row::stock)));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT COUNT(*) FROM TON.DISPONIBILITA_IMPORT")) {
                        r.next();
                        assertEquals(1, r.getInt(1));
                    }
                    return null;
                });
    }

    @Test
    void historicalCodesContinueToMatchStockAndSalesWithoutChangingCatalogueCodes() {
        var detail = catalog.load(CatalogKind.ARTICLES, article.id()).join();
        catalog.save(
                        detail.entry(),
                        new ArticleDraft(
                                "NEW",
                                "One",
                                product.id(),
                                article.diameter(),
                                article.height(),
                                "",
                                true))
                .join();
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;5\nNEW;2\nOTHER;99\n", false, false);
        sale("ZR9812MSTA1", LocalDate.of(2026, 11, 20), "VENDITA", "10", 1);
        sale("NEW", LocalDate.of(2026, 11, 21), "VENDITA", "4", 2);
        sale("NEW", LocalDate.of(2026, 11, 21), "RESO", "2", 3);
        var s = service.load(Period.DAYS_90).join();
        var row = s.rows().getFirst();
        assertEquals("NEW", row.article().code());
        assertEquals(0, new BigDecimal("7").compareTo(row.stock()));
        assertEquals(0, new BigDecimal("14").compareTo(row.sold()));
        assertEquals(1, s.unmatchedStock());
    }

    @Test
    void salesUseTheVisibleObservationPeriodAndDoNotMoveStock() {
        apply(Kind.DISPONIBILITA, "ZR9812MSTA1;9\n", false, false);
        sale(article.code(), LocalDate.of(2026, 11, 21), "VENDITA", "4", 1);
        sale(article.code(), LocalDate.of(2026, 8, 24), "VENDITA", "6", 2);
        sale(article.code(), LocalDate.of(2026, 8, 23), "VENDITA", "7", 3);
        sale(article.code(), LocalDate.of(2026, 11, 22), "VENDITA", "99", 4);
        var s = service.load(Period.DAYS_90).join();
        assertEquals(LocalDate.of(2026, 8, 24), s.from());
        assertEquals(0, new BigDecimal("10").compareTo(s.total(Row::sold)));
        assertEquals(0, new BigDecimal("9").compareTo(s.total(Row::stock)));
        assertEquals(
                0,
                new BigDecimal("4")
                        .compareTo(service.load(Period.DAYS_30).join().total(Row::sold)));
    }

    @Test
    void eightyCharacterArticleCodesAreSupported() {
        var d = catalog.load(CatalogKind.ARTICLES, article.id()).join();
        String code = "A".repeat(80);
        catalog.save(
                        d.entry(),
                        new ArticleDraft(
                                code,
                                "One",
                                product.id(),
                                article.diameter(),
                                article.height(),
                                "",
                                true))
                .join();
        apply(Kind.DISPONIBILITA, code + ";1\n", false, false);
        assertEquals(code, service.load(Period.DAYS_90).join().rows().getFirst().article().code());
    }

    void sale(String code, LocalDate day, String operation, String amount, int number) {
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita,tipo_operazione)"
                                        + " VALUES ('TEST','D',?,?,?,?,?,?)")) {
                        q.setInt(1, number);
                        q.setDate(2, java.sql.Date.valueOf(day));
                        q.setString(3, "C");
                        q.setString(4, code);
                        q.setBigDecimal(5, new BigDecimal(amount));
                        q.setString(6, operation);
                        q.executeUpdate();
                    }
                    return null;
                });
    }
}
