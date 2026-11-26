package com.orodent.ecos.misurazioni.labels.service;

import static com.orodent.ecos.misurazioni.labels.model.Labels.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.documents.DocumentPaper;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.labels.repository.LabelRepository;

import java.time.*;
import java.util.concurrent.*;

public final class LabelService {
    private final ConnectionProvider db;
    private final Executor workers;
    private final Clock clock;

    public LabelService(ConnectionProvider db, Executor workers) {
        this(db, workers, Clock.systemDefaultZone());
    }

    public LabelService(ConnectionProvider db, Executor workers, Clock clock) {
        this.db = db;
        this.workers = workers;
        this.clock = clock;
    }

    public CompletableFuture<Source> source(Key key) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withConnection(
                                c -> new LabelRepository(c).source(key, LocalDate.now(clock))),
                workers);
    }

    public CompletableFuture<Issued> issue(Prepared prepared, Operator operator) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c ->
                                        new LabelRepository(c)
                                                .issue(prepared, operator, LocalDate.now(clock))),
                workers);
    }

    public static String payload(Source source) {
        return com.orodent.ecos.misurazioni.labels.model.LabelData.payload(source);
    }

    public static Prepared prepared(Source source, Template template, String html) {
        return new Prepared(
                source,
                template,
                source.expiry(),
                payload(source),
                html,
                DocumentPaper.from(template.content()));
    }
}
