package com.orodent.ecos.ton.features.production.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels;
import com.orodent.ecos.ton.features.catalog.service.*;
import com.orodent.ecos.ton.features.compositions.model.Compositions;
import com.orodent.ecos.ton.features.compositions.service.CompositionService;
import com.orodent.ecos.ton.features.diskmodels.model.DiskModels;
import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;
import com.orodent.ecos.ton.features.documents.model.DocumentTemplates;
import com.orodent.ecos.ton.features.documents.service.DocumentTemplateService;
import com.orodent.ecos.ton.features.powders.model.PowderModel;
import com.orodent.ecos.ton.features.powders.service.PowderService;
import com.orodent.ecos.ton.features.production.model.Production.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executor;

public final class ProductionFixture {
    public final CatalogModels.Line line;
    public final CatalogModels.Product product;
    public final Map<Integer, Long> articles = new TreeMap<>();
    public final DocumentTemplates.Detail template;
    public final ProductionService service;
    public final DiskModels.Detail model;

    public ProductionFixture(ConnectionProvider db, Executor workers) {
        service = new ProductionService(db, workers);
        var catalog = new CatalogService(db, workers);
        line =
                (CatalogModels.Line)
                        catalog.create(
                                        new CatalogModels.LineDraft(
                                                "ZRMST", "Thor", "Multistrato", "ZR", "", true))
                                .join()
                                .entry();
        product =
                (CatalogModels.Product)
                        catalog.create(
                                        CatalogModels.ProductDraft.configured(
                                                line.id(), "A4", "", true))
                                .join()
                                .entry();
        var powders = new PowderService(db, workers);
        var revisions = new ArrayList<Long>();
        for (String code : List.of("ZPEX4.M", "ZPEX4.M YELLOW")) {
            var p =
                    powders.create(
                                    new PowderModel.IdentityDraft(
                                            code,
                                            code,
                                            code.contains("YELLOW")
                                                    ? PowderModel.PowderColor.GIALLA
                                                    : PowderModel.PowderColor.BIANCA,
                                            true,
                                            "Tosoh"),
                                    new PowderModel.TechnicalDraft(
                                            null,
                                            null,
                                            4,
                                            "",
                                            List.of(
                                                    new PowderModel.Oxide(
                                                            "Y2O3", new BigDecimal("7.1")))),
                                    true)
                            .join();
            revisions.add(p.latest().id());
        }
        var layers = new ArrayList<Compositions.Layer>();
        for (int i = 1; i <= 4; i++)
            layers.add(
                    new Compositions.Layer(
                            i,
                            "",
                            List.of(
                                    new Compositions.Ingredient(
                                            revisions.get(0), new BigDecimal("70")),
                                    new Compositions.Ingredient(
                                            revisions.get(1), new BigDecimal("30")))));
        var comp =
                new CompositionService(db, workers)
                        .create(new Compositions.Draft(product.id(), "", layers), true)
                        .join();
        var mouldEntry =
                new com.orodent.ecos.ton.features.equipment.service.EquipmentService(db, workers)
                        .save(
                                com.orodent.ecos.ton.features.equipment.model.Equipment.Kind.MOULDS,
                                null,
                                new com.orodent.ecos.ton.features.equipment.model.Equipment.Draft(
                                        "S98", "Stampo 98", new BigDecimal("98"), "", true))
                        .join();
        var mould =
                new DiskModels.Mould(
                        mouldEntry.id(),
                        mouldEntry.revision(),
                        mouldEntry.code(),
                        mouldEntry.name(),
                        mouldEntry.dimension());
        model =
                new DiskModelService(db, workers)
                        .create(
                                new DiskModels.Draft(
                                        "THOR-98",
                                        new BigDecimal("100"),
                                        new BigDecimal("1.5"),
                                        new BigDecimal("0.6"),
                                        "",
                                        List.of(
                                                new BigDecimal("30"),
                                                new BigDecimal("20"),
                                                new BigDecimal("20"),
                                                new BigDecimal("30")),
                                        List.of(),
                                        List.of(
                                                new DiskModels.DiameterGroup(
                                                        new BigDecimal("98"),
                                                        new BigDecimal("25"),
                                                        new DiskModels.Moulds(mould, List.of())))),
                                true)
                        .join();
        for (int height : List.of(12, 14, 16, 18, 20, 22)) {
            var a =
                    (CatalogModels.Article)
                            catalog.create(
                                            new CatalogModels.ArticleDraft(
                                                    "ZR98" + height + "MSTA4",
                                                    "Thor A4",
                                                    product.id(),
                                                    new BigDecimal("98"),
                                                    BigDecimal.valueOf(height),
                                                    "",
                                                    true))
                                    .join()
                                    .entry();
            articles.put(height, a.id());
            if (height == 22) catalog.setActive(a, false).join();
            if (height == 12)
                new SpecificationService(db, workers)
                        .save(
                                product,
                                null,
                                new CatalogModels.SpecificationDraft(
                                        comp.revision().id(),
                                        model.revision().id(),
                                        "COSTI-NOMINALE-1",
                                        ""),
                                true)
                        .join();
        }
        String content =
                """
{"schemaVersion":4,"title":"Foglio utente laboratorio","page":{"orientation":"landscape","margin":15},
 "content":{"type":"doc","content":[{"type":"heading","attrs":{"level":1},"content":[{"type":"text","text":"MODELLO UTENTE"}]},
 {"type":"tonBlock","attrs":{"kind":"identity"}},{"type":"tonBlock","attrs":{"kind":"press"}},
 {"type":"tonBlock","attrs":{"kind":"weighing"}},{"type":"tonBlock","attrs":{"kind":"mixtures"}},
 {"type":"tonBlock","attrs":{"kind":"totals"}},{"type":"paragraph","content":[{"type":"tonField","attrs":{"key":"notes"}}]}]}}
""";
        template =
                new DocumentTemplateService(db, workers)
                        .create(new DocumentTemplates.Draft("LAB", content), true)
                        .join();
    }

    public Workspace workspace() {
        return service.workspace(product.id(), new BigDecimal("98"), Map.of()).join();
    }

    public Request request(boolean document) {
        return new Request(
                LocalDate.of(2026, 11, 19),
                product.id(),
                new BigDecimal("98"),
                document ? template.template().id() : null,
                "Istruzioni reali <test>",
                Map.of(articles.get(14), 16, articles.get(18), 32, articles.get(20), 16));
    }
}
