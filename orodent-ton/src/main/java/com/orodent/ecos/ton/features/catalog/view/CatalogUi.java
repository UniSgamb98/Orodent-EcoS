package com.orodent.ecos.ton.features.catalog.view;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class CatalogUi {
    private CatalogUi() {}

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static Button button(String label, String id, boolean primary) {
        Button b = new Button(label);
        b.setId(id);
        b.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        return b;
    }

    static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted-label");
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    static Label feedback(String id) {
        Label l = new Label();
        l.setId(id);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.visibleProperty().bind(l.textProperty().isNotEmpty());
        l.managedProperty().bind(l.visibleProperty());
        return l;
    }

    static TextField field(String id, String prompt) {
        TextField f = new TextField();
        f.setId(id);
        f.setPromptText(prompt);
        f.setAccessibleText(prompt);
        return f;
    }

    static VBox field(String label, Node input) {
        Label l = new Label(label);
        l.setLabelFor(input);
        return new VBox(5, l, input);
    }

    static BigDecimal decimal(String text, String label) {
        String t = text.strip();
        if (t.isEmpty()) return null;
        if (!t.matches("[0-9]+([.,][0-9]+)?"))
            throw new IllegalArgumentException(
                    label + ": usa un numero con virgola o punto decimale.");
        return new BigDecimal(t.replace(',', '.'));
    }

    static String number(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static ScrollPane scroll(Node content) {
        var s = new ScrollPane(content);
        s.setFitToWidth(true);
        s.setFitToHeight(true);
        return s;
    }

    static void visible(Node node, boolean value) {
        node.setVisible(value);
        node.setManaged(value);
    }

    static GridPane fields(Node... fields) {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(16);
        grid.setMinWidth(0);
        Runnable arrange =
                () -> {
                    int columns = grid.getWidth() >= 620 ? 2 : 1;
                    if (grid.getColumnConstraints().size() == columns) return;
                    grid.getColumnConstraints().clear();
                    grid.getChildren().clear();
                    for (int i = 0; i < columns; i++) {
                        ColumnConstraints c = new ColumnConstraints();
                        c.setPercentWidth(100.0 / columns);
                        c.setHgrow(Priority.ALWAYS);
                        grid.getColumnConstraints().add(c);
                    }
                    for (int i = 0; i < fields.length; i++) {
                        grid.add(fields[i], i % columns, i / columns);
                        GridPane.setHgrow(fields[i], Priority.ALWAYS);
                    }
                };
        grid.widthProperty().addListener((o, a, b) -> arrange.run());
        arrange.run();
        return grid;
    }
}
