package com.orodent.ecos.costi.orders;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;

import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public final class OrderService {
    public record Catalogue(List<Stored> rows, List<Powder> powders) {
        public Catalogue {
            rows = List.copyOf(rows);
            powders = List.copyOf(powders);
        }
    }

    private final ConnectionProvider db;
    private final Executor workers;

    public OrderService(ConnectionProvider db, Executor workers) {
        this.db = Objects.requireNonNull(db);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Catalogue> list() {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                    c.setReadOnly(true);
                                    var r = new OrderRepository(c);
                                    return new Catalogue(r.list(), r.powders());
                                }),
                workers);
    }

    public CompletableFuture<Preview> preview(Path path, Charset charset) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (Files.size(path) > 32 * 1024 * 1024)
                            throw new IllegalArgumentException(
                                    "File CSV troppo grande: massimo 32 MiB.");
                    } catch (java.io.IOException e) {
                        throw new IllegalArgumentException(
                                "Impossibile leggere il file selezionato.", e);
                    }
                    var csv =
                            new CsvImporter<>(
                                            new CsvReader(),
                                            new OrderCsvMapper(),
                                            ModelValidator.<Row>none())
                                    .importFile(
                                            path,
                                            new CsvReadOptions(
                                                    ';', '"', true, true, true, charset));
                    return db.withTransaction(
                            c -> {
                                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                c.setReadOnly(true);
                                var r = new OrderRepository(c);
                                List<Powder> powders = r.powders();
                                Map<Code, Long> aliases = r.aliases();
                                var mapping = new HashMap<>(aliases);
                                Map<String, Long> canonical = new HashMap<>();
                                powders.forEach(p -> canonical.put(p.code(), p.id()));
                                var problems = new ArrayList<String>();
                                var keys = new HashSet<Key>();
                                for (var imported : csv.validRows()) {
                                    Row row = imported.value();
                                    if (!keys.add(row.key()))
                                        problems.add(
                                                "Chiave duplicata nel CSV, riga "
                                                        + imported.lineNumber()
                                                        + ": "
                                                        + row.key());
                                    if (!mapping.containsKey(row.source())
                                            && canonical.containsKey(row.code()))
                                        mapping.put(row.source(), canonical.get(row.code()));
                                }
                                var existing = new HashMap<Key, Stored>();
                                r.list().forEach(s -> existing.put(s.row().key(), s));
                                return new Preview(csv, powders, mapping, existing, problems);
                            });
                },
                workers);
    }

    public CompletableFuture<Report> apply(Preview preview, Map<Code, Long> chosen) {
        var choices = Map.copyOf(chosen);
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> new OrderRepository(c).apply(preview, choices)),
                workers);
    }

    public CompletableFuture<Void> edit(Stored row, LocalDate date, boolean excluded) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    new OrderRepository(c).edit(row, date, excluded);
                                    return null;
                                }),
                workers);
    }
}
