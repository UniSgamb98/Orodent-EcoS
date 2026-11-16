package com.orodent.ecos.crm;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.crm.core.country.Countries;
import com.orodent.ecos.crm.core.database.model.VersionedViewPayload;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.*;
import com.orodent.ecos.crm.feature.clienti.clienti.service.ClientiViewStateCodec;

import org.junit.jupiter.api.Test;

class CountriesTest {
    @Test
    void catalogueRecognizesImportedNamesAndKeepsAmbiguousValuesForReview() {
        assertTrue(Countries.names().size() >= 249);
        for (var value : new String[] {"IT", "ita", "Italy", "ITALIA", " Italia "})
            assertEquals("Italia", Countries.forWrite(value, null));
        assertEquals("Romania", Countries.forWrite("România", null));
        assertEquals("Regno Unito", Countries.forWrite("UK", null));
        assertTrue(Countries.code("Romnaia").isEmpty());
        assertTrue(Countries.display("Romnaia").contains("da verificare"));
        assertThrows(IllegalArgumentException.class, () -> Countries.forWrite("Romnaia", null));
        assertEquals("Romnaia", Countries.forWrite("Romnaia", "Romnaia"));
        assertNull(Countries.forWrite("", null));
    }

    @Test
    void savedCountryFilterRoundTripsAndOldSearchesRemainReadable() {
        var codec = new ClientiViewStateCodec();
        var view =
                ClientiViewState.from(
                        ClientiSearchState.initial(30).withPaese(new TextFilter("RO", "Romania")));
        assertEquals(view, codec.decode(codec.encode(view)));
        assertEquals(
                view,
                ClientiViewState.from(
                        ClientiSearchState.initial(30)
                                .withPaese(new TextFilter("RO", "Romania (non presente)"))),
                "Availability labels must not alter the saved search");
        var legacy =
                codec.decode(new VersionedViewPayload(1, "searchText=Dental\nascending=false\n"));
        assertEquals("Dental", legacy.searchText());
        assertTrue(legacy.paese().isEmptyOption());
        assertFalse(legacy.ascending());
        assertEquals("RO", view.toSearchState(30).withOffset(30).toRequest().paese());
    }
}
