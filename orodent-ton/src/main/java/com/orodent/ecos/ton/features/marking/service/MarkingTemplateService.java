package com.orodent.ecos.ton.features.marking.service;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.marking.repository.MarkingTemplateRepository;

import java.util.List;
import java.util.concurrent.*;

public final class MarkingTemplateService {
    private final ConnectionProvider db;
    private final Executor workers;

    public MarkingTemplateService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    public CompletableFuture<List<Entry>> list(String query, boolean archived) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withConnection(
                                c -> new MarkingTemplateRepository(c).list(query, archived)),
                workers);
    }

    public CompletableFuture<List<Version>> rules() {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new MarkingTemplateRepository(c).rules()), workers);
    }

    public CompletableFuture<Detail> load(long id, Long version) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new MarkingTemplateRepository(c).load(id, version)),
                workers);
    }

    public CompletableFuture<Detail> save(
            Entry expected, Snapshot selected, Draft draft, boolean approve) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c ->
                                        new MarkingTemplateRepository(c)
                                                .save(expected, selected, draft, approve)),
                workers);
    }

    public CompletableFuture<Detail> copy(Entry expected, Snapshot source) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> new MarkingTemplateRepository(c).copy(expected, source)),
                workers);
    }
}
