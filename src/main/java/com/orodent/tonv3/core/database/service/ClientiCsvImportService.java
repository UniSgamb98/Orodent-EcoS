package com.orodent.tonv3.core.database.service;

import com.orodent.tonv3.core.ConnectionProvider;
import com.orodent.tonv3.core.csv.CsvImportResult;
import com.orodent.tonv3.core.csv.CsvImporter;
import com.orodent.tonv3.core.csv.CsvReadOptions;
import com.orodent.tonv3.core.csv.CsvReader;
import com.orodent.tonv3.core.database.csv.ClienteCsvMapper;
import com.orodent.tonv3.core.database.csv.ClienteCsvSourceNormalizer;
import com.orodent.tonv3.core.database.csv.ClienteValidator;
import com.orodent.tonv3.core.database.model.Cliente;
import com.orodent.tonv3.core.database.repository.ClienteRepository;
import com.orodent.tonv3.core.database.repository.impl.ClienteRepositoryImpl;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class ClientiCsvImportService {
    private final ConnectionProvider connectionProvider;
    private final Executor executor;
    private final ClienteCsvSourceNormalizer normalizer;
    private final CsvImporter<Cliente> importer;
    private final Function<java.sql.Connection, ClienteRepository> repositoryFactory;

    public ClientiCsvImportService(ConnectionProvider connectionProvider, Executor executor) {
        this(connectionProvider, executor, new ClienteCsvSourceNormalizer(),
                new CsvImporter<>(new CsvReader(), new ClienteCsvMapper(), new ClienteValidator()),
                ClienteRepositoryImpl::new);
    }

    ClientiCsvImportService(
            ConnectionProvider connectionProvider,
            Executor executor,
            ClienteCsvSourceNormalizer normalizer,
            CsvImporter<Cliente> importer,
            Function<java.sql.Connection, ClienteRepository> repositoryFactory
    ) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
        this.executor = Objects.requireNonNull(executor);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.importer = Objects.requireNonNull(importer);
        this.repositoryFactory = Objects.requireNonNull(repositoryFactory);
    }

    public CompletableFuture<ClientiImportReport> importFile(Path path) {
        Objects.requireNonNull(path, "path");
        return CompletableFuture.supplyAsync(() -> importSync(path), executor);
    }

    private ClientiImportReport importSync(Path path) {
        CsvReadOptions options = CsvReadOptions.semicolonSeparated();
        String normalized = normalizer.normalize(path, options.charset());
        CsvImportResult<Cliente> result = importer.importReader(new StringReader(normalized), options);
        List<CsvImportResult.RowError> errors = new ArrayList<>(result.rowErrors());
        errors.addAll(duplicateErrors(result.validRows()));
        if (!result.documentErrors().isEmpty() || !errors.isEmpty() || result.validRows().isEmpty()) {
            List<CsvImportResult.DocumentError> documentErrors = result.documentErrors();
            if (result.validRows().isEmpty() && documentErrors.isEmpty() && errors.isEmpty()) {
                documentErrors = List.of(new CsvImportResult.DocumentError(1, "Il file non contiene clienti"));
            }
            return new ClientiImportReport(result.totalRows(), 0, 0, errors, documentErrors);
        }

        List<Cliente> clienti = result.validRows().stream().map(CsvImportResult.ImportedRow::value).toList();
        return connectionProvider.withTransaction(connection -> {
            ClienteRepository repository = repositoryFactory.apply(connection);
            int deleted = repository.deleteAll();
            int inserted = repository.insertAll(clienti);
            if (inserted != clienti.size()) throw new IllegalStateException("Inserimento anagrafica incompleto");
            return new ClientiImportReport(result.totalRows(), deleted, inserted, List.of(), List.of());
        });
    }

    private List<CsvImportResult.RowError> duplicateErrors(
            List<CsvImportResult.ImportedRow<Cliente>> rows
    ) {
        Map<String, Integer> firstLines = new HashMap<>();
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        for (CsvImportResult.ImportedRow<Cliente> row : rows) {
            String code = row.value().codiceCliente();
            Integer first = firstLines.putIfAbsent(code, row.lineNumber());
            if (first != null) {
                errors.add(new CsvImportResult.RowError(row.lineNumber(), ClienteCsvMapper.CODICE, code,
                        "Codice duplicato; prima occorrenza alla riga " + first));
            }
        }
        return errors;
    }
}
