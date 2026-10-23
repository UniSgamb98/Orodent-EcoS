package com.orodent.ecos.database.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.CsvImporter;
import com.orodent.ecos.core.csv.CsvReader;
import com.orodent.ecos.database.csv.VenditaDettaglioCsvMapper;
import com.orodent.ecos.database.csv.VenditaDettaglioValidator;
import com.orodent.ecos.database.model.VenditaDettaglio;
import com.orodent.ecos.database.repository.VenditaDettaglioRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenditeCsvImportServiceTest {

    private static final String HEADER = "Data reg.;Nr. doc.;MA_InventoryEntries_StoragePhase1;"
            + "MA_InventoryReasons_Reason;MA_InventoryEntries_CustSupp;Articolo;Descrizione;"
            + "Unità misura;Quantità;Divisa;Qta UM base;Valore unitario;Sconto;Imp. sconto;"
            + "Importo riga;Importo Riga in Divisa Base";

    @Test
    void replacesImportedDateRangeInOneTransactionOnBackgroundThread() throws Exception {
        TransactionProbe transaction = new TransactionProbe();
        RecordingRepository repository = new RecordingRepository();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "test-import"));
        Path file = csvFile(row("06/10/2026", "001005") + "\n" + row("01/10/2026", "000976"));

        VenditeCsvImportService service = service(transaction, repository, executor);
        try {
            VenditeImportReport report = service.importFile(file).join();

            assertTrue(report.imported());
            assertEquals(LocalDate.of(2026, 10, 1), report.fromDate());
            assertEquals(LocalDate.of(2026, 10, 6), report.throughDate());
            assertEquals(2, report.totalRows());
            assertEquals(7, report.deletedRows());
            assertEquals(2, report.insertedRows());
            assertEquals(LocalDate.of(2026, 10, 1), repository.deletedFrom);
            assertEquals(LocalDate.of(2026, 10, 6), repository.deletedThrough);
            assertEquals(2, repository.inserted.size());
            assertEquals("test-import", repository.threadName);
            assertTrue(transaction.committed);
            assertFalse(transaction.rolledBack);
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(file);
        }
    }

    @Test
    void acceptsFilesSpanningMultipleYears() throws Exception {
        TransactionProbe transaction = new TransactionProbe();
        RecordingRepository repository = new RecordingRepository();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Path file = csvFile(row("06/10/2026", "001005") + "\n" + row("01/10/2025", "000976"));

        VenditeCsvImportService service = service(transaction, repository, executor);
        try {
            VenditeImportReport report = service.importFile(file).join();

            assertTrue(report.imported());
            assertEquals(LocalDate.of(2025, 10, 1), report.fromDate());
            assertEquals(LocalDate.of(2026, 10, 6), report.throughDate());
            assertTrue(transaction.opened);
            assertEquals(2, repository.inserted.size());
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rejectsInvalidRowsWithoutDeletingExistingData() throws Exception {
        TransactionProbe transaction = new TransactionProbe();
        RecordingRepository repository = new RecordingRepository();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        String invalidRow = row("06/10/2026", "001005").replace(";1;EUR;", ";0;EUR;");
        Path file = csvFile(invalidRow);

        VenditeCsvImportService service = service(transaction, repository, executor);
        try {
            VenditeImportReport report = service.importFile(file).join();

            assertFalse(report.imported());
            assertEquals(1, report.rowErrors().size());
            assertFalse(transaction.opened);
            assertEquals(null, repository.deletedFrom);
            assertEquals(null, repository.deletedThrough);
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(file);
        }
    }

    @Test
    void rollsBackDeletionWhenBatchInsertFails() throws Exception {
        TransactionProbe transaction = new TransactionProbe();
        RecordingRepository repository = new RecordingRepository();
        repository.failInsert = true;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Path file = csvFile(row("06/10/2026", "001005"));

        VenditeCsvImportService service = service(transaction, repository, executor);
        try {
            assertThrows(CompletionException.class, () -> service.importFile(file).join());
            assertFalse(transaction.committed);
            assertTrue(transaction.rolledBack);
        } finally {
            executor.shutdownNow();
            Files.deleteIfExists(file);
        }
    }

    private VenditeCsvImportService service(
            TransactionProbe transaction,
            RecordingRepository repository,
            ExecutorService executor
    ) {
        CsvImporter<VenditaDettaglio> importer = new CsvImporter<>(
                new CsvReader(),
                new VenditaDettaglioCsvMapper(),
                new VenditaDettaglioValidator()
        );
        return new VenditeCsvImportService(transaction, importer, executor, connection -> repository);
    }

    private Path csvFile(String rows) throws Exception {
        Path file = Files.createTempFile("vendite-", ".csv");
        Files.writeString(file, HEADER + "\n" + rows);
        return file;
    }

    private String row(String date, String document) {
        return date + ";" + document + ";MAGMOD;VEND;20ZR1036;ZR9816MSA2;"
                + "Prodotto;N.;1;EUR;1;211;55+10;125,54;85,46;85,46";
    }

    private static final class TransactionProbe implements ConnectionProvider {
        private boolean opened;
        private boolean committed;
        private boolean rolledBack;

        @Override
        public Connection openConnection() {
            opened = true;
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "commit" -> committed = true;
                            case "rollback" -> rolledBack = true;
                            default -> { }
                        }
                        Class<?> returnType = method.getReturnType();
                        if (!returnType.isPrimitive()) {
                            return null;
                        }
                        if (returnType == boolean.class) {
                            return false;
                        }
                        return 0;
                    }
            );
        }
    }

    private static final class RecordingRepository implements VenditaDettaglioRepository {
        private LocalDate deletedFrom;
        private LocalDate deletedThrough;
        private final List<VenditaDettaglio> inserted = new ArrayList<>();
        private String threadName;
        private boolean failInsert;

        @Override
        public int deleteByDateRange(LocalDate from, LocalDate through) {
            deletedFrom = from;
            deletedThrough = through;
            threadName = Thread.currentThread().getName();
            return 7;
        }

        @Override
        public int insertAll(List<VenditaDettaglio> vendite) {
            if (failInsert) {
                throw new IllegalStateException("Errore simulato");
            }
            inserted.addAll(vendite);
            return vendite.size();
        }

        @Override public VenditaDettaglio insert(VenditaDettaglio vendita) { throw unsupported(); }
        @Override public void update(VenditaDettaglio vendita) { throw unsupported(); }
        @Override public Optional<VenditaDettaglio> findById(long venditaId) { throw unsupported(); }
        @Override public Optional<VenditaDettaglio> findByDocumento(String sorgente, String documentoId, int numeroRiga) { throw unsupported(); }
        @Override public List<VenditaDettaglio> findAll() { throw unsupported(); }
        @Override public List<VenditaDettaglio> findByDateRange(LocalDate dal, LocalDate al) { throw unsupported(); }
        @Override public List<VenditaDettaglio> findByClienteAndDateRange(String cliente, LocalDate dal, LocalDate al) { throw unsupported(); }
        @Override public List<VenditaDettaglio> findByProdottoAndDateRange(String prodotto, LocalDate dal, LocalDate al) { throw unsupported(); }
        @Override public List<VenditaDettaglio> findByClienteAndProdottoAndDateRange(String cliente, String prodotto, LocalDate dal, LocalDate al) { throw unsupported(); }
        @Override public List<VenditaDettaglio> findByCategoriaAndDateRange(String categoria, LocalDate dal, LocalDate al) { throw unsupported(); }
        @Override public void deleteById(long venditaId) { throw unsupported(); }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }
    }
}
