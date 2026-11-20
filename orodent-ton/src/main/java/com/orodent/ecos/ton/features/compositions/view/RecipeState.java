package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.compositions.service.CompositionChroma;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.*;

import java.math.*;
import java.util.*;

/** Editing buffers retain incomplete ppm/ratio text while navigating and copying layers. */
public final class RecipeState {
    public final class Item {
        private final ObjectProperty<PowderRef> powder = new SimpleObjectProperty<>();
        private final StringProperty mass = new SimpleStringProperty(""),
                share = new SimpleStringProperty("");
        private ChromaGroup savedGroup;

        private Item(PowderRef powder, String mass) {
            this.powder.set(powder);
            this.mass.set(mass);
            this.powder.addListener((o, a, b) -> notifyChanged());
            this.mass.addListener((o, a, b) -> notifyChanged());
            this.share.addListener((o, a, b) -> notifyChanged());
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
            return savedGroup == null ? CompositionChroma.group(powder.get()) : savedGroup;
        }
    }

    public final class Stratum {
        private int number;
        private final StringProperty name = new SimpleStringProperty(""),
                ppm = new SimpleStringProperty("");
        private final List<Item> items = new ArrayList<>();
        private boolean ppmMode;

        private Stratum(int number, String name) {
            this.number = number;
            this.name.set(name);
            this.name.addListener((o, a, b) -> notifyChanged());
            ppm.addListener((o, a, b) -> notifyChanged());
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

        public boolean ppmMode() {
            return ppmMode;
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
                if (layer.chroma() == null) {
                    for (var i : layer.ingredients())
                        s.items.add(
                                new Item(
                                        powders.get(i.powderRevisionId()),
                                        TechnicalUi.number(i.mass())));
                } else {
                    s.ppmMode = true;
                    s.ppm.set(TechnicalUi.number(layer.chroma().ppm()));
                    var mass = new HashMap<Long, BigDecimal>();
                    layer.ingredients().forEach(i -> mass.put(i.powderRevisionId(), i.mass()));
                    for (var p : layer.chroma().parts()) {
                        var i =
                                new Item(
                                        powders.get(p.powderRevisionId()),
                                        TechnicalUi.number(
                                                p.group() == ChromaGroup.CROMATICA
                                                        ? mass.getOrDefault(
                                                                p.powderRevisionId(),
                                                                BigDecimal.ZERO)
                                                        : p.value()));
                        i.savedGroup = p.group();
                        i.share.set(TechnicalUi.number(p.value()));
                        s.items.add(i);
                    }
                }
                layers.add(s);
            }
        } finally {
            batching = false;
        }
    }

    public void addLayer() {
        layers.add(new Stratum(layers.size() + 1, ""));
        renumber();
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
        layers.get(index).items.add(new Item(null, layers.get(index).ppmMode ? "0" : ""));
        notifyChanged();
    }

    public void removeItem(int index, Item item) {
        layers.get(index).items.remove(item);
        notifyChanged();
    }

    public void choosePowder(int index, Item item, PowderRef powder) {
        var chosenGroup =
                layers.get(index).ppmMode && powder != null
                        ? CompositionChroma.group(powder)
                        : null;
        batching = true;
        try {
            var old = item.powder.get();
            item.powder.set(powder);
            if (layers.get(index).ppmMode && powder != null) {
                var group = chosenGroup;
                if (old == null || item.savedGroup != group) {
                    item.savedGroup = group;
                    if (group == ChromaGroup.CROMATICA) {
                        boolean other =
                                layers.get(index).items.stream()
                                        .anyMatch(
                                                i ->
                                                        i != item
                                                                && i.powder.get() != null
                                                                && i.group() == group);
                        item.share.set(other ? "0" : "100");
                    } else item.mass.set("0");
                }
            }
        } finally {
            batching = false;
        }
        notifyChanged();
    }

