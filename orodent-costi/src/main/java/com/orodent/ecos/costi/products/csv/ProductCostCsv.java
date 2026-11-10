package com.orodent.ecos.costi.products.csv;

import com.orodent.ecos.core.csv.CsvExportMapper;
import com.orodent.ecos.costi.products.model.ProductCosts;
import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.time.Instant;
import java.util.*;

/**
 * Flat, typed Excel tables. Missing values stay empty; alternative configurations have distinct
 * IDs.
 */
public final class ProductCostCsv {
    private ProductCostCsv() {}

    public record Detail(Result result, Material material) {}

    public static CsvExportMapper<Result> summary(Instant capturedAt) {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Prodotto",
                        "Linea",
                        "Colore",
                        "Prodotto attivo",
                        "Articolo",
                        "Articolo attivo",
                        "Diametro (mm)",
                        "Altezza nominale (mm)",
                        "Revisione articolo",
                        "Configurazione ID",
                        "Configurazione versione",
                        "Predefinita",
                        "Composizione ID",
                        "Composizione versione",
                        "Modello",
                        "Modello versione",
                        "Miscela inclusa maggiorazione (g)",
                        "Fattore perdite",
                        "Costo polveri (EUR)",
                        "Trasformazione (EUR/articolo)",
                        "Costo totale (EUR/articolo)",
                        "Revisione parametri costi",
                        "Trasformazione specifica",
                        "Metodo costi",
                        "Riferimento calcolo TON",
                        "Fotografia prezzi al (UTC)",
                        "Stato",
                        "Miscela nominale (EUR/kg)");
            }

            public List<?> map(Result r) {
                Article a = r.article();
                Specification s = r.specification();
                return Arrays.asList(
                        r.product().code(),
                        r.product().lineCode(),
                        r.product().color(),
                        r.product().active(),
                        a == null ? null : a.code(),
                        a == null ? null : a.active(),
                        s == null ? a == null ? null : a.diameter() : s.diameter(),
                        s == null ? a == null ? null : a.height() : s.height(),
                        s == null ? null : s.articleRevision(),
                        s == null ? null : s.id(),
                        s == null ? null : s.version(),
                        s == null ? null : Objects.equals(a.preferredId(), s.id()),
                        r.recipe() == null ? null : r.recipe().id(),
                        r.recipe() == null ? null : r.recipe().version(),
                        r.model() == null ? null : r.model().code(),
                        r.model() == null ? null : r.model().version(),
                        r.grams(),
                        ProductCosts.LOSS_FACTOR,
                        r.powderCost(),
                        r.transformation(),
                        r.total(),
                        r.parameters().version(),
                        a != null && r.parameters().overrides().containsKey(a.id()),
                        ProductCosts.CALCULATION,
                        s == null ? null : s.productionCalculation(),
                        capturedAt,
                        r.status(),
                        r.nominalMixPrice());
            }
        };
    }

    public static List<Detail> details(List<Result> results) {
        var details = new ArrayList<Detail>();
        for (Result r : results) {
            if (r.materials().isEmpty()) details.add(new Detail(r, null));
            else for (Material m : r.materials()) details.add(new Detail(r, m));
        }
        return List.copyOf(details);
    }

    public static CsvExportMapper<Detail> detail(Instant capturedAt) {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Prodotto",
                        "Articolo",
                        "Configurazione ID",
                        "Configurazione versione",
                        "Composizione ID",
                        "Composizione versione",
                        "Modello",
                        "Modello versione",
                        "Strato",
                        "Polvere",
                        "Revisione tecnica polvere",
                        "Percentuale massa (%)",
                        "Quantità con maggiorazione (g)",
                        "Prezzo applicato (EUR/kg)",
                        "Modalità prezzo",
                        "Revisione prezzo",
                        "Data prezzo",
                        "Costo riga (EUR)",
                        "Fattore perdite",
                        "Metodo costi",
                        "Fotografia prezzi al (UTC)",
                        "Stato");
            }

            public List<?> map(Detail d) {
                Result r = d.result();
                Material m = d.material();
                Specification s = r.specification();
                PowderPrice p = m == null ? null : m.price();
                return Arrays.asList(
                        r.product().code(),
                        r.article() == null ? null : r.article().code(),
                        s == null ? null : s.id(),
                        s == null ? null : s.version(),
                        r.recipe() == null ? null : r.recipe().id(),
                        r.recipe() == null ? null : r.recipe().version(),
                        r.model() == null ? null : r.model().code(),
                        r.model() == null ? null : r.model().version(),
                        m == null ? null : m.layer(),
                        m == null ? null : m.ingredient().code(),
                        m == null ? null : m.ingredient().technicalVersion(),
                        m == null ? null : m.ingredient().percent(),
                        m == null ? null : m.grams(),
                        p == null ? null : p.price(),
                        p == null ? null : p.mode(),
                        p == null ? null : p.version(),
                        p == null ? null : p.date(),
                        m == null ? null : m.cost(),
                        ProductCosts.LOSS_FACTOR,
                        ProductCosts.CALCULATION,
                        capturedAt,
                        r.status());
            }
        };
    }
}
