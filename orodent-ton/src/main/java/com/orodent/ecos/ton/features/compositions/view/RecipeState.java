package com.orodent.ecos.ton.features.compositions.view;

import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.beans.property.*;

import java.util.*;

/** UI-only editing buffer: pending text survives validation errors and layer navigation. */
public final class RecipeState {
    public final class Item {
        private final ObjectProperty<PowderRef> powder = new SimpleObjectProperty<>();
        private final StringProperty mass = new SimpleStringProperty("");

        private Item(PowderRef powder, String mass) {
            this.powder.set(powder);
            this.mass.set(mass);
            this.powder.addListener((o, a, b) -> changed.run());
            this.mass.addListener((o, a, b) -> changed.run());
        }

        public ObjectProperty<PowderRef> powder() {
            return powder;
        }

        public StringProperty mass() {
            return mass;
        }
    }

    public final class Stratum {
        private int number;
        private final StringProperty name = new SimpleStringProperty("");
        private final List<Item> items = new ArrayList<>();

        private Stratum(int number, String name) {
            this.number = number;
            this.name.set(name);
            this.name.addListener((o, a, b) -> changed.run());
        }

        public int number() {
            return number;
        }

        public StringProperty name() {
            return name;
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

    public void onChanged(Runnable action) {
        changed = Objects.requireNonNull(action);
    }

    public List<Stratum> layers() {
        return Collections.unmodifiableList(layers);
    }

    public void show(List<Layer> values, Map<Long, PowderRef> powders) {
        layers.clear();
        for (Layer layer : values) {
            var s = new Stratum(layer.number(), layer.name());
            for (Ingredient i : layer.ingredients())
                s.items.add(
                        new Item(powders.get(i.powderRevisionId()), TechnicalUi.number(i.mass())));
            layers.add(s);
        }
    }

    public void addLayer() {
        layers.add(new Stratum(layers.size() + 1, ""));
        renumber();
        changed.run();
    }

    public void removeLayer(int index) {
        layers.remove(index);
        renumber();
        changed.run();
    }

    public void moveLayer(int index, int delta) {
        Collections.swap(layers, index, index + delta);
        renumber();
        changed.run();
    }

    private void renumber() {
        for (int i = 0; i < layers.size(); i++) layers.get(i).number = i + 1;
    }

    public void addItem(int index) {
        layers.get(index).items.add(new Item(null, ""));
        changed.run();
    }

    public void removeItem(int index, Item item) {
        layers.get(index).items.remove(item);
        changed.run();
    }

    public void copy(int source, int target) {
        var values =
                layers.get(source).items.stream()
                        .map(i -> new Item(i.powder.get(), i.mass.get()))
                        .toList();
        var destination = layers.get(target);
        destination.items.clear();
        destination.items.addAll(values);
        changed.run();
    }

    public Layer layer(int index) {
        var s = layers.get(index);
        var result = new ArrayList<Ingredient>();
        for (Item i : s.items) {
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
        for (Stratum s : layers)
            for (Item i : s.items)
                if (i.powder.get() != null) result.put(i.powder.get().id(), i.powder.get());
        return result;
    }

    public List<String> fingerprint() {
        var result = new ArrayList<String>();
        for (Stratum s : layers) {
            result.add("LAYER:" + s.number);
            result.add(s.name.get());
            for (Item i : s.items) {
                result.add("POWDER:" + (i.powder.get() == null ? "" : i.powder.get().id()));
                result.add(i.mass.get());
            }
        }
        return List.copyOf(result);
    }

    public void dispose() {
        changed = () -> {};
        layers.clear();
    }
}
