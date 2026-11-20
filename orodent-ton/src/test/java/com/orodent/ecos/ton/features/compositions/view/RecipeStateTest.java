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
    void ppmParametersSurviveReopenCopyAndIncompleteWhiteInput() {
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
        state.ppmMode(0, true);
        assertEquals("1000", state.layers().getFirst().ppm().get());
        assertEquals("60", state.layers().getFirst().items().get(2).share().get());
        assertEquals("40", state.layers().getFirst().items().get(3).share().get());
        state.layers().getFirst().items().getFirst().mass().set("45");
        assertEquals(0, new BigDecimal("95").compareTo(state.layer(0).total()));
        assertFalse(state.layer(0).complete());
        state.copy(0, 1);
        state.layers().getFirst().items().get(2).share().set("60,");
        assertThrows(IllegalArgumentException.class, () -> state.layer(0));
        assertEquals("60", state.layers().get(1).items().get(2).share().get());
        assertTrue(state.fingerprint().contains("60,"));
        var saved = state.layer(1);
        state.show(List.of(saved), refs);
        assertTrue(state.layers().getFirst().ppmMode());
        assertEquals(saved, state.layer(0));
        state.layers().getFirst().ppm().set("20");
        var input = state.layer(0).chroma();
        assertEquals(4, input.parts().size());
        state.ppmMode(0, false);
        assertEquals(2, state.layer(0).ingredients().size());
        assertNull(state.layer(0).chroma());
    }
}
