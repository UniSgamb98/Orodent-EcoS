package com.orodent.ecos.statistiche.features.sales.returns.controller;

import com.orodent.ecos.statistiche.features.sales.analysis.controller.AnalysisController;
import com.orodent.ecos.statistiche.features.sales.analysis.model.ReturnAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.returns.view.ReturnsView;

public final class ReturnsController extends AnalysisController<ReturnAnalysisItem> {

    public ReturnsController(ReturnsView view, SalesAnalysisService service) {
        super(view, service::loadReturns);
    }
}
