package com.orodent.tonv3.core.database.service;

import com.orodent.tonv3.core.ConnectionProvider;
import com.orodent.tonv3.core.csv.CsvImportResult;
import com.orodent.tonv3.core.csv.CsvImporter;
import com.orodent.tonv3.core.csv.CsvReadOptions;
import com.orodent.tonv3.core.csv.CsvReader;
import com.orodent.tonv3.core.database.csv.VenditaDettaglioCsvMapper;
import com.orodent.tonv3.core.database.csv.VenditaDettaglioValidator;
import com.orodent.tonv3.core.database.model.VenditaDettaglio;
import com.orodent.tonv3.core.database.repository.VenditaDettaglioRepository;
import com.orodent.tonv3.core.database.repository.impl.VenditaDettaglioRepositoryImpl;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class VenditeCsvImportService {

    private final ConnectionProvider connectionProvider;
    private final CsvImporter<VenditaDettaglio> csvImporter;
    private final Executor executor;
    private final Function<Connection, VenditaDettaglioRepository> repositoryFactory;

    public VenditeCsvImportService(ConnectionProvider connectionProvider, Executor executor) {
        this(
                connectionProvider,
                new CsvImporter<>(
                        new CsvReader(),
                        new VenditaDettaglioCsvMapper(),
                        new VenditaDettaglioValidator()
                ),
                executor,
                VenditaDettaglioRepositoryImpl::new
        );
    }

    VenditeCsvImportService(
            ConnectionProvider connectionProvider,
            CsvImporter<VenditaDettaglio> csvImporter,
            Executor executor,
            Function<Connection, VenditaDettaglioRepository> repositoryFactory
    ) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.csvImporter = Objects.requireNonNull(csvImporter, "csvImporter");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory, "repositoryFactory");
    }

    public CompletableFuture<VenditeImportReport> importFile(Path path) {
        return importFile(path, CsvReadOptions.semicolonSeparated());
    }

    public CompletableFuture<VenditeImportReport> importFile(Path path, CsvReadOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        return CompletableFuture.supplyAsync(() -> importFileSync(path, options), executor);
    }

    private VenditeImportReport importFileSync(Path path, CsvReadOptions options) {
        CsvImportResult<VenditaDettaglio> csvResult = csvImporter.importFile(path, options);
        if (!csvResult.valid()) {
            return rejectedReport(csvResult, csvResult.documentErrors());
        }
        if (csvResult.validRows().isEmpty()) {
            CsvImportResult.DocumentError error = new CsvImportResult.DocumentError(
                    1,
                    "Il file CSV non contiene vendite: il database non è stato modificato"
            );
            return rejectedReport(csvResult, List.of(error));
        }

        List<VenditaDettaglio> vendite = csvResult.validRows().stream()
                .map(CsvImportResult.ImportedRow::value)
                .toList();
        LocalDate fromDate = vendite.stream()
                .map(VenditaDettaglio::dataVendita)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        LocalDate throughDate = vendite.stream()
                .map(VenditaDettaglio::dataVendita)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        return connectionProvider.withTransaction(connection -> {
            VenditaDettaglioRepository repository = repositoryFactory.apply(connection);
            int deletedRows = repository.deleteByDateRange(fromDate, throughDate);
            int insertedRows = repository.insertAll(vendite);
            if (insertedRows != vendite.size()) {
                throw new IllegalStateException(
                        "Inserimento incompleto: attese " + vendite.size() + " righe, inserite " + insertedRows
                );
            }
            return new VenditeImportReport(
                    fromDate,
                    throughDate,
                    csvResult.totalRows(),
                    deletedRows,
                    insertedRows,
                    List.of(),
                    List.of()
            );
        });
    }

    private VenditeImportReport rejectedReport(
            CsvImportResult<VenditaDettaglio> csvResult,
            List<CsvImportResult.DocumentError> documentErrors
    ) {
        return new VenditeImportReport(
                null,
                null,
                csvResult.totalRows(),
                0,
                0,
                csvResult.rowErrors(),
                documentErrors
        );
    }
}
