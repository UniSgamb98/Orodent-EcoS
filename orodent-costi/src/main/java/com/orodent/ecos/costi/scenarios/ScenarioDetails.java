package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.Scenarios.Row;
import com.orodent.ecos.costi.view.PriceFormats;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.*;

final class ScenarioDetails {
    private ScenarioDetails() {}

    private record Pair(
            String layer,
            String powder,
            BigDecimal before,
            BigDecimal after,
            Material b,
            Material a) {}

    static void show(Window owner, Row row) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(
                "Confronto · "
                        + (row.before().article() == null
                                ? row.before().product().code()
                                : row.before().article().code()));
        var totals =
                new Label(
                        "Base: "
                                + row.before().status()
                                + "\nScenario: "
                                + row.after().status()
                                + "\nMiscela g: "
                                + n(row.before().grams())
                                + " → "
                                + n(row.after().grams())
                                + "\nSpessore totale mm: "
                                + n(thickness(row.before()))
                                + " → "
                                + n(thickness(row.after()))
                                + "\nMiscela nominale €/kg: "
                                + n(row.before().nominalMixPrice())
                                + " → "
                                + n(row.after().nominalMixPrice())
                                + "\nPolveri €: "
                                + n(row.before().powderCost())
                                + " → "
                                + n(row.after().powderCost())
                                + "\nTrasformazione €: "
                                + n(row.before().transformation())
                                + " → "
                                + n(row.after().transformation())
                                + "\nTotale €: "
                                + n(row.before().total())
                                + " → "
                                + n(row.after().total())
                                + "\n\nComposizione: "
                                + recipe(row.before())
                                + " → "
                                + recipe(row.after())
                                + "\nModello: "
                                + model(row.before())
                                + " → "
                                + model(row.after()));
        totals.setWrapText(true);
        var materials = new TableView<Pair>();
        col(materials, "Strato", 65, Pair::layer);
        col(materials, "Polvere / revisione", 160, Pair::powder);
        col(materials, "Base g", 100, p -> n(p.before()));
        col(materials, "Scenario g", 100, p -> n(p.after()));
        col(
                materials,
                "Base €/kg",
                100,
                p -> n(p.b() == null || p.b().price() == null ? null : p.b().price().price()));
        col(
                materials,
                "Scenario €/kg",
                110,
                p -> n(p.a() == null || p.a().price() == null ? null : p.a().price().price()));
        col(materials, "Base €", 100, p -> n(p.b() == null ? null : p.b().cost()));
        col(materials, "Scenario €", 100, p -> n(p.a() == null ? null : p.a().cost()));
        record Key(int layer, long powder, int version) implements Comparable<Key> {
            public int compareTo(Key k) {
                int c = Integer.compare(layer, k.layer);
                if (c == 0) c = Long.compare(powder, k.powder);
                return c == 0 ? Integer.compare(version, k.version) : c;
            }
        }
        var b = new TreeMap<Key, Material>();
        var a = new TreeMap<Key, Material>();
        row.before()
                .materials()
                .forEach(
                        m ->
                                b.put(
                                        new Key(
                                                m.layer(),
                                                m.ingredient().powderId(),
                                                m.ingredient().technicalVersion()),
                                        m));
        row.after()
                .materials()
                .forEach(
                        m ->
                                a.put(
                                        new Key(
                                                m.layer(),
                                                m.ingredient().powderId(),
                                                m.ingredient().technicalVersion()),
                                        m));
        var keys = new TreeSet<>(b.keySet());
        keys.addAll(a.keySet());
        for (Key key : keys) {
            Material before = b.get(key), after = a.get(key), m = before == null ? after : before;
            materials
                    .getItems()
                    .add(
                            new Pair(
                                    String.valueOf(key.layer()),
                                    m.ingredient().code() + " · v" + key.version(),
                                    before == null ? null : before.grams(),
                                    after == null ? null : after.grams(),
                                    before,
                                    after));
        }
        materials.setRowFactory(
                t -> {
                    var r = new TableRow<Pair>();
                    r.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !r.isEmpty()) {
                                    var p = r.getItem();
                                    message(
                                            owner,
                                            "Provenienza dei prezzi",
                                            "BASE\n"
                                                    + (p.b() == null || p.b().price() == null
                                                            ? "Assente"
                                                            : p.b().price().details())
                                                    + "\n\nSCENARIO\n"
                                                    + (p.a() == null || p.a().price() == null
                                                            ? "Assente"
                                                            : p.a().price().details()));
                                }
                            });
                    return r;
                });
        var layers = new TableView<Pair>();
        col(layers, "Strato", 90, Pair::layer);
        col(layers, "Base g", 150, p -> n(p.before()));
        col(layers, "Scenario g", 150, p -> n(p.after()));
        var nums = new TreeSet<Integer>();
        row.before().materials().forEach(m -> nums.add(m.layer()));
        row.after().materials().forEach(m -> nums.add(m.layer()));
        for (int layer : nums)
            layers.getItems()
                    .add(
                            new Pair(
                                    String.valueOf(layer),
                                    "",
                                    sum(row.before(), layer),
                                    sum(row.after(), layer),
                                    null,
                                    null));
        TabPane tabs = new TabPane();
        for (var tab :
                List.of(
                        new Tab("Riepilogo", new ScrollPane(totals)),
                        new Tab("Per strato", layers),
                        new Tab("Polveri · doppio clic per provenienza", materials))) {
            tab.setClosable(false);
            tabs.getTabs().add(tab);
        }
        tabs.setPrefSize(980, 520);
        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    static String n(BigDecimal value) {
        return PriceFormats.number(value, 2, 4);
    }

    static BigDecimal sum(Result r, int layer) {
        return r.grams() == null
                ? null
                : r.materials().stream()
                        .filter(m -> m.layer() == layer)
                        .map(Material::grams)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal thickness(Result r) {
        if (r.grams() == null || r.specification() == null || r.model() == null) return null;
        var model = r.model();
        BigDecimal top = model.top(), bottom = model.bottom();
        for (Range range : model.ranges())
            if (r.specification().height().compareTo(range.from()) >= 0
                    && r.specification().height().compareTo(range.to()) < 0) {
                top = range.top();
                bottom = range.bottom();
                break;
            }
        return r.specification().height().add(top).add(bottom);
    }

    private static String recipe(Result r) {
        return r.recipe() == null
                ? "—"
                : "ID "
                        + r.recipe().id()
                        + " · v"
                        + r.recipe().version()
                        + " · "
                        + r.recipe().state();
    }

    private static String model(Result r) {
        return r.model() == null
                ? "—"
                : r.model().code() + " · v" + r.model().version() + " · " + r.model().state();
    }
}
