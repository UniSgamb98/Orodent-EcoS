package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.ProductAttributes;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductFamily;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Strict recognition keeps unrelated products out of the family ranking without deleting sales. */
public final class ProductCodeParser {
    private static final Pattern CODE = Pattern.compile(
            "^(ZR|ZS)(95|98)(10|12|14|16|18|20|22|25|30)(UMS|MST|MS|G1200|T4|H|T)(A35|A3\\.5|A[0-4]|B[1-3]|C[1-3]|D[23])?$");

    public Optional<ProductAttributes> parse(String code) {
        if (code == null) return Optional.empty();
        var match = CODE.matcher(code.strip().toUpperCase(Locale.ROOT));
        if (!match.matches()) return Optional.empty();
        String color = match.group(5) == null ? "" : match.group(5).replace("A35", "A3.5");
        ProductFamily family = match.group(4).equals("T") && !color.isEmpty()
                ? ProductFamily.T_COLORED : ProductFamily.valueOf(match.group(4));
        if (family.colored() != !color.isEmpty()) return Optional.empty();
        return Optional.of(new ProductAttributes(match.group(1), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)), family, color));
    }
}
