package com.orodent.ecos.ton;

import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.ton.features.availability.controller.AvailabilityController;
import com.orodent.ecos.ton.features.availability.model.Availability.Article;
import com.orodent.ecos.ton.features.availability.service.AvailabilityService;
import com.orodent.ecos.ton.features.availability.view.AvailabilityView;
import com.orodent.ecos.ton.features.catalog.bulk.controller.BulkCatalogController;
import com.orodent.ecos.ton.features.catalog.bulk.service.BulkCatalogService;
import com.orodent.ecos.ton.features.catalog.bulk.view.BulkCatalogView;
import com.orodent.ecos.ton.features.catalog.controller.CatalogController;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Navigation;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;
import com.orodent.ecos.ton.features.catalog.service.SpecificationService;
import com.orodent.ecos.ton.features.catalog.view.CatalogView;
import com.orodent.ecos.ton.features.compositions.controller.CompositionsController;
import com.orodent.ecos.ton.features.compositions.service.CompositionService;
import com.orodent.ecos.ton.features.compositions.view.CompositionsView;
import com.orodent.ecos.ton.features.cubing.controller.CubatureRulesController;
import com.orodent.ecos.ton.features.cubing.service.CubatureRuleService;
import com.orodent.ecos.ton.features.cubing.view.CubatureRulesView;
import com.orodent.ecos.ton.features.diskmodels.controller.DiskModelsController;
import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;
import com.orodent.ecos.ton.features.diskmodels.view.DiskModelsView;
import com.orodent.ecos.ton.features.documents.controller.DocumentTemplatesController;
import com.orodent.ecos.ton.features.documents.service.DocumentTemplateService;
import com.orodent.ecos.ton.features.documents.view.DocumentTemplatesView;
import com.orodent.ecos.ton.features.equipment.controller.EquipmentController;
import com.orodent.ecos.ton.features.equipment.model.Equipment.Kind;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.equipment.view.EquipmentView;
import com.orodent.ecos.ton.features.firing.controller.FiringController;
import com.orodent.ecos.ton.features.firing.service.FiringService;
import com.orodent.ecos.ton.features.firing.view.FiringView;
import com.orodent.ecos.ton.features.firingprograms.controller.FiringProgramsController;
import com.orodent.ecos.ton.features.firingprograms.service.FiringProgramService;
import com.orodent.ecos.ton.features.firingprograms.view.FiringProgramsView;
import com.orodent.ecos.ton.features.marking.controller.MarkingTemplatesController;
import com.orodent.ecos.ton.features.marking.service.MarkingTemplateService;
import com.orodent.ecos.ton.features.marking.view.MarkingTemplatesView;
import com.orodent.ecos.ton.features.powders.controller.PowdersController;
import com.orodent.ecos.ton.features.powders.service.PowderService;
import com.orodent.ecos.ton.features.powders.view.PowdersView;
import com.orodent.ecos.ton.features.production.controller.ProductionController;
import com.orodent.ecos.ton.features.production.service.ProductionService;
import com.orodent.ecos.ton.features.production.view.ProductionView;
import com.orodent.ecos.ton.features.suppliers.controller.SuppliersController;
import com.orodent.ecos.ton.features.suppliers.service.SupplierService;
import com.orodent.ecos.ton.features.suppliers.view.SuppliersView;
import com.orodent.ecos.ton.navigation.FeatureController;
import com.orodent.ecos.ton.navigation.TonFeature;
import com.orodent.ecos.ton.navigation.TonSection;
import com.orodent.ecos.ton.view.TonView;

import javafx.scene.Parent;

final class TonController {
    private final TonView view = new TonView();
    private final CatalogService catalogues;
    private final BulkCatalogService bulkCatalogues;
    private final SpecificationService specifications;
    private final PowderService powders;
    private final DiskModelService models;
    private final CompositionService compositions;
    private final DocumentTemplateService documents;
    private final ProductionService productions;
    private final FiringService firing;
    private final FiringProgramService firingPrograms;
    private final EquipmentService equipment;
    private final SupplierService suppliers;
    private final CubatureRuleService cubatureRules;
    private final MarkingTemplateService markingTemplates;
    private final AvailabilityService availability;
    private AvailabilityView availabilityView;
    private AvailabilityView.Position availabilityPosition = AvailabilityView.Position.initial();
    private final java.util.concurrent.Executor workers;
    private FeatureController active;
    private TonFeature activeFeature;
    private TonSection section = TonSection.OVERVIEW;

