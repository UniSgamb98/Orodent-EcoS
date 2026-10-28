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
}
