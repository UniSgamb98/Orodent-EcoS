package com.orodent.ecos.misurazioni.labels.service;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.jobs.service.MeasurementJobsFixture;
import com.orodent.ecos.misurazioni.labels.model.Labels.*;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

class LabelServiceTest {
    private ConnectionProvider db;
    private String url;
    private ExecutorService workers;
    private LabelService service;
    private static final Key KEY = new Key(1, 1, 1);
    static final String MODEL =
            """
{"schemaVersion":5,"purpose":"ETICHETTA","title":"Scatola","page":{"format":"custom","width":100,"height":65,"orientation":"portrait","margin":3},"content":{"type":"doc","content":[{"type":"paragraph","content":[{"type":"labelField","attrs":{"key":"article"}}]}]}}
""";

    @BeforeEach
    void setup() {
        url = "jdbc:derby:memory:labels_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        MeasurementJobsFixture.seed(db);
        workers = Executors.newFixedThreadPool(2);
        service = new LabelService(db, workers, MeasurementJobsFixture.CLOCK);
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello,stato,approvato_il)"
                                        + " VALUES"
                                        + " ('BOX',1,'Scatola','ETICHETTA',?,'APPROVATA',CURRENT_TIMESTAMP)")) {
                        q.setString(1, MODEL);
                        q.executeUpdate();
                    }
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET etichetta_modello_id=1"
                                        + " WHERE configurazione_id=1");
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET gtin='09506000134352' WHERE"
                                        + " articolo_id=1");
                        q.executeUpdate(
                                "UPDATE TON.PIANI_PRODUZIONE SET"
                                        + " contenuto_piano='{\"product\":{\"name\":\"Nome"
                                        + " congelato\",\"lineName\":\"Thor originale\"}}' WHERE"
                                        + " piano_id=1");
                    }
                    return null;
                });
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    @Test
    void labelsNeedNoMeasurementsAndRetainOriginalContentAndDiscLinks() {
        Source source = service.source(KEY).join();
        assertEquals(0, source.job().done());
        assertEquals(1L, source.assignedTemplate());
        assertEquals(1, source.specificationVersion());
        assertEquals(LocalDate.of(2031, 11, 21), source.expiry());
        assertEquals("Nome congelato", source.name());
        assertEquals("Thor originale", source.line());
        String html = "<html><body>Etichetta originale</body></html>";
        Prepared prepared = LabelService.prepared(source, source.templates().getFirst(), html);
        Issued issued = service.issue(prepared, new Operator("123", "Operatore test")).join();
        assertEquals(100, issued.paper().width());
        assertEquals(65, issued.paper().height());
        var data = JsonParser.parseString(issued.data()).getAsJsonObject();
        assertEquals("09506000134352", data.get("gtin").getAsString());
        assertEquals("311121", data.get("expiryDateGs1").getAsString());
        assertEquals("261121", data.get("productionDateGs1").getAsString());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var rs = q.executeQuery("SELECT COUNT(*) FROM TON.ETICHETTA_DISCHI")) {
                        rs.next();
                        assertEquals(3, rs.getInt(1));
                    }
                    return null;
                });
        db.withTransaction(
                c -> {
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello,stato,approvato_il)"
                                        + " VALUES"
                                        + " ('BOX',2,'Nuova','ETICHETTA',?,'APPROVATA',CURRENT_TIMESTAMP)")) {
                        q.setString(1, MODEL.replace("100", "110"));
                        q.executeUpdate();
                    }
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.MODELLI_DOCUMENTO SET stato='RITIRATA' WHERE"
                                        + " modello_documento_id=1");
                        q.executeUpdate(
                                "UPDATE TON.ARTICOLI SET"
                                    + " gtin='04006381333931',versione_modifica=versione_modifica+1"
                                    + " WHERE articolo_id=1");
                    }
                    return null;
                });
        Source reopened = service.source(KEY).join();
        assertEquals(1L, reopened.assignedTemplate());
        assertEquals(issued.id(), reopened.latest().id());
        assertEquals(html, reopened.latest().html());
        assertEquals(3, reopened.latest().paper().margin());
        assertEquals(issued.data(), reopened.latest().data());
        assertEquals(
                MODEL,
                reopened.templates().stream()
                        .filter(t -> t.id() == 1)
                        .findFirst()
                        .orElseThrow()
                        .content());
        assertThrows(CompletionException.class, () -> service.issue(prepared, null).join());
        var altered =
                new Prepared(
                        reopened,
                        reopened.templates().getFirst(),
                        LocalDate.of(2030, 1, 1),
                        LabelService.payload(reopened),
                        html,
                        issued.paper());
        assertThrows(CompletionException.class, () -> service.issue(altered, null).join());
    }

    @Test
    void aHistoricalSpecificationWithNoLabelDoesNotPickTheFirstTemplate() {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET etichetta_modello_id=NULL");
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,calcolo_versione_id,stato,approvato_il,etichetta_modello_id)"
                                    + " VALUES"
                                    + " (1,2,1,1,'RITIRO',1,'APPROVATA',CURRENT_TIMESTAMP,1)");
                        q.executeUpdate(
                                "UPDATE TON.PRODOTTI SET configurazione_predefinita_id=3 WHERE"
                                        + " prodotto_id=1");
                    }
                    return null;
                });
        Source source = service.source(KEY).join();
        assertNull(source.assignedTemplate());
        assertEquals(1, source.specificationVersion());
        assertEquals(2, source.preferredSpecificationVersion());
        assertTrue(source.missingTemplateMessage().contains("specifica v1"));
        assertTrue(source.missingTemplateMessage().contains("attuale del prodotto è v2"));
        assertThrows(
                CompletionException.class,
                () ->
                        service.issue(
                                        LabelService.prepared(
                                                source,
                                                source.templates().getFirst(),
                                                "<html>Test</html>"),
                                        null)
                                .join());
        assertEquals(1, source.templates().size());
        assertNull(source.latest());
        assertDoesNotThrow(() -> LabelService.payload(source));
    }
}
