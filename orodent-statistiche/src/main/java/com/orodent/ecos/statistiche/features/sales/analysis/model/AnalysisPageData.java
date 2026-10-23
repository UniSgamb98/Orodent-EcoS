package com.orodent.ecos.statistiche.features.sales.analysis.model;

import java.util.List;

public record AnalysisPageData<T>(List<Integer> years, int selectedYear,
                                  AnalysisSummary summary, List<T> items) {
    public AnalysisPageData {
        years = List.copyOf(years);
        items = List.copyOf(items);
    }
}