    TonController(ModuleContext context) {
        workers = context.workers();
        suppliers = new SupplierService(context.connections(), workers);
        cubatureRules = new CubatureRuleService(context.connections(), workers);
        markingTemplates = new MarkingTemplateService(context.connections(), workers);
        availability = new AvailabilityService(context.connections(), workers);
        equipment = new EquipmentService(context.connections(), workers);
        documents = new DocumentTemplateService(context.connections(), workers);
        productions = new ProductionService(context.connections(), workers);
        firing = new FiringService(context.connections(), workers);
        firingPrograms = new FiringProgramService(context.connections(), workers);
        catalogues = new CatalogService(context.connections(), context.workers());
        bulkCatalogues = new BulkCatalogService(context.connections(), context.workers());
        specifications = new SpecificationService(context.connections(), context.workers());
        powders = new PowderService(context.connections(), context.workers());
        models = new DiskModelService(context.connections(), context.workers());
        compositions = new CompositionService(context.connections(), context.workers());
        view.homeButton()
                .setOnAction(
                        event -> {
                            if (canLeave()) context.showHome().run();
                        });
        view.onSection(this::showSection);
        view.onFeature(this::showFeature);
    }

    TonView root() {
        return view;
    }

    private boolean canLeave() {
        return active == null || active.canLeave();
    }

    private void showSection(TonSection next) {
        if ((activeFeature == null && section == next) || !canLeave()) return;
        closePage();
        section = next;
        view.showSection(next);
    }

    private void showFeature(TonFeature feature) {
        if (activeFeature == feature || !canLeave()) return;
        closePage();
        switch (feature) {
            case CUBATURE_RULES -> {
                var page = new CubatureRulesView();
                page.subpageProperty().addListener((o, a, b) -> view.showFeature(page, feature, b));
                var controller = new CubatureRulesController(page, cubatureRules);
                install(feature, page, controller, controller::start);
            }
            case PRESSES, MOULDS, FURNACES -> {
                Kind kind =
                        switch (feature) {
                            case PRESSES -> Kind.PRESSES;
                            case MOULDS -> Kind.MOULDS;
                            default -> Kind.FURNACES;
                        };
                var page = new EquipmentView(kind);
                page.subpageProperty()
                        .addListener(
                                (o, before, subpage) -> view.showFeature(page, feature, subpage));
                var controller = new EquipmentController(page, equipment);
                install(feature, page, controller, controller::start);
            }
            case SUPPLIERS -> {
                var page = new SuppliersView();
                page.subpageProperty().addListener((o, a, b) -> view.showFeature(page, feature, b));
                var controller = new SuppliersController(page, suppliers);
                install(feature, page, controller, controller::start);
            }
            case AVAILABILITY -> installAvailability();
            case PRODUCTIONS -> installProductions(null);
            case PROGRAMS -> {
                var page = new FiringProgramsView();
                page.subpageProperty().addListener((o, a, b) -> view.showFeature(page, feature, b));
                var controller = new FiringProgramsController(page, firingPrograms);
                install(feature, page, controller, controller::start);
            }
            case PLANNING -> {
                var page = new FiringView();
                var controller =
                        new FiringController(page, firing, () -> showSection(TonSection.FIRING));
                install(feature, page, controller, controller::start);
            }
            case PRODUCTS -> {
                installCatalogue(Navigation.list(CatalogKind.PRODUCTS));
            }
            case LINES -> installCatalogue(Navigation.list(CatalogKind.LINES));
            case ARTICLES -> installCatalogue(Navigation.list(CatalogKind.ARTICLES));
            case POWDERS -> {
                var page = new PowdersView();
                page.subpageProperty()
                        .addListener(
                                (observable, before, subpage) ->
                                        view.showFeature(page, feature, subpage));
                var controller =
                        new PowdersController(
                                page,
                                powders,
                                id -> openCatalogue(Navigation.open(CatalogKind.PRODUCTS, id)));
                install(feature, page, controller, controller::loadInitialData);
            }
            case MODELS -> installModels(null);
            case DISK_MARKING -> {
                var page = new MarkingTemplatesView();
                page.subpageProperty().addListener((o, a, b) -> view.showFeature(page, feature, b));
                var controller =
                        new MarkingTemplatesController(
                                page,
                                markingTemplates,
                                id -> openCatalogue(Navigation.open(CatalogKind.PRODUCTS, id)));
                install(feature, page, controller, controller::start);
            }
            case DOCUMENT_TEMPLATES -> {
                var page = new DocumentTemplatesView();
                var controller = new DocumentTemplatesController(page, documents);
                install(feature, page, controller, controller::start);
            }
            case COMPOSITIONS -> {
                var page = new CompositionsView();
                page.subpageProperty()
                        .addListener(
                                (observable, before, subpage) ->
                                        view.showFeature(page, feature, subpage));
                var controller =
                        new CompositionsController(
                                page,
                                compositions,
                                id -> openCatalogue(Navigation.open(CatalogKind.PRODUCTS, id)),
                                this::openModel);
                install(feature, page, controller, controller::start);
            }
            default ->
                    throw new IllegalArgumentException(
                            "Funzione TON non ancora disponibile: " + feature);
        }
    }

