package com.orodent.ecos.ton.features.production.service;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import com.google.gson.Gson;

import java.math.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/** Data only: the user's approved visual template determines layout and blocks. */
public final class ProductionDocumentData {
    private ProductionDocumentData() {}

    public static String payload(Plan p, String number) {
        var data = new LinkedHashMap<String, Object>();
        var reference = p.reference();
        data.put("production", number);
        data.put(
                "date",
                java.time.LocalDate.parse(p.date())
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("line", p.product().lineCode() + " · " + p.product().lineName());
        data.put("product", p.product().toString());
        data.put("notes", p.notes());
        data.put("quantity", p.count());
        data.put("needed", number(p.needed()));
        data.put("prepared", number(p.prepared()));
        data.put("extra", number(p.prepared().subtract(p.needed())));
        data.put(
                "recipe",
                reference == null
                        ? "—"
                        : p.product().code() + " · v" + reference.compositionVersion());
        data.put(
                "model",
                reference == null
                        ? "—"
                        : reference.modelCode() + " · v" + reference.modelVersion());
        data.put("machine", "");
        data.put("pressureLabel", "PRESSIONE SUL MATERIALE");
        data.put("pressureUnit", "kg/cm²");
        data.put("pressure", reference == null ? "—" : number(reference.pressure()));
        data.put(
                "coefficient",
                reference == null
                        ? "—"
                        : number(
                                reference
                                        .weight()
                                        .divide(reference.thickness(), 10, RoundingMode.HALF_UP)));
        var formats = new ArrayList<Map<String, Object>>();
        for (var f : p.formats()) {
            var a = f.article();
            var value = new LinkedHashMap<String, Object>();
            value.put("code", a.code());
            value.put("nominal", number(a.height()));
            value.put("thickness", number(a.thickness()));
            value.put("diameter", number(a.diameter()));
            value.put("count", f.quantity());
            value.put("weight", number(a.weight()));
            value.put("batchWeight", number(a.weight().multiply(BigDecimal.valueOf(f.quantity()))));
            value.put(
                    "layers",
                    a.doses().stream()
                            .map(
                                    d ->
                                            Map.of(
                                                    "number",
                                                    d.number(),
                                                    "weight",
                                                    number(d.grams()),
                                                    "total",
                                                    number(
                                                            d.grams()
                                                                    .multiply(
                                                                            BigDecimal.valueOf(
                                                                                    f.quantity()))),
                                                    "ppm",
                                                    "—"))
                            .toList());
            formats.add(value);
        }
        data.put("formats", formats);
        data.put(
                "layers",
                p.mixtures().stream()
                        .map(
                                m -> {
                                    var v = new LinkedHashMap<String, Object>();
                                    v.put("number", m.number());
                                    v.put("count", m.count());
                                    v.put("needed", number(m.needed()));
                                    v.put("total", number(m.prepared()));
                                    v.put(
                                            "powders",
                                            m.powders().stream()
                                                    .map(
                                                            w ->
                                                                    Map.of(
                                                                            "code",
                                                                            w.code(),
                                                                            "name",
                                                                            w.name(),
                                                                            "percentage",
                                                                            percent(w.percentage())
                                                                                    + "%",
                                                                            "weight",
                                                                            number(w.grams())))
                                                    .toList());
                                    return v;
                                })
                        .toList());
        return new Gson().toJson(data);
    }

    public static String number(BigDecimal value) {
        if (value == null) return "—";
        var f = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.ITALY));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value);
    }

    public static String percent(BigDecimal value) {
        var f = new DecimalFormat("0.####", DecimalFormatSymbols.getInstance(Locale.ITALY));
        return f.format(value);
    }
}
