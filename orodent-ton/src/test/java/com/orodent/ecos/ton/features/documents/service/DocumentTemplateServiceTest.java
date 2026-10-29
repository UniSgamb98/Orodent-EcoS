package com.orodent.ecos.ton.features.documents.service;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class DocumentTemplateServiceTest {
    private ConnectionProvider db;
    private ExecutorService workers;
    private DocumentTemplateService service;
    private String url, oldLog;
    private final AtomicInteger opened = new AtomicInteger(), background = new AtomicInteger();

    @BeforeEach
    void setup() throws Exception {
        oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/document-templates-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:documents_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "document-worker"));
        service =
                new DocumentTemplateService(
                        () -> {
                            opened.incrementAndGet();
                            if (Thread.currentThread().getName().equals("document-worker"))
                                background.incrementAndGet();
                            return db.openConnection();
                        },
                        workers);
    }

    @AfterEach
    void close() throws Exception {
        workers.shutdown();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        try {
            DriverManager.getConnection(url + ";drop=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
        if (oldLog == null) System.clearProperty("derby.stream.error.file");
        else System.setProperty("derby.stream.error.file", oldLog);
    }

    static String model(String name, boolean complete) {
        String nodes =
                complete
                        ? String.join(
                                ",",
                                List.of("identity", "press", "weighing", "mixtures", "totals")
                                        .stream()
                                        .map(
                                                k ->
                                                        "{\"type\":\"tonBlock\",\"attrs\":{\"kind\":\""
                                                                + k
                                                                + "\"}}")
                                        .toList())
                        : "{\"type\":\"paragraph\"}";
        return "{\"schemaVersion\":2,\"title\":\""
                + name
                + "\",\"page\":{\"orientation\":\"landscape\",\"margin\":15},\"content\":{\"type\":\"doc\",\"content\":["
                + nodes
                + "]}}";
    }

    @Test
    void persistsLayoutApprovesAndCopiesWithoutChangingHistory() {
        var draft = new Draft("pressatura", model("Scheda laboratorio", true));
        var saved = service.create(draft, false).join();
        assertEquals("PRESSATURA", saved.template().code());
        assertEquals(draft.content(), service.load(saved.template().id()).join().content());
        var approved = service.save(saved.template(), draft, true).join();
        assertEquals(State.APPROVATA, approved.template().state());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(approved.template(), draft, false));
        assertThrows(IllegalArgumentException.class, () -> service.delete(approved.template()));
        var copy = service.newVersion(approved.template()).join();
        assertEquals(2, copy.template().number());
        assertEquals(State.BOZZA, copy.template().state());
        assertEquals(draft.content(), copy.content());
        var edited =
                service.save(
                                copy.template(),
                                new Draft("PRESSATURA", model("Scheda aggiornata", true)),
                                false)
                        .join();
        assertEquals("Scheda aggiornata", edited.template().name());
        assertEquals(draft.content(), service.load(approved.template().id()).join().content());
        assertEquals(
                List.of(2, 1),
                service.versions("PRESSATURA").join().stream().map(Template::number).toList());
        assertEquals(List.of(edited.template()), service.page("scheda", 0).join().rows());
        assertEquals(opened.get(), background.get());
    }

    @Test
    void staleWritesAndDeletesDoNotOverwriteOtherPoststations() {
        var draft = new Draft("FOGLIO", model("Primo", false));
        var saved = service.create(draft, false).join();
        var updated =
                service.save(
                                saved.template(),
                                new Draft("FOGLIO", model("Da altra postazione", false)),
                                false)
                        .join();
        assertThrows(
                CompletionException.class,
                () -> service.save(saved.template(), draft, false).join());
        assertThrows(CompletionException.class, () -> service.delete(saved.template()).join());
        assertEquals(updated, service.load(saved.template().id()).join());
        service.delete(updated.template()).join();
        assertTrue(service.page("", 0).join().rows().isEmpty());
    }

    @Test
    void onlyOneConcurrentDraftCanBeCreatedPerCode() {
        var approved =
                service.create(new Draft("PRESSATURA", model("Operativo", true)), true).join();
        var first = service.newVersion(approved.template());
        var second = service.newVersion(approved.template());
        int successes = 0;
        for (var f : List.of(first, second)) {
            try {
                f.join();
                successes++;
            } catch (CompletionException expected) {
                assertTrue(expected.getCause() instanceof IllegalArgumentException);
            }
        }
        assertEquals(1, successes);
        assertEquals(2, service.versions("PRESSATURA").join().size());
    }

    @Test
    void validatesLayoutsBeforeAnyWriteAndKeepsIncompleteDrafts() {
        assertThrows(IllegalArgumentException.class, () -> new Draft("", model("Foglio", true)));
        for (String payload :
                List.of(
                        "<script>bad</script>",
                        "{}",
                        model("Foglio", true) + " garbage",
                        model("Foglio", true).replace("landscape", "invalid")))
            assertThrows(IllegalArgumentException.class, () -> new Draft("TEST", payload));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draft("TEST", " ".repeat(MAX_CONTENT + 1)));
        var incomplete = new Draft("PARZIALE", model("In lavorazione", false));
        assertThrows(IllegalArgumentException.class, () -> service.create(incomplete, true));
        var saved = service.create(incomplete, false).join();
        assertEquals(State.BOZZA, saved.template().state());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.save(saved.template(), incomplete, true));
        assertEquals(saved, service.load(saved.template().id()).join());
    }

    @Test
    void duplicateCodeRollsBackAndSearchIsLiteralAndPaginated() {
        var draft = new Draft("PRESSATURA", model("Nome % prova", true));
        var saved = service.create(draft, false).join();
        assertThrows(CompletionException.class, () -> service.create(draft, false).join());
        assertEquals(List.of(saved.template()), service.page("%", 0).join().rows());
        assertTrue(service.page("_", 0).join().rows().isEmpty());
        assertTrue(service.page("", 50).join().rows().isEmpty());
        assertFalse(service.page("", 0).join().more());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.save(
                                saved.template(),
                                new Draft("ALTRO", model("Foglio", true)),
                                false));
    }
}
