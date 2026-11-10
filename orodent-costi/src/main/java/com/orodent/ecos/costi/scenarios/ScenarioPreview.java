package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.*;

public final class ScenarioPreview extends VBox {
    public record Line(String key, String text) {
        public String toString() {
            return text;
        }
    }

    public final ComboBox<Line> line = new ComboBox<>();
    public final ComboBox<Product> product = new ComboBox<>();
    public final ComboBox<Row> article = new ComboBox<>();
    private final Label costValue = new Label(), deltaValue = new Label();
    public final Label summary = new Label("Scegli un articolo per vedere l'impatto.");
    public final Button compareLine = new Button("Confronta la linea →"),
            compareProduct = new Button("Confronta il prodotto →"),
            detail = new Button("Dettaglio per strato…"),
            find = new Button("Trova articolo per codice…");
    private Analysis analysis;
    private String selectedLine;
    private long selectedProduct;
    private Long selectedArticle;
    private Row selected;
    private boolean rendering;
    private final Map<ComboBox<?>, List<?>> all = new HashMap<>();

    public ScenarioPreview() {
        setSpacing(9);
        setStyle(
                "-fx-padding: 16; -fx-background-color: white; -fx-border-color: #dce5ed;"
                        + " -fx-border-radius: 8;");
        setPrefWidth(300);
        setMinWidth(265);
        setMaxWidth(360);
        searchable(line, Line::text);
        searchable(product, p -> p.code() + " · " + Objects.toString(p.color(), ""));
        searchable(
                article,
                r ->
                        r.before().article().code()
                                + " · Ø "
                                + edit(r.before().article().diameter())
                                + " × "
                                + edit(r.before().article().height())
                                + " mm");
        line.setId("scenarios-preview-line");
        product.setId("scenarios-preview-product");
        article.setId("scenarios-preview-article");
        costValue.setStyle("-fx-font-size: 27px; -fx-font-weight: bold; -fx-text-fill: #19758b;");
        summary.setId("scenarios-preview-summary");
        summary.setWrapText(true);
        getChildren()
                .addAll(
                        new Label("ANTEPRIMA D'IMPATTO"),
                        new Label("Linea · digita per cercare"),
                        line,
                        new Label("Prodotto"),
                        product,
                        new Label("Articolo / formato"),
                        article,
                        find,
                        new Separator(),
                        new Label("COSTO SCENARIO €/ARTICOLO"),
                        costValue,
                        deltaValue,
                        summary,
                        detail,
                        compareProduct,
                        compareLine);
        line.valueProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!rendering && a != null) {
                                selectedLine = a.key();
                                selectedProduct = 0;
                                selectedArticle = null;
                                render();
                            }
                        });
        product.valueProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!rendering && a != null) {
                                selectedProduct = a.id();
                                selectedArticle = null;
                                render();
                            }
                        });
        article.valueProperty()
                .addListener(
                        (o, b, a) -> {
                            if (!rendering && a != null) {
                                selectedArticle = a.articleId();
                                selected = a;
                                summary();
                            }
                        });
        filter(line, Line::text);
        filter(product, p -> p.code() + " " + p.color());
        filter(article, r -> r.before().article().code());
        detail.setOnAction(
                e -> {
                    if (selected != null) ScenarioDetails.show(getScene().getWindow(), selected);
                });
        find.setOnAction(e -> find());
    }

    private <T> void filter(ComboBox<T> box, java.util.function.Function<T, String> text) {
        box.getEditor()
                .setOnKeyReleased(
                        e -> {
                            if (e.getCode() == KeyCode.ENTER
                                    || e.getCode() == KeyCode.UP
                                    || e.getCode() == KeyCode.DOWN
                                    || e.getCode() == KeyCode.ESCAPE) return;
                            String query = box.getEditor().getText();
                            rendering = true;
                            try {
                                List<T> items = (List<T>) all.getOrDefault(box, List.of());
                                box.getItems()
                                        .setAll(
                                                items.stream()
                                                        .filter(
                                                                v ->
                                                                        text.apply(v)
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .contains(
                                                                                        query
                                                                                                .toLowerCase(
                                                                                                        Locale
                                                                                                                .ROOT)))
                                                        .toList());
                                box.getEditor().setText(query);
                                box.getEditor().positionCaret(query.length());
                                if (!box.getItems().isEmpty()) box.show();
                            } finally {
                                rendering = false;
                            }
                        });
    }

    public Row selected() {
        return selected;
    }

    public void show(Analysis analysis) {
        this.analysis = analysis;
        render();
    }

    private void render() {
        if (analysis == null) return;
        rendering = true;
        try {
            var lines = new TreeMap<String, Line>();
            analysis.rows()
                    .forEach(
                            r -> {
                                Product p = r.before().product();
                                lines.put(
                                        Objects.toString(p.lineId(), "none"),
                                        new Line(
                                                Objects.toString(p.lineId(), "none"),
                                                Objects.toString(p.lineCode(), "Senza linea")
                                                        + " · "
                                                        + Objects.toString(p.lineName(), "")));
                            });
            if (selectedLine == null || !lines.containsKey(selectedLine))
                selectedLine = lines.isEmpty() ? null : lines.firstKey();
            all.put(line, List.copyOf(lines.values()));
            values(
                    line,
                    List.copyOf(lines.values()),
                    selectedLine == null ? null : lines.get(selectedLine));
            var products =
                    analysis.rows().stream()
                            .map(r -> r.before().product())
                            .filter(p -> Objects.toString(p.lineId(), "none").equals(selectedLine))
                            .distinct()
                            .sorted(Comparator.comparing(Product::code))
                            .toList();
            if (products.stream().noneMatch(p -> p.id() == selectedProduct))
                selectedProduct = products.isEmpty() ? 0 : products.getFirst().id();
            all.put(product, products);
            values(
                    product,
                    products,
                    products.stream()
                            .filter(p -> p.id() == selectedProduct)
                            .findFirst()
                            .orElse(null));
            var rows =
                    analysis.rows().stream()
                            .filter(
                                    r ->
                                            r.before().product().id() == selectedProduct
                                                    && r.articleId() != null)
                            .toList();
            if (rows.stream().noneMatch(r -> Objects.equals(r.articleId(), selectedArticle)))
                selectedArticle = rows.isEmpty() ? null : rows.getFirst().articleId();
            all.put(article, rows);
            selected =
                    rows.stream()
                            .filter(r -> Objects.equals(r.articleId(), selectedArticle))
                            .findFirst()
                            .orElse(null);
            values(article, rows, selected);
            summary();
        } finally {
            rendering = false;
        }
    }

    private void summary() {
        detail.setDisable(selected == null);
        compareLine.setDisable(selected == null);
        compareProduct.setDisable(selected == null);
        if (selected == null) {
            costValue.setText("—");
            deltaValue.setText("");
            summary.setText("Nessun articolo disponibile nell'ambito selezionato.");
            return;
        }
        Row r = selected;
        costValue.setText(ScenarioDetails.n(r.after().total()));
        deltaValue.setText(
                "Δ "
                        + ScenarioDetails.n(r.delta())
                        + " € · "
                        + ScenarioDetails.n(r.percent())
                        + "%");
        deltaValue.setStyle(
                "-fx-text-fill: "
                        + (r.delta() != null && r.delta().signum() < 0 ? "#28705d" : "#a34c27")
                        + ";");
        summary.setText(
                "Mostrando "
                        + r.before().article().code()
                        + "\n\nBase €/articolo: "
                        + ScenarioDetails.n(r.before().total())
                        + "\n\nMiscela nominale €/kg\n"
                        + ScenarioDetails.n(r.before().nominalMixPrice())
                        + " → "
                        + ScenarioDetails.n(r.after().nominalMixPrice())
                        + "\nMiscela g: "
                        + ScenarioDetails.n(r.before().grams())
                        + " → "
                        + ScenarioDetails.n(r.after().grams())
                        + "\nSpessore mm: "
                        + ScenarioDetails.n(ScenarioDetails.thickness(r.before()))
                        + " → "
                        + ScenarioDetails.n(ScenarioDetails.thickness(r.after()))
                        + "\n\n"
                        + r.status());
    }

    private void find() {
        if (analysis == null) return;
        Dialog<Row> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Trova articolo nella fotografia dello scenario");
        var search = new TextField();
        search.setPromptText("Scrivi almeno 2 caratteri del codice articolo");
        var table = new TableView<Row>();
        col(table, "Linea", 100, r -> Objects.toString(r.before().product().lineCode(), "—"));
        col(table, "Prodotto", 130, r -> r.before().product().code());
        col(table, "Articolo", 230, r -> r.before().article().code());
        table.setPrefSize(600, 300);
        Label count =
                new Label("I risultati appaiono durante la ricerca, al massimo 100 per volta.");
        search.textProperty()
                .addListener(
                        (o, b, a) -> {
                            var results =
                                    a.strip().length() < 2
                                            ? List.<Row>of()
                                            : analysis.rows().stream()
                                                    .filter(
                                                            r ->
                                                                    r.articleId() != null
                                                                            && r.before()
                                                                                    .article()
                                                                                    .code()
                                                                                    .toLowerCase(
                                                                                            Locale
                                                                                                    .ROOT)
                                                                                    .contains(
                                                                                            a.strip()
                                                                                                    .toLowerCase(
                                                                                                            Locale
                                                                                                                    .ROOT)))
                                                    .toList();
                            table.getItems().setAll(results.stream().limit(100).toList());
                            count.setText(results.size() + " risultati · primi 100 visualizzati");
                        });
        dialog.getDialogPane().setContent(new VBox(10, search, count, table));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(
                b -> b == ButtonType.OK ? table.getSelectionModel().getSelectedItem() : null);
        dialog.showAndWait()
                .ifPresent(
                        r -> {
                            selectedLine = Objects.toString(r.before().product().lineId(), "none");
                            selectedProduct = r.before().product().id();
                            selectedArticle = r.articleId();
                            render();
                        });
    }
}
