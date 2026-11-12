package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionPreview;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionType;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.TimelineFilter;

import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class ClienteTimelineSection extends VBox {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final double EDIT_NEXT_CALL_PICKER_PREF_WIDTH = 170;
    private static final double EDIT_NEXT_CALL_PICKER_MAX_WIDTH = 190;
    private static final double DELETE_INTERACTION_BUTTON_WIDTH = 36;
    private static final double EDIT_INTERACTION_TEXT_AREA_MAX_WIDTH = 760;
    private static final double EDIT_INTERACTION_TEXT_AREA_MIN_HEIGHT = 160;
    private static final int EDIT_INTERACTION_TEXT_AREA_PREF_ROWS = 5;

    private final Button newNoteButton;
    private final Button newCallButton;
    private final Button allFilterButton;
    private final Button notesFilterButton;
    private final Button callsFilterButton;
    private final VBox timelineList;
    private final ScrollPane historyScroll;
    private final Label activityCount = new Label();
    private final java.util.Set<java.util.UUID> expandedActivities = new java.util.HashSet<>();
    private final VBox importedNotes = new VBox(10);
    private final TitledPane importedDisclosure =
            new TitledPane("Informazioni importate CRM2", importedNotes);
    private final VBox noteEditor;
    private final DatePicker nextCallDatePicker;
    private final ChoiceBox<CallOutcome> callOutcomeChoiceBox;
    private final TextArea noteTextArea;
    private final Button saveNoteButton;
    private final Button cancelNoteButton;
    private final List<TimelineEditField> timelineEditFields = new ArrayList<>();

    ClienteTimelineSection() {
        super(12);
        getStyleClass().add("new-client-section");
        Label title = new Label("Attività e conversazioni");
        title.getStyleClass().add("new-client-section-title");

        newNoteButton = new Button("+ Scrivi nota");
        newNoteButton.getStyleClass().add("clients-filter-button");
        newCallButton = new Button("+ Registra chiamata");
        newCallButton.getStyleClass().add("clients-primary-button");
        allFilterButton = createTimelineFilterButton("Tutti");
        notesFilterButton = createTimelineFilterButton("Solo note");
        callsFilterButton = createTimelineFilterButton("Solo chiamate");
        timelineList = new VBox(0);
        timelineList.getStyleClass().add("crm-activity-list");
        importedDisclosure.setExpanded(false);
        importedDisclosure.setAnimated(false);
        importedDisclosure.getStyleClass().add("client-profile-disclosure");
        noteEditor = createNoteEditor();
        nextCallDatePicker = new DatePicker();
        nextCallDatePicker.setPromptText("Prossima chiamata");
        nextCallDatePicker.getStyleClass().add("client-profile-call-date-picker");
        callOutcomeChoiceBox = createCallOutcomeChoiceBox(null);
        noteTextArea = new TextArea();
        noteTextArea.setWrapText(true);
        noteTextArea.setPromptText("Scrivi una nota sulla comunicazione con il cliente...");
        noteTextArea.getStyleClass().add("client-profile-note-area");
        saveNoteButton = new Button("Salva");
        saveNoteButton.getStyleClass().add("clients-primary-button");
        cancelNoteButton = new Button("Annulla");
        cancelNoteButton.getStyleClass().add("clients-filter-button");
        HBox callDetails = new HBox(10, callOutcomeChoiceBox, nextCallDatePicker);
        callDetails.getStyleClass().add("client-profile-call-details");
        noteEditor.getChildren().addAll(callDetails, noteTextArea, createNoteActions());
        setActiveTimelineFilter(TimelineFilter.ALL);
        hideNoteEditor();

        activityCount.getStyleClass().add("crm-activity-count");
        BorderPane toolbar = new BorderPane();
        toolbar.setCenter(new VBox(3, title, activityCount));
        toolbar.setRight(createActions());
        toolbar.getStyleClass().add("crm-activity-toolbar");
        VBox scrollContent = new VBox(10, noteEditor, timelineList, importedDisclosure);
        historyScroll = new ScrollPane(scrollContent);
        historyScroll.setId("crm-history-scroll");
        historyScroll.setFitToWidth(true);
        historyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        historyScroll.setMinHeight(0);
        historyScroll.getStyleClass().add("client-profile-timeline-scroll");
        VBox.setVgrow(historyScroll, Priority.ALWAYS);
        getChildren().addAll(toolbar, createFilters(), historyScroll);
        setMinHeight(0);
        getStyleClass().add("crm-activity-panel");
    }

    void render(List<InteractionPreview> interactions) {
        timelineList.getChildren().clear();
        importedNotes.getChildren().clear();
        long count = interactions.stream().filter(i -> !isImportedSummary(i)).count();
        long calls =
                interactions.stream().filter(i -> i.type() == InteractionType.CHIAMATA).count();
        activityCount.setText(count + " attività · " + calls + " chiamate");
        for (InteractionPreview interaction : interactions) {
            if (isImportedSummary(interaction)) {
                VBox source = new VBox(8, createInfoLabel(interaction.testo()));
                importedNotes.getChildren().add(source);
            } else timelineList.getChildren().add(createActivity(interaction));
        }
        if (count == 0)
            timelineList.getChildren().add(createInfoLabel("Nessuna attività in questa vista."));
        boolean hasImported = !importedNotes.getChildren().isEmpty();
        importedDisclosure.setText(
                "Informazioni importate CRM2 · " + importedNotes.getChildren().size());
        importedDisclosure.setVisible(hasImported);
        importedDisclosure.setManaged(hasImported);
    }

    private boolean isImportedSummary(InteractionPreview interaction) {
        return interaction.type() == InteractionType.NOTA
                && interaction.testo() != null
                && interaction
                        .testo()
                        .replace("\r\n", "\n")
                        .startsWith("Riepilogo originale CRM2\n");
    }

    private VBox createActivity(InteractionPreview interaction) {
        String original = interaction.testo() == null ? "" : interaction.testo();
        CallText parsed = callText(interaction.type(), original);
        VBox card = new VBox(0);
        card.getStyleClass()
                .addAll(
                        "crm-activity",
                        interaction.type() == InteractionType.NOTA
                                ? "crm-activity-note"
                                : "crm-activity-call");
        FlowPane heading =
                createTimelineHeader(
                        interaction.data(),
                        interaction.createdAt(),
                        interaction.type(),
                        false,
                        interaction.esito());
        if (interaction.type() == InteractionType.CHIAMATA) heading.getChildren().removeFirst();
        else {
            var type = heading.getChildren().removeFirst();
            heading.getChildren().add(1, type);
        }
        if (interaction.esito() == CallOutcome.NON_TROVATO)
            heading.getStyleClass().add("crm-activity-unanswered");
        Label arrow = new Label("⌄");
        arrow.getStyleClass().add("crm-activity-arrow");
        HBox trailing = new HBox(12);
        trailing.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        if (interaction.prossimoContatto() != null) {
            Label recall =
                    new Label("Richiamo " + DATE_FORMATTER.format(interaction.prossimoContatto()));
            recall.getStyleClass().add("crm-activity-recall");
            if (interaction.prossimoContatto().isBefore(LocalDate.now()))
                recall.getStyleClass().add("crm-activity-recall-overdue");
            trailing.getChildren().add(recall);
        }
        trailing.getChildren().add(arrow);
        BorderPane graphic = new BorderPane();
        graphic.setCenter(heading);
        graphic.setRight(trailing);
        BorderPane.setMargin(trailing, new javafx.geometry.Insets(0, 0, 0, 12));
        Button toggle = new Button();
        toggle.setGraphic(graphic);
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setMinWidth(0);
        toggle.setMinHeight(54);
        toggle.setPrefHeight(54);
        toggle.setMaxHeight(54);
        toggle.getStyleClass().add("crm-activity-toggle");
        toggle.setAccessibleText(
                interaction.type().label()
                        + " · "
                        + (interaction.data() == null
                                ? "Data non indicata"
                                : DATE_FORMATTER.format(interaction.data()))
                        + (interaction.esito() == null ? "" : " · " + interaction.esito().label()));
        graphic.prefWidthProperty().bind(toggle.widthProperty().subtract(24));
        Label preview = createInfoLabel(previewText(parsed.body()));
        preview.getStyleClass().add("crm-activity-preview");
        preview.setMaxHeight(48);
        preview.setOnMouseClicked(e -> toggle.fire());
        VBox details = new VBox(10);
        details.getStyleClass().add("crm-activity-details");
        Label full =
                createInfoLabel(
                        parsed.body().isBlank()
                                ? "Nessun testo di conversazione registrato."
                                : parsed.body());
        full.getStyleClass().add("client-profile-conversation");
        details.getChildren().add(full);
        if (parsed.legacy()) {
            TitledPane source =
                    new TitledPane("Dettagli originali CRM2", createInfoLabel(original));
            source.setExpanded(parsed.body().isBlank());
            source.setAnimated(false);
            source.getStyleClass().add("client-profile-disclosure");
            details.getChildren().add(source);
        }
        java.util.UUID key =
                interaction.interazioneId() != null
                        ? interaction.interazioneId()
                        : interaction.notaId();
        Runnable refresh =
                () -> {
                    boolean expanded = key != null && expandedActivities.contains(key);
                    details.setVisible(expanded);
                    details.setManaged(expanded);
                    boolean showPreview = !expanded && !parsed.body().isBlank();
                    preview.setVisible(showPreview);
                    preview.setManaged(showPreview);
                    arrow.setText(expanded ? "⌃" : "⌄");
                    toggle.setAccessibleHelp(expanded ? "Comprimi attività" : "Espandi attività");
                    card.pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("expanded"), expanded);
                };
        toggle.setOnAction(
                e -> {
                    if (key != null && !expandedActivities.add(key)) expandedActivities.remove(key);
                    refresh.run();
                });
        card.getChildren().addAll(toggle, preview, details);
        refresh.run();
        return card;
    }

    private String previewText(String text) {
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 230 ? oneLine : oneLine.substring(0, 230) + "…";
    }

    /** Interpret only the recognizable CRM2 prefix; the complete original remains available. */
    private CallText callText(InteractionType type, String original) {
        String text = original.replace("\r\n", "\n");
        if (type != InteractionType.CHIAMATA
                || !(text.startsWith("[Chiamata #")
                        || text.startsWith("Data originale non valida:")))
            return new CallText(original, false);
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            if (line.isBlank()) break;
            boolean metadata =
                    line.matches("\\[Chiamata #[^\\]]+\\]")
                            || java.util.stream.Stream.of(
                                            "Data originale non valida:",
                                            "Stato precedente:",
                                            "Nuovo stato:",
                                            "Checkpoint:",
                                            "Durata:",
                                            "Messaggio:")
                                    .anyMatch(line::startsWith);
            if (!metadata) break;
            offset += line.length() + 1;
        }
        return new CallText(text.substring(Math.min(offset, text.length())).stripLeading(), true);
    }

    private record CallText(String body, boolean legacy) {}

    void renderEditor(List<InteractionEditInput> interactions) {
        timelineList.getChildren().clear();
        timelineEditFields.clear();
        importedDisclosure.setVisible(false);
        importedDisclosure.setManaged(false);
        if (interactions.isEmpty()) {
            timelineList.getChildren().add(createInfoLabel("Nessuna interazione registrata"));
            return;
        }
        for (InteractionEditInput interaction : interactions) {
            VBox card = new VBox(8);
            card.setMaxWidth(Double.MAX_VALUE);
            card.getStyleClass().add("client-profile-timeline-card");
            FlowPane header =
                    createTimelineHeader(
                            interaction.data(),
                            interaction.createdAt(),
                            interaction.type(),
                            interaction.prossimoContatto() != null,
                            interaction.esito());
            DatePicker nextCallPicker = null;
            ChoiceBox<CallOutcome> outcomeChoiceBox = null;
            if (interaction.type() == InteractionType.CHIAMATA) {
                nextCallPicker = new DatePicker(interaction.prossimoContatto());
                nextCallPicker.setPromptText("Prossima chiamata");
                nextCallPicker.getStyleClass().add("client-profile-call-date-picker");
                outcomeChoiceBox = createCallOutcomeChoiceBox(interaction.esito());
            }
            TextArea textArea = new TextArea(interaction.testo());
            configureEditableInteractionTextArea(textArea);
            TimelineEditField editField =
                    new TimelineEditField(
                            interaction.notaId(),
                            interaction.interazioneId(),
                            interaction.data(),
                            interaction.createdAt(),
                            interaction.type(),
                            interaction.prossimoContatto(),
                            interaction.esito(),
                            outcomeChoiceBox,
                            nextCallPicker,
                            textArea);
            Button deleteButton =
                    createDeleteInteractionButton(
                            () -> {
                                timelineEditFields.remove(editField);
                                timelineList.getChildren().remove(card);
                            });
            if (interaction.type() == InteractionType.CHIAMATA) {
                card.getChildren()
                        .addAll(
                                header,
                                createEditableInteractionActions(
                                        outcomeChoiceBox, nextCallPicker, deleteButton));
            } else {
                addDeleteActionToHeader(header, deleteButton);
                card.getChildren().add(header);
            }
            card.getChildren().add(textArea);
            timelineList.getChildren().add(card);
            timelineEditFields.add(editField);
        }
    }

    List<InteractionEditInput> collectInteractions() {
        return timelineEditFields.stream()
                .map(
                        field ->
                                new InteractionEditInput(
                                        field.notaId(),
                                        field.interazioneId(),
                                        field.data(),
                                        field.createdAt(),
                                        field.type(),
                                        field.nextCallPicker() == null
                                                ? field.prossimoContatto()
                                                : field.nextCallPicker().getValue(),
                                        field.outcomeChoiceBox() == null
                                                ? field.esito()
                                                : field.outcomeChoiceBox().getValue(),
                                        field.textArea().getText()))
                .toList();
    }

    void setEditMode(boolean editMode) {
        newNoteButton.setDisable(editMode);
        newCallButton.setDisable(editMode);
        allFilterButton.setDisable(editMode);
        notesFilterButton.setDisable(editMode);
        callsFilterButton.setDisable(editMode);
    }

    void showNoteEditor() {
        saveNoteButton.setText("Salva nota");
        nextCallDatePicker.setVisible(false);
        nextCallDatePicker.setManaged(false);
        callOutcomeChoiceBox.setVisible(false);
        callOutcomeChoiceBox.setManaged(false);
        showEditor();
    }

    void showCallEditor() {
        saveNoteButton.setText("Salva chiamata");
        nextCallDatePicker.setVisible(true);
        nextCallDatePicker.setManaged(true);
        callOutcomeChoiceBox.setVisible(true);
        callOutcomeChoiceBox.setManaged(true);
        showEditor();
    }

    void hideNoteEditor() {
        noteEditor.setVisible(false);
        noteEditor.setManaged(false);
        noteTextArea.clear();
        nextCallDatePicker.setValue(null);
        callOutcomeChoiceBox.setValue(null);
    }

    void setActiveTimelineFilter(TimelineFilter filter) {
        allFilterButton.getStyleClass().remove("client-profile-small-filter-active");
        notesFilterButton.getStyleClass().remove("client-profile-small-filter-active");
        callsFilterButton.getStyleClass().remove("client-profile-small-filter-active");

        Button activeButton =
                switch (filter) {
                    case NOTES -> notesFilterButton;
                    case CALLS -> callsFilterButton;
                    case ALL -> allFilterButton;
                };
        activeButton.getStyleClass().add("client-profile-small-filter-active");
    }

    Button getNewNoteButton() {
        return newNoteButton;
    }

    Button getNewCallButton() {
        return newCallButton;
    }

    Button getAllFilterButton() {
        return allFilterButton;
    }

    Button getNotesFilterButton() {
        return notesFilterButton;
    }

    Button getCallsFilterButton() {
        return callsFilterButton;
    }

    DatePicker getNextCallDatePicker() {
        return nextCallDatePicker;
    }

    ChoiceBox<CallOutcome> getCallOutcomeChoiceBox() {
        return callOutcomeChoiceBox;
    }

    TextArea getNoteTextArea() {
        return noteTextArea;
    }

    Button getSaveNoteButton() {
        return saveNoteButton;
    }

    Button getCancelNoteButton() {
        return cancelNoteButton;
    }

    private FlowPane createTimelineHeader(
            LocalDate date,
            LocalDateTime createdAt,
            InteractionType type,
            boolean hasNextContact,
            CallOutcome outcome) {
        FlowPane header = new FlowPane(8, 6);
        header.getStyleClass().add("client-profile-timeline-card-header");
        Label typeBadge = new Label(type.label());
        typeBadge
                .getStyleClass()
                .addAll(
                        "client-profile-timeline-type-badge",
                        type == InteractionType.CHIAMATA
                                ? "client-profile-timeline-call-badge"
                                : "client-profile-timeline-note-badge");
        String dateAndTime = date == null ? "Data non indicata" : DATE_FORMATTER.format(date);
        if (createdAt != null
                && createdAt.toLocalDate().equals(date)
                && (createdAt.getHour() != 0 || createdAt.getMinute() != 0)) {
            dateAndTime += " " + TIME_FORMATTER.format(createdAt);
        }
        Label title = createInfoLabel(dateAndTime);
        title.getStyleClass().add("client-profile-timeline-title");
        header.getChildren().addAll(typeBadge, title);
        if (outcome != null) {
            Label outcomeBadge = new Label(outcome.label());
            outcomeBadge.getStyleClass().add("client-profile-timeline-outcome-badge");
            header.getChildren().add(outcomeBadge);
        }
        if (hasNextContact) {
            Label nextBadge = new Label("Follow-up");
            nextBadge.getStyleClass().add("client-profile-timeline-next-badge");
            header.getChildren().add(nextBadge);
        }
        return header;
    }

    private HBox createEditableInteractionActions(
            ChoiceBox<CallOutcome> outcomeChoiceBox,
            DatePicker nextCallPicker,
            Button deleteButton) {
        HBox actions = new HBox(8);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.getStyleClass().add("client-profile-edit-interaction-actions");
        if (outcomeChoiceBox != null) {
            actions.getChildren().add(outcomeChoiceBox);
        }
        if (nextCallPicker != null) {
            nextCallPicker.setPrefWidth(EDIT_NEXT_CALL_PICKER_PREF_WIDTH);
            nextCallPicker.setMaxWidth(EDIT_NEXT_CALL_PICKER_MAX_WIDTH);
            HBox.setHgrow(nextCallPicker, Priority.NEVER);
            actions.getChildren().add(nextCallPicker);
        }
        actions.getChildren().add(deleteButton);
        return actions;
    }

    private ChoiceBox<CallOutcome> createCallOutcomeChoiceBox(CallOutcome selected) {
        ChoiceBox<CallOutcome> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().setAll(CallOutcome.values());
        choiceBox.setValue(selected);
        choiceBox.setPrefWidth(170);
        choiceBox.setMaxWidth(190);
        choiceBox.getStyleClass().add("client-profile-call-outcome-choice");
        return choiceBox;
    }

    private void addDeleteActionToHeader(FlowPane header, Button deleteButton) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, deleteButton);
    }

    private Button createDeleteInteractionButton(Runnable deleteAction) {
        Button deleteButton = new Button("🗑");
        deleteButton.setAccessibleText("Elimina interazione");
        deleteButton.setMinWidth(DELETE_INTERACTION_BUTTON_WIDTH);
        deleteButton.setPrefWidth(DELETE_INTERACTION_BUTTON_WIDTH);
        deleteButton.getStyleClass().add("client-profile-delete-interaction-button");
        deleteButton.setOnAction(event -> deleteAction.run());
        return deleteButton;
    }

    private void configureEditableInteractionTextArea(TextArea textArea) {
        textArea.getStyleClass().add("client-profile-note-area");
        textArea.setWrapText(true);
        textArea.setMaxWidth(EDIT_INTERACTION_TEXT_AREA_MAX_WIDTH);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setMinHeight(EDIT_INTERACTION_TEXT_AREA_MIN_HEIGHT);
        textArea.setPrefRowCount(EDIT_INTERACTION_TEXT_AREA_PREF_ROWS);
        VBox.setVgrow(textArea, Priority.ALWAYS);
    }

    private void showEditor() {
        historyScroll.setVvalue(0);
        noteEditor.setVisible(true);
        noteEditor.setManaged(true);
        noteTextArea.requestFocus();
    }

    private VBox createNoteEditor() {
        VBox editor = new VBox(10);
        editor.getStyleClass().add("client-profile-note-editor");
        return editor;
    }

    private HBox createNoteActions() {
        HBox actions = new HBox(10);
        actions.getChildren().addAll(saveNoteButton, cancelNoteButton);
        return actions;
    }

    private FlowPane createActions() {
        FlowPane actions = new FlowPane(8, 6);
        actions.setPrefWrapLength(290);
        actions.getStyleClass().add("client-profile-timeline-actions");
        actions.getChildren().addAll(newNoteButton, newCallButton);
        return actions;
    }

    private HBox createFilters() {
        HBox filters = new HBox(6);
        filters.getStyleClass().add("client-profile-timeline-filter-bar");
        filters.getChildren().addAll(allFilterButton, notesFilterButton, callsFilterButton);
        return filters;
    }

    private Button createTimelineFilterButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("client-profile-small-filter-button");
        return button;
    }

    private Label createInfoLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("client-profile-info-label");
        label.setWrapText(true);
        return label;
    }

    private record TimelineEditField(
            java.util.UUID notaId,
            java.util.UUID interazioneId,
            LocalDate data,
            LocalDateTime createdAt,
            InteractionType type,
            LocalDate prossimoContatto,
            CallOutcome esito,
            ChoiceBox<CallOutcome> outcomeChoiceBox,
            DatePicker nextCallPicker,
            TextArea textArea) {}
}
