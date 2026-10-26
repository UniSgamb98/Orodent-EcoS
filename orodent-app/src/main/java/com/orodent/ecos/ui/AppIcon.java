package com.orodent.ecos.ui;

import javafx.scene.image.Image;

import java.util.Objects;

public final class AppIcon {
    private static final Image IMAGE = load();

    private AppIcon() { }

    public static Image image() {
        return IMAGE;
    }

    private static Image load() {
        Image image = new Image(Objects.requireNonNull(
                AppIcon.class.getResource("/icons/orodent-ecos.png")).toExternalForm());
        if (image.isError()) {
            throw new IllegalStateException("Impossibile caricare l'icona Orodent-EcoS", image.getException());
        }
        return image;
    }
}
