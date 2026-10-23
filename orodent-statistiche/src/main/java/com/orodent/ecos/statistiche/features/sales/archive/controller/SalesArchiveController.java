package com.orodent.ecos.statistiche.features.sales.archive.controller;

import com.orodent.ecos.statistiche.features.sales.analysis.controller.AnalysisController;
import com.orodent.ecos.statistiche.features.sales.analysis.model.SalesArchiveItem;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.archive.view.SalesArchiveView;

public final class SalesArchiveController extends AnalysisController<SalesArchiveItem> {

    public SalesArchiveController(SalesArchiveView view, SalesAnalysisService service) {
        super(view, year -> service.loadArchive(year, view.searchText()));
    }
}
