package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.service.ProductCostCalculator;

import java.math.*;
import java.time.*;
import java.util.*;

/** Reuses the versioned TON mass calculation, never economic totals or stock movements. */
public final class ConsumptionEngine {
    private final ProductCostCalculator calculator = new ProductCostCalculator();

    public Analysis analyse(Snapshot snapshot, Request request) {
        var history = new HashMap<String, TreeMap<LocalDate, BigDecimal>>();
        var invalid = EnumSet.noneOf(Basis.class);
        var warnings = new LinkedHashSet<>(snapshot.warnings());
        if (snapshot.powders().stream()
                .anyMatch(p -> p.stockDate() != null && p.stockDate().isBefore(request.today())))
            warnings.add(
                    "Le scorte datate prima di oggi sono estrapolate con il consumo medio: i"
                        + " rifornimenti già ricevuti vanno riportati nella fotografia manuale.");
        var present = EnumSet.noneOf(Basis.class);
        record Key(long article, Long specification) {}
        var cache = new HashMap<Key, Result>();
        Map<Long, Article> articles = new HashMap<>();
        snapshot.catalogue().articles().forEach(a -> articles.put(a.id(), a));
        Map<Long, Product> products = new HashMap<>();
        snapshot.catalogue().products().forEach(p -> products.put(p.id(), p));
        for (Movement movement : snapshot.movements()) {
            if (movement.date().isBefore(request.from())
                    || movement.date().isAfter(request.through())) continue;
            present.add(movement.basis());
            if (movement.quantity() == null || movement.quantity().signum() < 0) {
                invalid.add(movement.basis());
                warnings.add(movement.basis() + ": quantità non valida nel periodo.");
                continue;
            }
            Article a = articles.get(movement.articleId());
            Specification spec =
                    a == null
                            ? null
                            : snapshot
                                    .catalogue()
                                    .specifications()
                                    .getOrDefault(a.id(), List.of())
                                    .stream()
                                    .filter(
                                            s ->
                                                    Objects.equals(
                                                                    movement.specificationId()
                                                                                    == null
                                                                            ? a.preferredId()
                                                                            : movement
                                                                                    .specificationId(),
                                                                    s.id())
                                                            && (movement.specificationId() != null
                                                                    || snapshot.approved()
                                                                            .contains(s.id())))
                                    .findFirst()
                                    .orElse(null);
            Result result =
                    a == null
                            ? null
                            : cache.computeIfAbsent(
                                    new Key(a.id(), spec == null ? null : spec.id()),
                                    key ->
                                            calculator.calculate(
                                                    snapshot.catalogue(),
                                                    products.get(a.productId()),
                                                    a,
                                                    spec,
                                                    Parameters.empty()));
            if (result == null || result.grams() == null || result.materials().isEmpty()) {
                invalid.add(movement.basis());
                warnings.add(
                        movement.basis()
                                + ": articolo o configurazione non calcolabile, ID "
                                + movement.articleId());
                continue;
            }
            if (!request.calendar().includes(movement.date())) {
                invalid.add(movement.basis());
                warnings.add(
                        movement.basis()
                                + ": presenti movimenti nel fine settimana. Seleziona giorni di"
                                + " calendario.");
            }
            for (Material m : result.materials())
                history.computeIfAbsent(
                                key(m.ingredient().powderId(), movement.basis()),
                                k -> new TreeMap<>())
                        .merge(
                                movement.date(),
                                m.grams().movePointLeft(3).multiply(movement.quantity()),
                                BigDecimal::add);
        }
        long denominator = Consumption.days(request.from(), request.through(), request.calendar());
        var series = new ArrayList<Series>();
        for (Powder powder : snapshot.powders())
            for (Basis basis : List.of(Basis.PRODUZIONI, Basis.VENDITE)) {
                var observed = history.getOrDefault(key(powder.id(), basis), new TreeMap<>());
                boolean hasSource = present.contains(basis);
                boolean complete = hasSource && !invalid.contains(basis) && denominator > 0;
                BigDecimal total =
                        observed.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal rate =
                        complete
                                ? Consumption.divide(total, BigDecimal.valueOf(denominator))
                                : null;
                var daily = new TreeMap<LocalDate, BigDecimal>();
                request.from()
                        .datesUntil(request.through().plusDays(1))
                        .forEach(
                                d ->
                                        daily.put(
                                                d,
                                                hasSource
                                                        ? observed.getOrDefault(d, BigDecimal.ZERO)
                                                        : null));
                for (int n = 1; n <= request.horizon(); n++) {
                    LocalDate date = request.today().plusDays(n);
                    daily.put(
                            date,
                            rate == null
                                    ? null
                                    : request.calendar().includes(date) ? rate : BigDecimal.ZERO);
                }
                BigDecimal projected =
                        rate == null
                                ? null
                                : rate.multiply(
                                        BigDecimal.valueOf(
                                                Consumption.days(
                                                        request.today().plusDays(1),
                                                        request.today().plusDays(request.horizon()),
                                                        request.calendar())));
                var arrivals = new TreeMap<LocalDate, BigDecimal>();
                if (request.includeArrivals())
                    for (Arrival arrival : snapshot.arrivals())
                        if (arrival.powderId() == powder.id()) {
                            if (arrival.expected() == null
                                    || arrival.expected().isBefore(request.today())) {
                                warnings.add(
                                        "Ordini senza data o scaduti esclusi dalle disponibilità:"
                                                + " aggiorna la consegna prevista.");
                                continue;
                            }
                            if (!arrival.expected()
                                    .isAfter(request.today().plusDays(request.horizon())))
                                arrivals.merge(
                                        arrival.expected(), arrival.quantity(), BigDecimal::add);
                        }
                series.add(new Series(powder, basis, daily, rate, projected, complete, arrivals));
            }
        if (invalid.contains(Basis.VENDITE))
            warnings.add(
                    "Previsione vendite incompleta: nessuna autonomia affidabile fino alla"
                            + " risoluzione dei collegamenti mancanti.");
        return new Analysis(snapshot, request, series, List.copyOf(warnings));
    }

