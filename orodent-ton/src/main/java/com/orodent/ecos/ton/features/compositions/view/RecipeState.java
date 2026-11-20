package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.core.PowderAppearance;
import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.compositions.service.CompositionChroma;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.*;

import java.math.*;
import java.util.*;

/** Ppm/tint are editing aids; only actual ingredient percentages leave this buffer. */
public final class RecipeState {
    public final class Item {
        private final ObjectProperty<PowderRef> powder = new SimpleObjectProperty<>();
        private final StringProperty mass = new SimpleStringProperty("0"),
                share = new SimpleStringProperty("0");

        private Item(Stratum owner, PowderRef powder, String mass) {
            this.powder.set(powder);
            this.mass.set(mass);
            this.mass.addListener(
                    (o, a, b) -> {
                        if (batching) return;
                        if (chromatic()) updateTint(owner, this);
                        else if (this.powder.get() != null
                                && "GRIGIA".equals(this.powder.get().color())) calculate(owner);
                        notifyChanged();
                    });
            this.share.addListener(
                    (o, a, b) -> {
                        if (batching) return;
                        calculate(owner);
                        notifyChanged();
                    });
        }

        public ObjectProperty<PowderRef> powder() {
            return powder;
        }

        public StringProperty mass() {
            return mass;
        }

        public StringProperty share() {
            return share;
        }

        public ChromaGroup group() {
            return CompositionChroma.group(powder.get());
        }

        public boolean chromatic() {
            return powder.get() != null
                    && ("GIALLA".equals(powder.get().color())
                            || "ROSA".equals(powder.get().color()));
        }
    }

    public final class Stratum {
        private int number;
        private final StringProperty name = new SimpleStringProperty(""),
                ppm = new SimpleStringProperty("0");
        private final List<Item> items = new ArrayList<>();
        private String calculationError;

        private Stratum(int number, String name) {
            this.number = number;
            this.name.set(name);
            this.name.addListener((o, a, b) -> notifyChanged());
            ppm.addListener(
                    (o, a, b) -> {
                        if (batching) return;
                        calculate(this);
                        notifyChanged();
                    });
        }

        public int number() {
            return number;
        }

        public StringProperty name() {
            return name;
        }

        public StringProperty ppm() {
            return ppm;
        }

        public List<Item> items() {
            return Collections.unmodifiableList(items);
        }

        public String label() {
            return "Strato " + number + (name.get().isBlank() ? "" : " · " + name.get());
        }

        @Override
        public String toString() {
            return label();
        }
    }

    private final List<Stratum> layers = new ArrayList<>();
    private Runnable changed = () -> {};
    private boolean batching;

    private void notifyChanged() {
        if (!batching) changed.run();
    }

    public void onChanged(Runnable action) {
        changed = Objects.requireNonNull(action);
    }

    public List<Stratum> layers() {
        return Collections.unmodifiableList(layers);
    }

    public void show(List<Layer> values, Map<Long, PowderRef> powders) {
        layers.clear();
        batching = true;
        try {
            for (var layer : values) {
                var s = new Stratum(layer.number(), layer.name());
                for (var i : layer.ingredients())
                    s.items.add(
                            new Item(
                                    s,
                                    powders.get(i.powderRevisionId()),
                                    TechnicalUi.number(i.mass())));
                sort(s);
                try {
                    s.ppm.set(TechnicalUi.number(CompositionChroma.ppm(layer, powders)));
                } catch (IllegalArgumentException unknown) {
                    s.ppm.set("");
                }
                inferShares(s);
                layers.add(s);
            }
        } finally {
            batching = false;
        }
    }

    /** Deriving the editing aids must never round or recalculate an existing physical recipe. */
    private void inferShares(Stratum s) {
        var colored = s.items.stream().filter(Item::chromatic).toList();
        BigDecimal total =
                colored.stream()
                        .map(i -> TechnicalUi.preview(i.mass.get()))
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = BigDecimal.ZERO;
        for (int n = 0; n < colored.size(); n++) {
            var item = colored.get(n);
            var mass = TechnicalUi.preview(item.mass.get());
            BigDecimal ratio =
                    n == colored.size() - 1
                            ? new BigDecimal("100").subtract(used)
                            : total.signum() > 0
                                    ? (mass == null ? BigDecimal.ZERO : mass)
                                            .multiply(new BigDecimal("100"))
                                            .divide(total, 4, RoundingMode.DOWN)
                                    : BigDecimal.ZERO;
            item.share.set(TechnicalUi.number(ratio));
            used = used.add(ratio);
        }
    }

    private void sort(Stratum s) {
        s.items.sort(
                Comparator.comparing(
                        i -> i.powder.get() == null ? null : i.powder.get().appearance(),
                        Comparator.nullsLast(PowderAppearance.order())));
    }

    public void addLayer() {
        layers.add(new Stratum(layers.size() + 1, ""));
        notifyChanged();
    }

    public void removeLayer(int index) {
        layers.remove(index);
        renumber();
        notifyChanged();
    }

    public void moveLayer(int index, int delta) {
        Collections.swap(layers, index, index + delta);
        renumber();
        notifyChanged();
    }

    private void renumber() {
        for (int i = 0; i < layers.size(); i++) layers.get(i).number = i + 1;
    }

    public void addItem(int index) {
        var s = layers.get(index);
        s.items.add(new Item(s, null, "0"));
        notifyChanged();
    }
    public void removeItem(int index, Item item) {
        var s = layers.get(index);
        s.items.remove(item);
        calculate(s);
        notifyChanged();
    }
    public void choosePowder(int index, Item item, PowderRef powder) {
        if (powder != null) CompositionChroma.group(powder);
        var s = layers.get(index);
        batching = true;
        try {
            boolean wasChromatic = item.chromatic();
            item.powder.set(powder);
            if (item.chromatic() && !wasChromatic)
                item.share.set(
                        s.items.stream().anyMatch(i -> i != item && i.chromatic()) ? "0" : "100");
            sort(s);
        } finally {
            batching = false;
        }
        calculate(s);
        notifyChanged();
    }

