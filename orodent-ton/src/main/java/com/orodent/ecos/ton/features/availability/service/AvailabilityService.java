package com.orodent.ecos.ton.features.availability.service;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.ton.features.availability.repository.AvailabilityRepository;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public final class AvailabilityService {
    private final ConnectionProvider db;
    private final Executor workers;
    private final Clock clock;

    public AvailabilityService(ConnectionProvider db, Executor workers) {
        this(db, workers, Clock.systemDefaultZone());
    }

    public AvailabilityService(ConnectionProvider db, Executor workers, Clock clock) {
        this.db = db;
        this.workers = workers;
        this.clock = clock;
    }

    public CompletableFuture<Snapshot> load(Period period) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(
                                            Connection.TRANSACTION_REPEATABLE_READ);
                                    c.setReadOnly(true);
                                    return new AvailabilityRepository(c)
                                            .snapshot(period, LocalDate.now(clock));
                                }),
                workers);
    }

    public CompletableFuture<CsvDocument> read(Path file, char delimiter, Charset charset) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (Files.size(file) > 16 * 1024 * 1024)
                            throw new IllegalArgumentException("Il CSV supera 16 MiB.");
                    } catch (java.io.IOException e) {
                        throw new IllegalArgumentException(
                                "Impossibile leggere il file selezionato.", e);
                    }
                    var document =
                            new CsvReader()
                                    .read(
                                            file,
                                            new CsvReadOptions(
                                                    delimiter, '"', true, true, true, charset));
                    if (document.headers().isEmpty())
                        throw new IllegalArgumentException(
                                "Il CSV è vuoto o privo di intestazioni.");
                    if (document.rows().size() > 100000)
                        throw new IllegalArgumentException("Il CSV contiene più di 100.000 righe.");
                    return document;
                },
                workers);
    }

    public CompletableFuture<Preview> preview(
            Kind kind,
            CsvDocument document,
            String filename,
            LocalDate date,
            String codeColumn,
            String quantityColumn,
            String warehouseColumn,
            Numbers numbers,
            boolean sumDuplicates,
            boolean missingZero) {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (date == null)
                        throw new IllegalArgumentException("Indica la data dei dati importati.");
                    if (codeColumn == null
                            || quantityColumn == null
                            || !document.headers().contains(codeColumn)
                            || !document.headers().contains(quantityColumn)
                            || Objects.equals(codeColumn, quantityColumn))
                        throw new IllegalArgumentException(
                                "Scegli due colonne distinte per codice e quantità.");
                    if (kind == Kind.DISPONIBILITA
                            && (warehouseColumn == null
                                    || !document.headers().contains(warehouseColumn)
                                    || Objects.equals(codeColumn, warehouseColumn)
                                    || Objects.equals(quantityColumn, warehouseColumn)))
                        throw new IllegalArgumentException(
                                "Scegli una colonna distinta per il deposito.");
                    var values = new LinkedHashMap<QuantityKey, BigDecimal>();
                    var errors = new ArrayList<String>();
                    int duplicates = 0;
                    for (var row : document.rows()) {
                        try {
                            var key =
                                    new QuantityKey(
                                            row.requiredValue(codeColumn),
                                            kind == Kind.ORDINI
                                                    ? UNALLOCATED
                                                    : row.requiredValue(warehouseColumn));
                            if (kind == Kind.DISPONIBILITA && key.warehouse().equals(UNALLOCATED))
                                throw new IllegalArgumentException(
                                        "Deposito riservato ai dati precedenti: scegli un deposito"
                                            + " reale.");
                            BigDecimal n = numbers.parse(row.requiredValue(quantityColumn));
                            if (kind == Kind.ORDINI && n.signum() < 0)
                                throw new IllegalArgumentException(
                                        "Gli ordini da evadere non possono essere negativi.");
                            if (values.containsKey(key)) {
                                duplicates++;
                                if (!sumDuplicates)
                                    throw new IllegalArgumentException(
                                            (kind == Kind.DISPONIBILITA
                                                            ? "Codice e deposito ripetuti: "
                                                                    + key.code()
                                                                    + " / "
                                                                    + key.warehouse()
                                                            : "Codice ripetuto: " + key.code())
                                                    + ". Abilita la somma delle righe se"
                                                    + " rappresentano quantità distinte.");
                                n = quantity(values.get(key).add(n));
                            }
                            values.put(key, n);
                        } catch (IllegalArgumentException e) {
                            if (errors.size() < 50)
                                errors.add("Riga " + row.lineNumber() + ": " + e.getMessage());
                        }
                    }
                    int repeated = duplicates;
                    return db.withTransaction(
                            c -> {
                                c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                                c.setReadOnly(true);
                                var repo = new AvailabilityRepository(c);
                                var aliases = repo.aliases(repo.articles());
                                var matches = new HashMap<String, String>();
                                values.keySet()
                                        .forEach(
                                                k -> {
                                                    var a = aliases.get(k.code());
                                                    if (a != null) matches.put(k.code(), a.code());
                                                });
                                return new Preview(
                                        kind,
                                        repo.source(kind).revision(),
                                        filename,
                                        date,
                                        missingZero,
                                        values,
                                        document.rows().size(),
                                        repeated,
                                        matches,
                                        errors);
                            });
                },
                workers);
    }

    public CompletableFuture<Void> apply(Preview preview) {
        return CompletableFuture.runAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    new AvailabilityRepository(c).apply(preview);
                                    return null;
                                }),
                workers);
    }
}
