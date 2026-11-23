package com.orodent.ecos.misurazioni.view;

import static com.orodent.ecos.misurazioni.model.Instrument.*;

import com.orodent.ecos.misurazioni.model.Instrument;
import com.orodent.ecos.misurazioni.model.MeasurementBatch;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class MeasurementsView extends BorderPane {
    public final Button home = button("⌂ Home Orodent-EcoS", "measurements-home"),
            reset = button("Reset", "measurements-reset"),
            acquire = button("Acquisisci da\nmicrometro", "measurements-acquire"),
            swap = button("Cambia strumento", "measurements-swap"),
            copy = button("Copia su appunti", "measurements-copy");
    public final ListView<MeasurementBatch.Measurement> list = new ListView<>();
    public final Label feedback = new Label();
    private final Map<Instrument, Button> connections = new EnumMap<>(Instrument.class);
    private final Map<Instrument, Circle> lights = new EnumMap<>(Instrument.class);
    private final Image micrometer = image("eyeIcon.png"), scale = image("scaleIcon.png");
    private final ImageView instrumentImage = new ImageView(micrometer);
    private BiConsumer<Integer, Instrument> remove = (index, instrument) -> {};

    public MeasurementsView() {
        setId("misurazioni-module");
        getStylesheets()
                .add(getClass().getResource("/misurazioni/measurements.css").toExternalForm());
        Label title = new Label("Misurazioni");
        title.getStyleClass().add("measurements-title");
        HBox header = new HBox(22, home, title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("measurements-header");
        HBox tools = new HBox(14, reset, connection(MICROMETER), connection(SCALE));
        tools.setAlignment(Pos.CENTER_LEFT);
        tools.setPadding(new Insets(14, 18, 8, 18));
        setTop(new VBox(header, tools));

        instrumentImage.setFitWidth(120);
        instrumentImage.setPreserveRatio(true);
        acquire.setMaxWidth(Double.MAX_VALUE);
        swap.setMaxWidth(Double.MAX_VALUE);
        Label pedal = new Label("Pedale / Invio\nBreve: acquisisci\nLungo: cambia strumento");
        pedal.getStyleClass().add("measurements-hint");
        VBox instrument = new VBox(18, acquire, instrumentImage, swap, pedal);
        instrument.setAlignment(Pos.CENTER);
        instrument.setPadding(new Insets(18));
        instrument.setMinWidth(190);
        instrument.setPrefWidth(205);

        list.setId("measurements-list");
        list.setMinWidth(300);
        list.setPlaceholder(new Label("Acquisisci la prima misura."));
        list.setCellFactory(v -> measurementCell());
        Region rowNumber = new Region(), removeColumn = new Region(), lastColumn = new Region();
        rowNumber.setMinWidth(34);
        removeColumn.setMinWidth(28);
        lastColumn.setMinWidth(28);
        Label micrometerCaption = new Label("Micrometro"), scaleCaption = new Label("Bilancia · g");
        micrometerCaption.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(micrometerCaption, Priority.ALWAYS);
        scaleCaption.setMinWidth(85);
        scaleCaption.setAlignment(Pos.CENTER_RIGHT);
        HBox caption =
                new HBox(10, rowNumber, removeColumn, micrometerCaption, scaleCaption, lastColumn);
        caption.getStyleClass().add("measurements-columns");
        VBox readings = new VBox(8, caption, list);
        readings.setPadding(new Insets(8, 18, 8, 0));
        VBox.setVgrow(list, Priority.ALWAYS);
        HBox body = new HBox(instrument, readings);
        HBox.setHgrow(readings, Priority.ALWAYS);
        setCenter(body);

        feedback.setId("measurements-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(24);
        copy.getStyleClass().add("measurements-copy");
        copy.setMaxWidth(Double.MAX_VALUE);
        VBox bottom = new VBox(8, feedback, copy);
        bottom.setPadding(new Insets(4, 18, 18, 18));
        setBottom(bottom);
    }

    private Button connection(Instrument instrument) {
        Circle light = new Circle(5, Color.web("#c94747"));
        lights.put(instrument, light);
        Button button =
                button(
                        instrument.label(),
                        "measurements-connect-"
                                + instrument.name().toLowerCase(java.util.Locale.ROOT));
        button.setGraphic(light);
        button.getStyleClass().add("measurements-connection");
        button.setTooltip(
                new Tooltip(
                        "Clic per collegare o scollegare "
                                + instrument.label().toLowerCase(java.util.Locale.ROOT)));
        connections.put(instrument, button);
        return button;
    }

    public Button connectionButton(Instrument instrument) {
        return connections.get(instrument);
    }

    public void connectionState(Instrument instrument, boolean connected, boolean busy) {
        lights.get(instrument)
                .setFill(Color.web(busy ? "#d79b27" : connected ? "#43a05b" : "#c94747"));
        connections.get(instrument).setDisable(busy);
        connections
                .get(instrument)
                .setAccessibleText(
                        instrument.label()
                                + (busy
                                        ? ": operazione in corso"
                                        : connected ? ": collegato" : ": scollegato"));
    }

    public void active(Instrument instrument, boolean ready) {
        acquire.setText("Acquisisci da\n" + instrument.label().toLowerCase(java.util.Locale.ROOT));
        instrumentImage.setImage(instrument == MICROMETER ? micrometer : scale);
        acquire.setDisable(!ready);
    }

    public void onRemove(BiConsumer<Integer, Instrument> action) {
        remove = action;
    }

    private ListCell<MeasurementBatch.Measurement> measurementCell() {
        return new ListCell<>() {
            private final Label number = new Label(), micrometer = new Label(), scale = new Label();
            private final Button removeMicrometer = button("×", ""), removeScale = button("×", "");
            private final HBox content =
                    new HBox(10, number, removeMicrometer, micrometer, scale, removeScale);

            {
                content.setAlignment(Pos.CENTER_LEFT);
                number.setMinWidth(34);
                micrometer.setMinWidth(100);
                micrometer.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(micrometer, Priority.ALWAYS);
                scale.setMinWidth(85);
                scale.setAlignment(Pos.CENTER_RIGHT);
                removeMicrometer.getStyleClass().add("measurements-remove");
                removeScale.getStyleClass().add("measurements-remove");
                removeMicrometer.setAccessibleText("Cancella misura micrometro");
                removeScale.setAccessibleText("Cancella peso bilancia");
                removeMicrometer.setOnAction(
                        e -> {
                            if (!isEmpty()) remove.accept(getIndex(), MICROMETER);
                        });
                removeScale.setOnAction(
                        e -> {
                            if (!isEmpty()) remove.accept(getIndex(), SCALE);
                        });
            }

            @Override
            protected void updateItem(MeasurementBatch.Measurement item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                number.setText((getIndex() + 1) + ".");
                micrometer.setText(item.micrometer());
                scale.setText(item.scale());
                setGraphic(content);
            }
        };
    }

    private static Button button(String text, String id) {
        var button = new Button(text);
        if (!id.isEmpty()) button.setId(id);
        return button;
    }

    private static Image image(String name) {
        return new Image(
                MeasurementsView.class.getResource("/misurazioni/images/" + name).toExternalForm());
    }
}
