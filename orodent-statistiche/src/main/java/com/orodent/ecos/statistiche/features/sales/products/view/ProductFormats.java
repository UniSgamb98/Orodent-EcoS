package com.orodent.ecos.statistiche.features.sales.products.view;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

final class ProductFormats {
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    ProductFormats() { number.setMaximumFractionDigits(3); }
    String money(BigDecimal value) { return value == null ? "—" : currency.format(value); }
    String number(BigDecimal value) { return value == null ? "—" : number.format(value); }
    String percent(BigDecimal value) { return value == null ? "Non confrontabile" : number(value) + "%"; }
    String change(BigDecimal value) {
        return value == null ? "Non confrontabile" : (value.signum() > 0 ? "↗ +" : value.signum() < 0 ? "↘ " : "→ ") + percent(value);
    }
}
