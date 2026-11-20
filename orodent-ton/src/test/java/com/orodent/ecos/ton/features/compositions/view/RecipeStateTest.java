package com.orodent.ecos.ton.features.compositions.view;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class RecipeStateTest {
    @Test
    void copyingCreatesIndependentEditableRowsAndReorderingPreservesTheirContents() {
        var state = new RecipeState();
        var powder =
                new PowderRef(
                        7, 2, "P", "Polvere", "BIANCA", 3, 1, State.APPROVATA, true, Map.of());
        state.show(
                List.of(
                        new Layer(1, "Origine", List.of(new Ingredient(7, new BigDecimal("100")))),
                        new Layer(2, "Destinazione", List.of())),
                Map.of(7L, powder));
        state.copy(0, 1);
        state.layers().get(1).items().getFirst().mass().set("75,1234");
        assertEquals("100", state.layers().getFirst().items().getFirst().mass().get());
        assertEquals(7, state.layers().get(1).items().getFirst().powder().get().id());
        state.moveLayer(1, -1);
        assertEquals("Destinazione", state.layers().getFirst().name().get());
        assertEquals(1, state.layers().getFirst().number());
        assertEquals("75,1234", state.layers().getFirst().items().getFirst().mass().get());
        state.removeLayer(0);
        assertEquals(1, state.layers().getFirst().number());
        assertEquals("100", state.layers().getFirst().items().getFirst().mass().get());
        state.dispose();
    }

    @Test
    void invalidPendingTextSurvivesValidationAndEditingAnotherLayer() {
        var state = new RecipeState();
        var powder =
                new PowderRef(
                        1, 1, "P", "Polvere", "BIANCA", 3, 1, State.APPROVATA, true, Map.of());
        state.show(
                List.of(new Layer(1, "", List.of(new Ingredient(1, BigDecimal.valueOf(100))))),
                Map.of(1L, powder));
        state.layers().getFirst().items().getFirst().mass().set("75,");
        assertThrows(IllegalArgumentException.class, state::data);
        state.addLayer();
        state.addItem(1);
        assertEquals("75,", state.layers().getFirst().items().getFirst().mass().get());
        assertTrue(state.fingerprint().contains("75,"));
        state.dispose();
    }

    @Test
    void chromaIsAlwaysAvailableAndDerivedAfterReopeningActualPercentages() {
        var refs = new HashMap<Long, PowderRef>();
        for (int n = 1; n <= 4; n++)
            refs.put(
                    (long) n,
                    new PowderRef(
                            n,
                            n,
                            "P" + n,
                            "Polvere",
                            List.of("BIANCA", "GRIGIA", "GIALLA", "ROSA").get(n - 1),
                            3,
                            1,
                            State.APPROVATA,
                            true,
                            Map.of()));
        var state = new RecipeState();
        state.show(
                List.of(
                        new Layer(
                                1,
                                "",
                                List.of(
                                        new Ingredient(1, new BigDecimal("50")),
                                        new Ingredient(2, new BigDecimal("1")),
                                        new Ingredient(3, new BigDecimal("29.4")),
                                        new Ingredient(4, new BigDecimal("19.6")))),
                        new Layer(2, "", List.of())),
                refs);
        assertEquals(
                List.of("BIANCA", "GIALLA", "ROSA", "GRIGIA"),
                state.layers().getFirst().items().stream()
                        .map(i -> i.powder().get().color())
                        .toList());
        assertEquals("1000", state.layers().getFirst().ppm().get());
        assertEquals("60", state.layers().getFirst().items().get(1).share().get());
        state.layers().getFirst().ppm().set("600");
        assertEquals("17,4", state.layers().getFirst().items().get(1).mass().get());
        assertEquals("50", state.layers().getFirst().items().getFirst().mass().get());
        assertFalse(state.layer(0).complete());
        state.copy(0, 1);
        state.layers().getFirst().items().get(1).share().set("60,");
        assertThrows(IllegalArgumentException.class, () -> state.layer(0));
        assertEquals("60", state.layers().get(1).items().get(1).share().get());
        assertTrue(state.fingerprint().contains("60,"));
        var saved = state.layer(1);
        state.show(List.of(saved), refs);
        assertEquals(saved, state.layer(0));
        assertEquals("600", state.layers().getFirst().ppm().get());
        assertEquals("60", state.layers().getFirst().items().get(1).share().get());
        state.layers().getFirst().ppm().set("20");
        assertEquals(2, state.layer(0).ingredients().size());
    }

    @Test
    void editingEitherColumnKeepsTheUsersPpmFixedAndDoesNotChangeOtherPowders() {
        var state = sample();
        var layer = state.layers().getFirst();
        layer.items().get(1).mass().set("24,5");
        assertEquals("50", layer.items().get(1).share().get());
        assertEquals("19,6", layer.items().get(2).mass().get());
        assertEquals("1000", layer.ppm().get());
        assertThrows(IllegalArgumentException.class, () -> state.layer(0));
        layer.items().get(2).mass().set("24,5");
        assertEquals("50", layer.items().get(2).share().get());
        assertTrue(state.layer(0).complete());
        layer.items().get(1).share().set("70");
        assertEquals("34,3", layer.items().get(1).mass().get());
        assertEquals("1000", layer.ppm().get());
        layer.items().get(2).share().set("30");
        assertEquals("14,7", layer.items().get(2).mass().get());
        assertTrue(state.layer(0).complete());
        layer.items().get(3).mass().set("2");
        assertEquals("33,6", layer.items().get(1).mass().get());
        assertEquals("1000", layer.ppm().get());
        assertEquals("50", layer.items().getFirst().mass().get());
    }

    @Test
    void openingAndCopyingARecipeDoesNotRecalculateItsOriginalFourDecimalPercentages() {
        var state = sample();
        var refs = state.powders();
        var legacy =
                new Layer(
                        1,
                        "",
                        List.of(
                                new Ingredient(1, new BigDecimal("99.6667")),
                                new Ingredient(3, new BigDecimal("0.3333"))));
        state.show(List.of(legacy), refs);
        assertEquals(legacy, state.layer(0));
        assertEquals("6,666", state.layers().getFirst().ppm().get());
        state.addLayer();
        state.copy(0, 1);
        assertEquals(legacy.ingredients(), state.layer(1).ingredients());
    }

    @Test
    void ingredientRowsUseSupplierThenColourYClassAndAlphabeticalCode() {
        var refs = new HashMap<Long, PowderRef>();
        String[][] values = {
            {"Z", "Tosoh", "BIANCA", "4"},
            {"B", "Tosoh", "BIANCA", "3"},
            {"A", "Tosoh", "BIANCA", "3"},
            {"A", "Sinocera", "GRIGIA", "6"},
            {"A", "Tosoh", "GIALLA", "3"},
            {"A", "Tosoh", "ROSA", "3"},
            {"A", "Tosoh", "GRIGIA", "3"}
        };
        var ingredients = new ArrayList<Ingredient>();
        for (int n = 0; n < values.length; n++) {
            long id = n + 1;
            var row = values[n];
            refs.put(
                    id,
                    new PowderRef(
                            id,
                            id,
                            row[0],
                            row[0],
                            row[2],
                            Integer.valueOf(row[3]),
                            1,
                            State.APPROVATA,
                            true,
                            Map.of(),
                            row[1]));
            ingredients.add(new Ingredient(id, BigDecimal.ONE));
        }
        var state = new RecipeState();
        state.show(List.of(new Layer(1, "", ingredients)), refs);
        assertEquals(
                List.of(4L, 3L, 2L, 1L, 5L, 6L, 7L),
                state.layers().getFirst().items().stream()
                        .map(i -> i.powder().get().id())
                        .toList());
        var changed = state.layers().getFirst().items().getLast();
        state.choosePowder(
                0,
                changed,
                new PowderRef(
                        8, 8, "C", "C", "BIANCA", 5, 1, State.APPROVATA, true, Map.of(), "Tosoh"));
        assertEquals(
                List.of(4L, 3L, 2L, 1L, 8L, 5L, 6L),
                state.layers().getFirst().items().stream()
                        .map(i -> i.powder().get().id())
                        .toList());
        state.addItem(0);
        assertNull(state.layers().getFirst().items().getLast().powder().get());
    }

    private RecipeState sample() {
        var refs = new HashMap<Long, PowderRef>();
        for (int n = 1; n <= 4; n++)
            refs.put(
                    (long) n,
                    new PowderRef(
                            n,
                            n,
                            "P" + n,
                            "Polvere",
                            List.of("BIANCA", "GRIGIA", "GIALLA", "ROSA").get(n - 1),
                            3,
                            1,
                            State.APPROVATA,
                            true,
                            Map.of()));
        var state = new RecipeState();
        state.show(
                List.of(
                        new Layer(
                                1,
                                "",
                                List.of(
                                        new Ingredient(1, new BigDecimal("50")),
                                                new Ingredient(2, new BigDecimal("1")),
                                        new Ingredient(3, new BigDecimal("29.4")),
                                                new Ingredient(4, new BigDecimal("19.6"))))),
                refs);
        return state;
    }
}
