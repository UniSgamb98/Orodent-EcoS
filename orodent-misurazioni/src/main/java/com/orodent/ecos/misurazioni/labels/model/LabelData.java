package com.orodent.ecos.misurazioni.labels.model;

import static com.orodent.ecos.misurazioni.labels.model.Labels.*;

import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class LabelData {
    private LabelData() {}

    public static String payload(Source source, LocalDate expiry) {
        var j = source.job();
        var data = new JsonObject();
        data.addProperty("article", j.article());
        data.addProperty("product", j.product());
        data.addProperty("name", source.name());
        data.addProperty("line", source.line());
        data.addProperty("lot", j.lot());
        data.addProperty("gtin", source.gtin() == null ? "" : source.gtin());
        data.addProperty("diameter", j.diameter().stripTrailingZeros().toPlainString());
        data.addProperty("height", j.height().stripTrailingZeros().toPlainString());
        data.addProperty(
                "temperature",
                j.finalTemperature() == null
                        ? ""
                        : j.finalTemperature().stripTrailingZeros().toPlainString());
        data.addProperty("production", j.production());
        data.addProperty("quantity", j.readings().size());
        data.addProperty("furnace", j.furnace());
        data.addProperty(
                "productionDate",
                source.produced().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.addProperty(
                "productionDateGs1",
                source.produced().format(DateTimeFormatter.ofPattern("yyMMdd")));
        data.addProperty(
                "expiryDate",
                expiry == null ? "" : expiry.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.addProperty(
                "expiryDateGs1",
                expiry == null ? "" : expiry.format(DateTimeFormatter.ofPattern("yyMMdd")));
        data.addProperty("articleRevision", source.articleRevision());
        if (expiry != null && expiry.isBefore(source.produced()))
            throw new IllegalArgumentException("La scadenza precede la produzione.");
        return data.toString();
    }
}
