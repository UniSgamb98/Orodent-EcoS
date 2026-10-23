package com.orodent.ecos.statistiche.features.sales.discounts.controller;

import com.orodent.ecos.statistiche.features.sales.analysis.controller.AnalysisController;
import com.orodent.ecos.statistiche.features.sales.analysis.model.DiscountAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.analysis.service.SalesAnalysisService;
import com.orodent.ecos.statistiche.features.sales.discounts.view.DiscountsView;

public final class DiscountsController extends AnalysisController<DiscountAnalysisItem> {

    public DiscountsController(DiscountsView view, SalesAnalysisService service) {
        super(view, service::loadDiscounts);
    }
}