    public Map<Long, BigDecimal> plan(Snapshot snapshot, List<PlanLine> lines) {
        return calculatePlan(snapshot, lines).required();
    }

    public Planned calculatePlan(Snapshot snapshot, List<PlanLine> lines) {
        var required = new HashMap<Long, BigDecimal>();
        var details = new ArrayList<Contribution>();
        int row = 0;
        for (PlanLine line : lines) {
            row++;
            if (!snapshot.approved().contains(line.specificationId()))
                throw new IllegalArgumentException("La configurazione deve essere approvata.");
            Article a =
                    snapshot.catalogue().articles().stream()
                            .filter(v -> v.id() == line.articleId())
                            .findFirst()
                            .orElseThrow();
            Specification s =
                    snapshot.catalogue().specifications().getOrDefault(a.id(), List.of()).stream()
                            .filter(v -> v.id() == line.specificationId())
                            .findFirst()
                            .orElseThrow();
            Product p =
                    snapshot.catalogue().products().stream()
                            .filter(v -> v.id() == a.productId())
                            .findFirst()
                            .orElseThrow();
            Result r = calculator.calculate(snapshot.catalogue(), p, a, s, Parameters.empty());
            if (r.grams() == null || r.materials().isEmpty())
                throw new IllegalArgumentException("Configurazione non calcolabile: " + a.code());
            for (Material m : r.materials()) {
                BigDecimal kg =
                        m.grams().movePointLeft(3).multiply(BigDecimal.valueOf(line.quantity()));
                required.merge(m.ingredient().powderId(), kg, BigDecimal::add);
                details.add(
                        new Contribution(
                                row, a.code(), s.id(), m.layer(), m.ingredient().code(), kg));
            }
        }
        return new Planned(required, details);
    }

    private static String key(long powder, Basis basis) {
        return powder + ":" + basis;
    }
}
