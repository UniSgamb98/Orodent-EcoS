package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.core.csv.CsvExportMapper;
import com.orodent.ecos.costi.products.csv.ProductCostCsv;
import com.orodent.ecos.costi.scenarios.ScenarioComparison.Display;
import com.orodent.ecos.costi.scenarios.Scenarios.*;

import java.util.*;

final class ScenarioCsv {
    private ScenarioCsv() {}

    static CsvExportMapper<Display> comparison(Draft draft, Weight weight, String level) {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Scenario ID",
                        "Revisione",
                        "Scenario",
                        "Base ID",
                        "Base acquisita UTC",
                        "Fonte prezzi",
                        "Periodo dal",
                        "Periodo al",
                        "Ponderazione",
                        "Livello",
                        "Gruppo o articolo",
                        "Base EUR/articolo",
                        "Scenario EUR/articolo",
                        "Delta EUR/articolo",
                        "Delta percentuale costo medio",
                        "Copertura",
                        "Articolo ID",
                        "Configurazione ID",
                        "Composizione base ID",
                        "Composizione base versione",
                        "Composizione scenario ID",
                        "Composizione scenario versione",
                        "Modello base",
                        "Modello base versione",
                        "Modello scenario",
                        "Modello scenario versione",
                        "Miscela base g",
                        "Miscela scenario g",
                        "Miscela nominale base EUR/kg",
                        "Miscela nominale scenario EUR/kg",
                        "Trasformazione base EUR/articolo",
                        "Trasformazione scenario EUR/articolo",
                        "Prezzi modificati",
                        "Ricette modificate",
                        "Modelli modificati",
                        "Trasformazioni modificate");
            }

            public List<?> map(Display r) {
                var before = r.row() == null ? null : r.row().before();
                var after = r.row() == null ? null : r.row().after();
                return Arrays.asList(
                        draft.id(),
                        draft.version(),
                        draft.name(),
                        draft.base().id(),
                        draft.base().catalogue().capturedAt(),
                        draft.selection().label(),
                        draft.base().from(),
                        draft.base().through(),
                        weight.toString(),
                        level,
                        r.name(),
                        r.before(),
                        r.after(),
                        r.delta(),
                        r.percent(),
                        r.coverage(),
                        r.row() == null ? null : r.row().articleId(),
                        before == null || before.specification() == null
                                ? null
                                : before.specification().id(),
                        before == null || before.recipe() == null ? null : before.recipe().id(),
                        before == null || before.recipe() == null
                                ? null
                                : before.recipe().version(),
                        after == null || after.recipe() == null ? null : after.recipe().id(),
                        after == null || after.recipe() == null ? null : after.recipe().version(),
                        before == null || before.model() == null ? null : before.model().code(),
                        before == null || before.model() == null ? null : before.model().version(),
                        after == null || after.model() == null ? null : after.model().code(),
                        after == null || after.model() == null ? null : after.model().version(),
                        before == null ? null : before.grams(),
                        after == null ? null : after.grams(),
                        before == null ? null : before.nominalMixPrice(),
                        after == null ? null : after.nominalMixPrice(),
                        before == null ? null : before.transformation(),
                        after == null ? null : after.transformation(),
                        draft.prices().size(),
                        draft.recipes().size(),
                        draft.models().size(),
                        draft.transforms().size());
            }
        };
    }

    static List<Display> articles(Analysis a) {
        return a.rows().stream()
                .map(
                        r ->
                                new Display(
                                        r.before().article() == null
                                                ? r.before().product().code() + " · senza articoli"
                                                : r.before().article().code(),
                                        r.before().total(),
                                        r.after().total(),
                                        r.delta(),
                                        r.percent(),
                                        r.status(),
                                        r,
                                        null))
                .toList();
    }

    record MaterialDetail(String side, ProductCostCsv.Detail detail) {}

    static List<MaterialDetail> materials(Row row) {
        var list = new ArrayList<MaterialDetail>();
        ProductCostCsv.details(List.of(row.before()))
                .forEach(d -> list.add(new MaterialDetail("BASE", d)));
        ProductCostCsv.details(List.of(row.after()))
                .forEach(d -> list.add(new MaterialDetail("SCENARIO", d)));
        return list;
    }

    static CsvExportMapper<MaterialDetail> materials(Draft draft) {
        var delegate = ProductCostCsv.detail(draft.base().catalogue().capturedAt());
        return new CsvExportMapper<>() {
            public List<String> headers() {
                var h =
                        new ArrayList<>(
                                List.of(
                                        "Scenario ID",
                                        "Scenario revisione",
                                        "Scenario",
                                        "Base ID",
                                        "Lato confronto"));
                h.addAll(delegate.headers());
                return h;
            }

            public List<?> map(MaterialDetail row) {
                var result =
                        new ArrayList<Object>(
                                Arrays.asList(
                                        draft.id(),
                                        draft.version(),
                                        draft.name(),
                                        draft.base().id(),
                                        row.side()));
                result.addAll(delegate.map(row.detail()));
                return result;
            }
        };
    }
}
