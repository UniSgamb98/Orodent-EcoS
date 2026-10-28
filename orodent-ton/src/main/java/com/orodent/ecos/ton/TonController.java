package com.orodent.ecos.ton;

import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.ton.features.catalog.controller.CatalogController;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Navigation;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;
import com.orodent.ecos.ton.features.catalog.service.SpecificationService;
import com.orodent.ecos.ton.features.catalog.view.CatalogView;
import com.orodent.ecos.ton.features.compositions.controller.CompositionsController;
import com.orodent.ecos.ton.features.compositions.service.CompositionService;
import com.orodent.ecos.ton.features.compositions.view.CompositionsView;
import com.orodent.ecos.ton.features.diskmodels.controller.DiskModelsController;
import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;
import com.orodent.ecos.ton.features.diskmodels.view.DiskModelsView;
import com.orodent.ecos.ton.features.powders.controller.PowdersController;
import com.orodent.ecos.ton.features.powders.service.PowderService;
import com.orodent.ecos.ton.features.powders.view.PowdersView;
import com.orodent.ecos.ton.navigation.FeatureController;
import com.orodent.ecos.ton.navigation.TonFeature;
import com.orodent.ecos.ton.navigation.TonSection;
import com.orodent.ecos.ton.view.TonView;

import javafx.scene.Parent;

final class TonController {
    private final TonView view = new TonView();
    private final CatalogService catalogues;
    private final SpecificationService specifications;
    private final PowderService powders;
    private final DiskModelService models;
    private final CompositionService compositions;
    private FeatureController active;
    private TonFeature activeFeature;
    private TonSection section = TonSection.OVERVIEW;

    TonController(ModuleContext context) {
        catalogues = new CatalogService(context.connections(), context.workers());
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
        if ((active == null && section == next) || !canLeave()) return;
        closePage();
        section = next;
        view.showSection(next);
    }

    private void showFeature(TonFeature feature) {
        if (activeFeature == feature || !canLeave()) return;
        closePage();
        switch (feature) {
            case PRODUCTS -> {
                installCatalogue(Navigation.list(CatalogKind.PRODUCTS));
            }
            case LINES -> installCatalogue(Navigation.list(CatalogKind.LINES));
            case ARTICLES -> installCatalogue(Navigation.list(CatalogKind.ARTICLES));
            case POWDERS -> {
                var page = new PowdersView();
                var controller = new PowdersController(page, powders);
                install(feature, page, controller, controller::loadInitialData);
            }
            case MODELS -> installModels(null);
            case COMPOSITIONS -> {
                var page = new CompositionsView();
                var controller =
                        new CompositionsController(
                                page,
                                compositions,
                                id -> openCatalogue(Navigation.open(CatalogKind.ARTICLES, id)),
                                this::openModel);
                install(feature, page, controller, controller::start);
            }
            default ->
                    throw new IllegalArgumentException(
                            "Funzione TON non ancora disponibile: " + feature);
        }
    }

    private void openCatalogue(Navigation route) {
        if (!canLeave()) return;
        closePage();
        installCatalogue(route);
    }

    private void installCatalogue(Navigation route) {
        var page = new CatalogView(route.kind());
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
        var controller =
                new DiskModelsController(
                        page,
                        models,
                        article -> openCatalogue(Navigation.open(CatalogKind.ARTICLES, article)));
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
        view.showFeature(page, feature);
        initialize.run();
    }

    private void closePage() {
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
