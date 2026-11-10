package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily receipts cannot retroactively repair a shortage before their expected delivery date. */
public final class SupplyProjection {
    private SupplyProjection() {}

    public static BigDecimal coverage(
            Series s,
            LocalDate date,
            BigDecimal stock,
            CalendarMode calendar,
            BigDecimal threshold) {
        if (stock == null || threshold == null || s.rate() == null || s.rate().signum() <= 0)
            return null;
        if (stock.compareTo(threshold) <= 0) return BigDecimal.ZERO;
        BigDecimal elapsed = BigDecimal.ZERO;
        LocalDate last = s.arrivals().isEmpty() ? date : s.arrivals().lastKey();
        for (LocalDate day = date.plusDays(1); !day.isAfter(last); day = day.plusDays(1)) {
            stock = stock.add(s.arrivals().getOrDefault(day, BigDecimal.ZERO));
            if (calendar.includes(day)) {
                BigDecimal buffer = stock.subtract(threshold);
                if (buffer.compareTo(s.rate()) <= 0)
                    return elapsed.add(Consumption.divide(buffer.max(BigDecimal.ZERO), s.rate()));
                stock = stock.subtract(s.rate());
                elapsed = elapsed.add(BigDecimal.ONE);
            }
        }
        return elapsed.add(
                Consumption.divide(stock.subtract(threshold).max(BigDecimal.ZERO), s.rate()));
    }

    public static Balance forecast(Series s, Request request) {
        return compute(s, request, request.today(), BigDecimal.ZERO, true);
    }

    public static Balance order(
            Series s, Request request, LocalDate productionDate, BigDecimal quantity) {
        if (productionDate == null
                || productionDate.isBefore(request.today())
                || productionDate.isAfter(request.today().plusDays(request.horizon())))
            throw new IllegalArgumentException(
                    "La data di produzione deve essere tra oggi e la fine della previsione.");
        return compute(s, request, productionDate, quantity, false);
    }

    private static Balance compute(
            Series s, Request request, LocalDate date, BigDecimal quantity, boolean forecast) {
        var end = request.today().plusDays(request.horizon());
        var calendar = request.calendar();
        BigDecimal current = Consumption.stockOn(s, date, calendar),
                after = current == null ? null : current.subtract(quantity);
        BigDecimal residual = forecast ? Consumption.stockOn(s, end, calendar) : after;
        Settings policy = s.powder().settings();
        BigDecimal minimum =
                policy == null
                        ? null
                        : policy.unit() == Unit.KG
                                ? policy.value()
                                : s.rate() == null ? null : policy.value().multiply(s.rate());
        BigDecimal target = policy == null ? BigDecimal.ZERO : minimum;
        BigDecimal lowest = null;
        LocalDate shortage = null;
        boolean unknown = false;
        for (LocalDate d = request.today(); !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal stock = Consumption.stockOn(s, d, calendar);
            if (stock == null) {
                unknown = true;
                continue;
            }
            if (!forecast && !d.isBefore(date)) stock = stock.subtract(quantity);
            lowest = lowest == null ? stock : lowest.min(stock);
            if (shortage == null && stock.signum() < 0) shortage = d;
        }
        BigDecimal buy =
                target == null || lowest == null || unknown
                        ? null
                        : target.subtract(lowest).max(BigDecimal.ZERO);
        if (!forecast && after != null && target != null && s.rate() == null)
            buy = target.subtract(after).max(BigDecimal.ZERO);
        return new Balance(
                s.powder(),
                s.basis(),
                current,
                forecast ? s.projected() : quantity,
                residual,
                s.rate(),
                coverage(s, date, current, calendar, BigDecimal.ZERO),
                coverage(s, forecast ? end : date, residual, calendar, BigDecimal.ZERO),
                minimum,
                buy,
                policy == null ? null : coverage(s, date, after, calendar, minimum),
                shortage);
    }
}