    private void calculate(Stratum s) {
        try {
            var input = chroma(s);
            var result = CompositionChroma.preview(input);
            batching = true;
            try {
                for (var item : s.items)
                    if (item.chromatic())
                        item.mass.set(
                                TechnicalUi.number(
                                        result.percentages().get(item.powder.get().id())));
                s.calculationError = tintError(input);
            } finally {
                batching = false;
            }
        } catch (IllegalArgumentException invalid) {
            s.calculationError = invalid.getMessage();
        }
    }

    private String tintError(Chroma input) {
        try {
            CompositionChroma.calculate(input);
            return null;
        } catch (IllegalArgumentException invalid) {
            return invalid.getMessage();
        }
    }

    /** Inverse of the same fixed-croma rule; never changes ppm or another powder's mass. */
    private void updateTint(Stratum s, Item edited) {
        batching = true;
        try {
            BigDecimal gray = BigDecimal.ZERO;
            for (var i : s.items)
                if (i.powder.get() != null && "GRIGIA".equals(i.powder.get().color()))
                    gray = gray.add(TechnicalUi.decimal(i.mass.get(), "Percentuale grigia"));
            BigDecimal budget =
                    TechnicalUi.decimal(s.ppm.get(), "Croma dello strato")
                            .divide(new BigDecimal("20"))
                            .subtract(gray)
                            .setScale(1, RoundingMode.HALF_UP);
            BigDecimal mass = TechnicalUi.decimal(edited.mass.get(), "Percentuale sullo strato");
            if (mass.signum() < 0 || mass.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException(
                        "La percentuale deve essere compresa fra 0 e 100%.");
            if (budget.signum() <= 0) {
                if (mass.signum() > 0)
                    throw new IllegalArgumentException(
                            "Il croma impostato non consente polveri rosa o gialle.");
            } else {
                edited.share.set(
                        TechnicalUi.number(
                                mass.multiply(new BigDecimal("100"))
                                        .divide(budget, 4, RoundingMode.HALF_UP)));
                var coloredTotal =
                        s.items.stream()
                                .filter(Item::chromatic)
                                .map(i -> TechnicalUi.preview(i.mass.get()))
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (coloredTotal.compareTo(budget) == 0) inferShares(s);
            }
            s.calculationError = tintError(chroma(s));
        } catch (IllegalArgumentException invalid) {
            s.calculationError = invalid.getMessage();
        } finally {
            batching = false;
        }
    }

    public void copy(int source, int target) {
        var from = layers.get(source);
        var to = layers.get(target);
        batching = true;
        try {
            to.items.clear();
            for (var i : from.items) {
                var copy = new Item(to, i.powder.get(), i.mass.get());
                copy.share.set(i.share.get());
                to.items.add(copy);
            }
            to.ppm.set(from.ppm.get());
            to.calculationError = from.calculationError;
        } finally {
            batching = false;
        }
        notifyChanged();
    }

    public Chroma chroma(int index) {
        return chroma(layers.get(index));
    }

    private Chroma chroma(Stratum s) {
        var parts = new ArrayList<ChromaPart>();
        for (var i : s.items) {
            if (i.powder.get() == null)
                throw new IllegalArgumentException(
                        s.label() + ": seleziona una polvere approvata.");
            var group = i.group();
            parts.add(
                    new ChromaPart(
                            i.powder.get().id(),
                            group,
                            TechnicalUi.decimal(
                                    i.chromatic() ? i.share.get() : i.mass.get(),
                                    s.label()
                                            + (i.chromatic()
                                                    ? " · tinta"
                                                    : " · percentuale sullo strato"))));
        }
        return new Chroma(TechnicalUi.decimal(s.ppm.get(), s.label() + " · ppm"), parts);
    }

    public Layer layer(int index) {
        var s = layers.get(index);
        if (s.calculationError != null) throw new IllegalArgumentException(s.calculationError);
        var result = new ArrayList<Ingredient>();
        for (var i : s.items) {
            if (i.powder.get() == null)
                throw new IllegalArgumentException(
                        s.label() + ": seleziona una polvere approvata.");
            BigDecimal value = TechnicalUi.decimal(i.mass.get(), s.label() + " · percentuale");
            if (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException(
                        "La percentuale deve essere compresa fra 0 e 100%.");
            // Configured zero candidates exist only in this editing session.
            if (value.signum() > 0) result.add(new Ingredient(i.powder.get().id(), value));
        }
        result.sort(Comparator.comparingLong(Ingredient::powderRevisionId));
        return new Layer(s.number, s.name.get(), result);
    }
    public List<Layer> data() {
        var result = new ArrayList<Layer>();
        for (int i = 0; i < layers.size(); i++) result.add(layer(i));
        return result;
    }
    public Map<Long, PowderRef> powders() {
        var result = new HashMap<Long, PowderRef>();
        for (var s : layers)
            for (var i : s.items)
                if (i.powder.get() != null) result.put(i.powder.get().id(), i.powder.get());
        return result;
    }
    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        for (var s : layers) {
            result.add("LAYER:" + s.number);
            result.add(s.name.get());
            result.add(s.ppm.get());
            for (var i : s.items) {
                result.add("POWDER:" + (i.powder.get() == null ? "" : i.powder.get().id()));
                result.add(i.mass.get());
                result.add(i.share.get());
            }
        }
        return List.copyOf(result);
    }

    public void dispose() {
        changed = () -> {};
        layers.clear();
    }
}
