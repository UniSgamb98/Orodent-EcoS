package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.core.csv.CsvExportMapper;
import com.orodent.ecos.costi.consumption.Consumption.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class ConsumptionCsv {
    private ConsumptionCsv() {}

    public static CsvExportMapper<Contribution> contributions() {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Riga piano",
                        "Articolo",
                        "Configurazione ID",
                        "Strato",
                        "Polvere",
                        "Necessari (kg)",
                        "Metodo");
            }

            public List<?> map(Contribution c) {
                return List.of(
                        c.row(),
                        c.article(),
                        c.specification(),
                        c.layer(),
                        c.powder(),
                        c.kg(),
                        "COSTI-NOMINALE-1");
            }
        };
    }

    public static CsvExportMapper<Balance> balances(Analysis analysis, String scope) {
        return balances(analysis, scope, null);
    }

    public static CsvExportMapper<Balance> balances(
            Analysis analysis, String scope, LocalDate productionDate) {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Ambito",
                        "Polvere",
                        "Colore",
                        "Base",
                        "Disponibilità dichiarata (kg)",
                        "Data dichiarazione",
                        "Disponibilità stimata oggi (kg)",
                        "Necessaria (kg)",
                        "Residuo stimato (kg)",
                        "Media (kg/giorno)",
                        "Copertura prima (giorni)",
                        "Copertura dopo (giorni)",
                        "Scorta minima (kg)",
                        "Da acquistare (kg)",
                        "Giorni alla scorta minima",
                        "Calendario",
                        "Periodo dal",
                        "Periodo al",
                        "Arrivi previsti inclusi",
                        "Prima carenza",
                        "Data produzione simulata");
            }

            public List<?> map(Balance b) {
                return Arrays.asList(
                        scope,
                        b.powder().code(),
                        b.powder().color(),
                        b.basis().name(),
                        b.powder().stock(),
                        b.powder().stockDate(),
                        b.current(),
                        b.required(),
                        b.residual(),
                        b.rate(),
                        b.beforeDays(),
                        b.afterDays(),
                        b.minimumKg(),
                        b.buy(),
                        b.daysToMinimum(),
                        analysis.request().calendar().toString(),
                        analysis.request().from(),
                        analysis.request().through(),
                        analysis.request().includeArrivals(),
                        b.firstShortage(),
                        productionDate);
            }
        };
    }

    public record Point(Series series, LocalDate date, BigDecimal consumed, BigDecimal available) {}

    public static List<Point> points(Analysis a, List<Series> visible) {
        var result = new ArrayList<Point>();
        for (Series s : visible)
            s.daily()
                    .forEach(
                            (d, v) ->
                                    result.add(
                                            new Point(
                                                    s,
                                                    d,
                                                    v,
                                                    Consumption.stockOn(
                                                            s, d, a.request().calendar()))));
        return List.copyOf(result);
    }

    public static CsvExportMapper<Point> points() {
        return new CsvExportMapper<>() {
            public List<String> headers() {
                return List.of(
                        "Polvere",
                        "Colore",
                        "Base",
                        "Data",
                        "Consumo (kg)",
                        "Disponibilità stimata (kg)",
                        "Data disponibilità dichiarata",
                        "Previsione completa");
            }

            public List<?> map(Point p) {
                return Arrays.asList(
                        p.series().powder().code(),
                        p.series().powder().color(),
                        p.series().basis().name(),
                        p.date(),
                        p.consumed(),
                        p.available(),
                        p.series().powder().stockDate(),
                        p.series().complete());
            }
        };
    }
}
