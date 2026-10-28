package com.orodent.ecos.ton.features.technical.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.function.Function;

public final class TechnicalUi {
    private TechnicalUi() {}

    public static Button button(String text, String id, boolean primary) {
        var b = new Button(text);
        b.setId(id);
        b.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        return b;
    }

    public static Label label(String text, String style) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setMinWidth(0);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.getStyleClass().add(style);
        return l;
    }

    public static TextField input(String id, String prompt) {
        var t = new TextField();
        t.setId(id);
        t.setPromptText(prompt);
        t.setAccessibleText(prompt);
        t.setMaxWidth(Double.MAX_VALUE);
        return t;
    }

    public static VBox field(String name, Node input) {
        var l = new Label(name);
        l.setWrapText(true);
        l.setMinWidth(0);
        l.setLabelFor(input);
        return new VBox(7, l, input);
    }

    public static VBox panel(String title, String description, Node... nodes) {
        var box = new VBox(15);
        box.setMinWidth(0);
        box.getStyleClass().add("ton-technical-panel");
        box.getChildren()
                .addAll(
                        label(title, "ton-technical-panel-title"),
                        label(description, "muted-label"));
        box.getChildren().addAll(nodes);
        return box;
    }

    public static ScrollPane scroll(Node content) {
        var s = new ScrollPane(content);
        s.setFitToWidth(true);
        return s;
    }

    public static void visible(Node n, boolean show) {
        n.setVisible(show);
        n.setManaged(show);
    }

    public static String number(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static BigDecimal decimal(String text, String label) {
        String s = text.strip();
        if (s.isEmpty()) throw new IllegalArgumentException(label + ": inserisci un valore.");
        if (!s.matches("[0-9]+([.,][0-9]+)?"))
            throw new IllegalArgumentException(
                    label + ": usa un numero con virgola o punto decimale.");
        return new BigDecimal(s.replace(',', '.'));
    }

    public static BigDecimal preview(String text) {
        try {
            return decimal(text, "");
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static <T> void column(
            TableView<T> table, String title, double width, Function<T, String> format) {
        var c = new TableColumn<T, String>(title);
        c.setCellValueFactory(v -> new SimpleStringProperty(format.apply(v.getValue())));
        c.setPrefWidth(width);
        c.setSortable(false);
        table.getColumns().add(c);
    }

    public static GridPane fields(Node... nodes) {
        var g = new GridPane();
        g.setHgap(20);
        g.setVgap(18);
        g.setMinWidth(0);
        Runnable layout =
                () -> {
                    int cols = g.getWidth() >= 420 ? 2 : 1;
                    if (g.getColumnConstraints().size() == cols) return;
                    g.getChildren().clear();
                    g.getColumnConstraints().clear();
                    for (int i = 0; i < cols; i++) {
                        var c = new ColumnConstraints();
                        c.setPercentWidth(100.0 / cols);
                        c.setHgrow(Priority.ALWAYS);
                        g.getColumnConstraints().add(c);
                    }
                    for (int i = 0; i < nodes.length; i++) g.add(nodes[i], i % cols, i / cols);
                };
        g.widthProperty().addListener((o, a, b) -> layout.run());
        layout.run();
        return g;
    }
}
