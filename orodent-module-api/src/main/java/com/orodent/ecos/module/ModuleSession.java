package com.orodent.ecos.module;

import javafx.scene.Parent;

/** Owns only a module's UI callbacks and resources; all methods run on the JavaFX thread. */
public interface ModuleSession extends AutoCloseable {
    Parent root();

    /** Detach listeners and invalidate pending UI work when leaving the module. */
    @Override
    default void close() { }
}
