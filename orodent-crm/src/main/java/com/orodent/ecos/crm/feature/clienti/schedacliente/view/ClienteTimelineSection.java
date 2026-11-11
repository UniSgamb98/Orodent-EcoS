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
    private static final double TIMELINE_SCROLL_MAX_HEIGHT = 500;

    private final Button newNoteButton;
    private final Button newCallButton;
    private final Button allFilterButton;
    private final Button notesFilterButton;
    private final Button callsFilterButton;
    private final VBox timelineList;
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
        Label title = new Label("Interazioni");
        title.getStyleClass().add("new-client-section-title");

        newNoteButton = new Button("+ Nuova nota");
        newNoteButton.getStyleClass().add("clients-filter-button");
        newCallButton = new Button("+ Nuova chiamata");
        newCallButton.getStyleClass().add("clients-primary-button");
        allFilterButton = createTimelineFilterButton("Tutti");
        notesFilterButton = createTimelineFilterButton("Solo note");
        callsFilterButton = createTimelineFilterButton("Solo chiamate");
        timelineList = new VBox(10);
        ScrollPane timelineScrollPane = createTimelineScrollPane();
        noteEditor = createNoteEditor();
        nextCallDatePicker = new DatePicker();
        nextCallDatePicker.setPromptText("Prossima chiamata");
        nextCallDatePicker.getStyleClass().add("client-profile-call-date-picker");
        callOutcomeChoiceBox = createCallOutcomeChoiceBox(null);
        noteTextArea = new TextArea();
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

        getChildren()
                .addAll(title, createActions(), createFilters(), noteEditor, timelineScrollPane);
    }

    private ScrollPane createTimelineScrollPane() {
        ScrollPane scrollPane = new ScrollPane(timelineList);
        scrollPane.getStyleClass().add("client-profile-timeline-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setMaxHeight(TIMELINE_SCROLL_MAX_HEIGHT);
        scrollPane.setPrefViewportHeight(TIMELINE_SCROLL_MAX_HEIGHT);
        return scrollPane;
    }

    void render(List<InteractionPreview> interactions) {
        timelineList.getChildren().clear();
        if (interactions.isEmpty()) {
            timelineList.getChildren().add(createInfoLabel("Nessuna interazione registrata"));
            return;
        }
        for (InteractionPreview interaction : interactions) {
            VBox card = new VBox(4);
            card.getStyleClass().add("client-profile-timeline-card");
            HBox header =
                    createTimelineHeader(
                            interaction.data(),
                            interaction.createdAt(),
                            interaction.type(),
                            interaction.prossimoContatto() != null,
                            interaction.esito());
            Label text = createInfoLabel(timelineText(interaction));
            card.getChildren().addAll(header, text);
            timelineList.getChildren().add(card);
        }
    }

    void renderEditor(List<InteractionEditInput> interactions) {
        timelineList.getChildren().clear();
        timelineEditFields.clear();
        if (interactions.isEmpty()) {
            timelineList.getChildren().add(createInfoLabel("Nessuna interazione registrata"));
            return;
        }
        for (InteractionEditInput interaction : interactions) {
            VBox card = new VBox(8);
            card.setMaxWidth(Double.MAX_VALUE);
            card.getStyleClass().add("client-profile-timeline-card");
            HBox header =
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

    private HBox createTimelineHeader(
            LocalDate date,
            LocalDateTime createdAt,
            InteractionType type,
            boolean hasNextContact,
            CallOutcome outcome) {
        HBox header = new HBox(8);
        header.getStyleClass().add("client-profile-timeline-card-header");
        Label typeBadge = new Label(type.label());
        typeBadge
                .getStyleClass()
                .addAll(
                        "client-profile-timeline-type-badge",
                        type == InteractionType.CHIAMATA
                                ? "client-profile-timeline-call-badge"
                                : "client-profile-timeline-note-badge");
        String dateAndTime = DATE_FORMATTER.format(date);
        if (createdAt != null && createdAt.toLocalDate().equals(date)) {
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

    private void addDeleteActionToHeader(HBox header, Button deleteButton) {
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

    private HBox createActions() {
        HBox actions = new HBox(10);
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

    private String timelineText(InteractionPreview interaction) {
        if (interaction.prossimoContatto() == null) {
            return interaction.testo();
        }
        return interaction.testo()
                + "\nProssima chiamata: "
                + DATE_FORMATTER.format(interaction.prossimoContatto());
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