    private void installAvailability() {
        availabilityView = new AvailabilityView();
        var controller =
                new AvailabilityController(availabilityView, availability, this::openProduction);
        install(
                TonFeature.AVAILABILITY,
                availabilityView,
                controller,
                () -> controller.start(availabilityPosition));
    }

    private void openProduction(Article article) {
        if (!canLeave()) return;
        closePage();
        installProductions(article);
    }

    private void installProductions(Article origin) {
        var page = new ProductionView();
        page.subpageProperty()
                .addListener((o, a, b) -> view.showFeature(page, TonFeature.PRODUCTIONS, b));
        var controller =
                new ProductionController(
                        page,
                        productions,
                        article -> openCatalogue(Navigation.open(CatalogKind.ARTICLES, article)),
                        product -> openCatalogue(Navigation.open(CatalogKind.PRODUCTS, product)));
        if (origin != null) {
            page.back.setText("← Torna alla disponibilità");
            controller.onReturn(
                    () -> {
                        closePage();
                        installAvailability();
                    });
        }
        install(
                TonFeature.PRODUCTIONS,
                page,
                controller,
                () -> {
                    if (origin == null) controller.start();
                    else controller.startForProduct(origin.productId(), origin.diameter());
                });
    }

    private void openCatalogue(Navigation route) {
        if (!canLeave()) return;
        closePage();
        installCatalogue(route);
    }

    private void installCatalogue(Navigation route) {
        if (route.bulk()) {
            var page = new BulkCatalogView(route.kind());
            var controller = new BulkCatalogController(page, bulkCatalogues, this::openCatalogue);
            page.modeProperty()
                    .addListener(
                            (o, previous, kind) -> {
                                activeFeature = kind.feature();
                                view.showFeature(page, activeFeature, "Inserimento multiplo");
                            });
            install(route.kind().feature(), page, controller, () -> controller.start(route));
            return;
        }
        var page = new CatalogView(route.kind());
        page.subpageProperty()
                .addListener(
                        (observable, before, subpage) ->
                                view.showFeature(page, route.kind().feature(), subpage));
        var controller =
                new CatalogController(
                        route.kind(), page, catalogues, specifications, this::openCatalogue);
        install(route.kind().feature(), page, controller, () -> controller.start(route));
    }

    private void openModel(long id) {
        if (!canLeave()) return;
        closePage();
        installModels(id);
    }

    private void installModels(Long id) {
        var page = new DiskModelsView();
        page.subpageProperty()
                .addListener(
                        (observable, before, subpage) ->
                                view.showFeature(page, TonFeature.MODELS, subpage));
        var controller =
                new DiskModelsController(
                        page,
                        models,
                        product -> openCatalogue(Navigation.open(CatalogKind.PRODUCTS, product)));
        install(
                TonFeature.MODELS,
                page,
                controller,
                () -> {
                    if (id == null) controller.start();
                    else controller.open(id);
                });
    }

    private void install(
            TonFeature feature, Parent page, FeatureController controller, Runnable initialize) {
        active = controller;
        activeFeature = feature;
        section = feature.section();
        view.navigationBlockedProperty().bind(active.savingProperty());
        view.showFeature(
                page, feature, page instanceof BulkCatalogView ? "Inserimento multiplo" : null);
        initialize.run();
    }

    private void closePage() {
        if (activeFeature == TonFeature.AVAILABILITY && availabilityView != null) {
            availabilityPosition = availabilityView.position();
            availabilityView = null;
        }
        view.navigationBlockedProperty().unbind();
        view.navigationBlockedProperty().set(false);
        if (active != null) {
            active.dispose();
            active = null;
        }
        activeFeature = null;
    }

    void dispose() {
        closePage();
        view.dispose();
    }
}
