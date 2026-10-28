package com.orodent.ecos.ton.navigation;

import javafx.beans.property.ReadOnlyBooleanProperty;

/** Lifecycle shared by editable TON pages; reads can close, writes must finish. */
public interface FeatureController {
    ReadOnlyBooleanProperty savingProperty();

    boolean canLeave();

    void dispose();
}
