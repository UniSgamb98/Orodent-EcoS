package com.orodent.ecos.ton.features.catalog.bulk.view;

import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition;
import com.orodent.ecos.ton.features.catalog.bulk.model.CodeComposition.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.util.*;

/** A visual ordered list; text is not reparsed or reformatted while the user types. */
public final class CodeCompositionDialog {
    private CodeCompositionDialog() {}

    public static Optional<CodeComposition> show(
            Parent owner, CatalogKind kind, CodeComposition original, Values sample) {
        Dialog<CodeComposition> dialog = new Dialog<>();
        dialog.initOwner(owner.getScene().getWindow());
        dialog.setTitle(
                "Componi il codice " + (kind == CatalogKind.PRODUCTS ? "prodotto" : "articolo"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.setId("ton-bulk-code-dialog");
        pane.getStyleClass().add("ton-bulk-code-dialog");
        pane.getStylesheets()
                .add(
                        CodeCompositionDialog.class
                                .getResource("/ton/css/catalog-bulk.css")
                                .toExternalForm());
        pane.setPrefWidth(
                Math.max(360, Math.min(660, owner.getScene().getWindow().getWidth() - 60)));
        ButtonType apply = new ButtonType("Usa questa composizione", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(ButtonType.CANCEL, apply);
        ((Button) pane.lookupButton(ButtonType.CANCEL)).setText("Annulla");
        pane.lookupButton(apply).setId("ton-bulk-rule-apply");
        pane.lookupButton(apply).getStyleClass().add("bulk-primary");
        var draft = new ArrayList<>(original.parts());
        VBox rows = new VBox(8);
        FlowPane add = new FlowPane(6, 6);
        ComboBox<String> separator = new ComboBox<>();
        separator.getItems().setAll("", "-", "_");
        separator.setValue(original.separator());
        separator.setId("ton-bulk-rule-separator");
        separator.setConverter(
                new StringConverter<>() {
                    public String toString(String v) {
                        return v == null || v.isEmpty()
                                ? "Nessuno"
                                : v.equals("-") ? "Trattino · -" : "Trattino basso · _";
                    }

                    public String fromString(String v) {
                        return v;
                    }
                });
        Label example = new Label();
        example.setId("ton-bulk-rule-example");
        example.getStyleClass().add("bulk-code-example");
        example.setWrapText(true);
        example.setMaxWidth(Double.MAX_VALUE);
        example.setMinHeight(Region.USE_PREF_SIZE);
        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("bulk-error");
        error.setMinHeight(Region.USE_PREF_SIZE);
        Runnable preview =
                () -> {
                    try {
                        CodeComposition c = new CodeComposition(draft, separator.getValue());
                        pane.lookupButton(apply).setDisable(false);
                        try {
                            example.setText(c.code(sample));
                            error.setText("");
                        } catch (IllegalArgumentException missing) {
                            example.setText("—");
                            error.setText(missing.getMessage());
                        }
                    } catch (IllegalArgumentException invalid) {
                        example.setText("—");
                        error.setText(invalid.getMessage());
                        pane.lookupButton(apply).setDisable(true);
                    }
                };
        Runnable[] render = new Runnable[1];
        render[0] =
                () -> {
                    rows.getChildren().clear();
                    add.getChildren().clear();
                    for (int i = 0; i < draft.size(); i++) {
                        int index = i;
                        Part part = draft.get(i);
                        Label order = new Label(Integer.toString(i + 1));
                        order.getStyleClass().add("bulk-order");
                        Label title = new Label(part.type().label());
                        title.setMinWidth(76);
                        title.getStyleClass().add("bulk-part-title");
                        javafx.scene.Node value;
                        if (part.type() == Type.TEXT) {
                            TextField text = new TextField(part.text());
                            text.setId("ton-bulk-rule-text-" + i);
                            text.setPromptText("Scrivi il tuo testo");
                            text.setMinWidth(30);
                            text.textProperty()
                                    .addListener(
                                            (o, a, b) -> {
                                                draft.set(index, new Part(Type.TEXT, b));
                                                preview.run();
                                            });
                            value = text;
                        } else {
                            Label current = new Label();
                            try {
                                current.setText(CodeComposition.value(part, sample));
                            } catch (IllegalArgumentException missing) {
                                current.setText("Da compilare");
                            }
                            current.getStyleClass().add("bulk-small");
                            current.setMinWidth(0);
                            value = current;
                        }
                        HBox.setHgrow(value, Priority.ALWAYS);
                        if (value instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
                        Button up = new Button("↑"),
                                down = new Button("↓"),
                                remove = new Button("×");
                        up.setAccessibleText("Sposta " + part.type().label() + " prima");
                        down.setAccessibleText("Sposta " + part.type().label() + " dopo");
                        remove.setAccessibleText("Rimuovi " + part.type().label());
                        up.setDisable(i == 0);
                        down.setDisable(i == draft.size() - 1);
                        up.setOnAction(
                                e -> {
                                    Collections.swap(draft, index, index - 1);
                                    render[0].run();
                                });
                        down.setOnAction(
                                e -> {
                                    Collections.swap(draft, index, index + 1);
                                    render[0].run();
                                });
                        remove.setOnAction(
                                e -> {
                                    draft.remove(index);
                                    render[0].run();
                                });
                        HBox row = new HBox(8, order, title, value, up, down, remove);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.getStyleClass().add("bulk-rule-row");
                        rows.getChildren().add(row);
                    }
                    for (Type type : CodeComposition.available(kind)) {
                        Button button = new Button("+ " + type.label());
                        button.setId("ton-bulk-rule-add-" + type.name().toLowerCase(Locale.ROOT));
                        button.setDisable(
                                draft.size() >= 20
                                        || type != Type.TEXT
                                                && draft.stream().anyMatch(p -> p.type() == type));
                        button.setOnAction(
                                e -> {
                                    draft.add(new Part(type));
                                    render[0].run();
                                    if (type == Type.TEXT) {
                                        var text =
                                                rows.lookup(
                                                        "#ton-bulk-rule-text-"
                                                                + (draft.size() - 1));
                                        if (text != null) text.requestFocus();
                                    }
                                });
                        add.getChildren().add(button);
                    }
                    preview.run();
                };
        separator.valueProperty().addListener((o, a, b) -> preview.run());
        Label title = new Label(dialog.getTitle());
        title.getStyleClass().add("bulk-dialog-title");
        Label hint =
                new Label(
                        "Scegli le parti e usa le frecce per cambiarne l'ordine. I valori arrivano"
                                + " dai dati selezionati."
                                + (kind == CatalogKind.ARTICLES
                                        ? " Per ZR o ZS usa Testo fisso. Un prefisso fisso iniziale"
                                              + " già presente nel prodotto viene riportato davanti"
                                              + " al formato senza ripeterlo."
                                        : ""));
        hint.setWrapText(true);
        hint.getStyleClass().add("bulk-small");
        VBox content =
                new VBox(
                        12,
                        title,
                        hint,
                        rows,
                        new Label("Aggiungi una parte"),
                        add,
                        new Label("Separatore tra le parti"),
                        separator,
                        new Label("Anteprima del primo elemento"),
                        example,
                        error);
        content.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(520);
        scroll.getStyleClass().add("bulk-dialog-scroll");
        pane.setContent(scroll);
        render[0].run();
        dialog.setResultConverter(
                answer ->
                        answer == apply ? new CodeComposition(draft, separator.getValue()) : null);
        return dialog.showAndWait();
    }
}