    public void ppmMode(int index, boolean enabled) {
        var s = layers.get(index);
        if (s.ppmMode == enabled) return;
        if (!enabled) {
            var result = layer(index);
            var positive = new HashMap<Long, BigDecimal>();
            result.ingredients().forEach(i -> positive.put(i.powderRevisionId(), i.mass()));
            batching = true;
            try {
                s.items.removeIf(
                        i -> i.powder.get() == null || !positive.containsKey(i.powder.get().id()));
                for (var i : s.items) {
                    i.mass.set(TechnicalUi.number(positive.get(i.powder.get().id())));
                    i.savedGroup = null;
                }
                s.ppmMode = false;
            } finally {
                batching = false;
            }
        } else {
            batching = true;
            try {
                try {
                    s.ppm.set(TechnicalUi.number(CompositionChroma.ppm(layer(index), powders())));
                } catch (IllegalArgumentException ignored) {
                    s.ppm.set("");
                }
                var colored = new ArrayList<Item>();
                BigDecimal total = BigDecimal.ZERO;
                for (var i : s.items)
                    if (i.powder.get() != null) {
                        i.savedGroup = CompositionChroma.group(i.powder.get());
                        if (i.group() == ChromaGroup.CROMATICA) {
                            colored.add(i);
                            var n = TechnicalUi.preview(i.mass.get());
                            if (n != null) total = total.add(n);
                        }
                    }
                BigDecimal used = BigDecimal.ZERO;
                for (int n = 0; n < colored.size(); n++) {
                    var i = colored.get(n);
                    var mass = TechnicalUi.preview(i.mass.get());
                    var share =
                            n == colored.size() - 1
                                    ? new BigDecimal("100").subtract(used)
                                    : total.signum() > 0
                                            ? (mass == null ? BigDecimal.ZERO : mass)
                                                    .multiply(new BigDecimal("100"))
                                                    .divide(total, 4, RoundingMode.DOWN)
                                            : BigDecimal.ZERO;
                    i.share.set(TechnicalUi.number(share));
                    used = used.add(share);
                }
                s.ppmMode = true;
            } finally {
                batching = false;
            }
        }
        notifyChanged();
    }

    public void copy(int source, int target) {
        var from = layers.get(source);
        var to = layers.get(target);
        batching = true;
        try {
            to.items.clear();
            for (var i : from.items) {
                var copy = new Item(i.powder.get(), i.mass.get());
                copy.share.set(i.share.get());
                copy.savedGroup = i.savedGroup;
                to.items.add(copy);
            }
            to.ppmMode = from.ppmMode;
            to.ppm.set(from.ppm.get());
        } finally {
            batching = false;
        }
        notifyChanged();
    }

    public Chroma chroma(int index) {
        var s = layers.get(index);
        var parts = new ArrayList<ChromaPart>();
        for (var i : s.items) {
            if (i.powder.get() == null)
                throw new IllegalArgumentException(
                        s.label() + ": seleziona una polvere approvata.");
            var group = i.group();
            String raw = group == ChromaGroup.CROMATICA ? i.share.get() : i.mass.get();
            parts.add(
                    new ChromaPart(
                            i.powder.get().id(),
                            group,
                            TechnicalUi.decimal(
                                    raw,
                                    s.label()
                                            + " · "
                                            + (group == ChromaGroup.CROMATICA
                                                    ? "rapporto rosa/gialle"
                                                    : "percentuale sullo strato"))));
        }
        return new Chroma(TechnicalUi.decimal(s.ppm.get(), s.label() + " · ppm"), parts);
    }

    public Layer layer(int index) {
        var s = layers.get(index);
        if (s.ppmMode) {
            var input = chroma(index);
            return new Layer(
                    s.number,
                    s.name.get(),
                    CompositionChroma.calculate(input).ingredients(),
                    input);
        }
        var result = new ArrayList<Ingredient>();
        for (var i : s.items) {
            if (i.powder.get() == null)
                throw new IllegalArgumentException(
                        s.label() + ": seleziona una polvere approvata.");
            result.add(
                    new Ingredient(
                            i.powder.get().id(),
                            TechnicalUi.decimal(i.mass.get(), s.label() + " · percentuale")));
        }
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
            result.add("CHROMA:" + s.ppmMode);
            if (s.ppmMode) result.add(s.ppm.get());
            for (var i : s.items) {
                result.add("POWDER:" + (i.powder.get() == null ? "" : i.powder.get().id()));
                result.add(i.mass.get());
                if (s.ppmMode) {
                    result.add(i.share.get());
                    result.add(Objects.toString(i.savedGroup, ""));
                }
            }
        }
        return List.copyOf(result);
    }

    public void dispose() {
        changed = () -> {};
        layers.clear();
    }
}
