package com.orodent.ecos.crm.feature.clienti.schedacliente.view;

import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.ClienteProfile;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.EditProfileDraft;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionPreview;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.InteractionType;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.TimelineFilter;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SchedaClienteView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final double RELATED_SECTIONS_GAP = 12;
    private static final double RELATED_SECTIONS_TWO_COLUMN_BREAKPOINT = 560;

    private final AppSidebar sidebar;
    private final VBox columns = new VBox(16);
    private final BorderPane responsiveHost = new BorderPane();
    private TitledPane resourcesDisclosure;
    private boolean editing;
    private ScrollPane contactScroll;
    private TitledPane compactContacts;
    private final ClienteProfileHeader header;
    private final ClienteDataSection dataSection;
    private final ClienteRelatedSections relatedSections;
    private final ClienteProfileDetailsPanel detailsPanel;
    private final ClienteTimelineSection timelineSection;
    private final ClienteProfileResourcesPanel resourcesPanel;

    public SchedaClienteView() {
        sidebar = new AppSidebar();
        header = new ClienteProfileHeader();
        dataSection = new ClienteDataSection();
        relatedSections =
                new ClienteRelatedSections(
                        RELATED_SECTIONS_GAP, RELATED_SECTIONS_TWO_COLUMN_BREAKPOINT);
        detailsPanel = new ClienteProfileDetailsPanel(dataSection, relatedSections);
        timelineSection = new ClienteTimelineSection();
        resourcesPanel = new ClienteProfileResourcesPanel();
        dataSection.setLinkedOptionsRefresh(relatedSections::refreshLinkedContactOptions);
        setEditMode(false);

        setLeft(sidebar);
        setCenter(createContent());
    }

    private BorderPane createContent() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(18, 22, 18, 22));
        responsiveHost.getStyleClass().addAll("clients-content", "client-profile-page");
        Button back = new Button("← Contatti e lead");
        back.getStyleClass().add("client-profile-back");
        back.setOnAction(e -> sidebar.getClientsButton().fire());
        resourcesDisclosure = new TitledPane("Attrezzature e materiali", resourcesPanel);
        resourcesDisclosure.setExpanded(false);
        resourcesDisclosure.setAnimated(false);
        resourcesDisclosure.getStyleClass().add("client-profile-disclosure");
        contactScroll = new ScrollPane(detailsPanel);
        contactScroll.setId("crm-contact-scroll");
        contactScroll.setFitToWidth(true);
        contactScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contactScroll.setMinHeight(0);
        contactScroll.getStyleClass().add("client-profile-contact-scroll");
        compactContacts = new TitledPane("Contatti e dati azienda", null);
        compactContacts.setAnimated(false);
        compactContacts.getStyleClass().add("client-profile-disclosure");
        VBox.setVgrow(columns, Priority.ALWAYS);
        columns.setMinHeight(0);
        columns.setPrefHeight(500);
        // Resource editors keep their full width and remain reachable without scrolling the
        // history.
        ScrollPane resourceScroll = new ScrollPane(resourcesPanel);
        resourceScroll.setId("crm-resource-scroll");
        resourceScroll.setFitToWidth(true);
        resourceScroll.setPrefViewportHeight(250);
        resourceScroll.getStyleClass().add("new-client-scroll");
        resourcesDisclosure.setContent(resourceScroll);
        createMainColumns(content, back, resourceScroll);
        return responsiveHost;
    }

    private void createMainColumns(VBox widePage, Button back, ScrollPane resourceScroll) {
        HBox wide = new HBox(16);
        VBox compactPage = new VBox(14);
        compactPage.setPadding(new Insets(18, 22, 18, 22));
        ScrollPane pageScroll = new ScrollPane(compactPage);
        pageScroll.setId("crm-page-scroll");
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.getStyleClass().add("new-client-scroll");
        Boolean[] lastCompact = {null};
        Runnable layout =
                () -> {
                    boolean small = responsiveHost.getWidth() - 44 < (editing ? 1120 : 900);
                    double sideWidth = editing ? 530 : 350;
                    detailsPanel.setMinWidth(0);
                    detailsPanel.setMaxWidth(Double.MAX_VALUE);
                    contactScroll.setMinWidth(small ? 0 : sideWidth);
                    contactScroll.setPrefWidth(small ? 580 : sideWidth);
                    contactScroll.setMaxWidth(small ? Double.MAX_VALUE : sideWidth);
                    contactScroll.setMaxHeight(Double.MAX_VALUE);
                    timelineSection.setPrefWidth(600);
                    timelineSection.setMaxWidth(Double.MAX_VALUE);
                    if (java.util.Objects.equals(lastCompact[0], small)) {
                        if (small && editing) compactContacts.setExpanded(true);
                        return;
                    }
                    lastCompact[0] = small;
                    wide.getChildren().clear();
                    columns.getChildren().clear();
                    widePage.getChildren().clear();
                    compactPage.getChildren().clear();
                    compactContacts.setContent(null);
                    contactScroll.setContent(null);
                    resourcesDisclosure.setContent(null);
                    resourceScroll.setContent(null);
                    detailsPanel.setVisible(true);
                    resourcesPanel.setVisible(true);
                    timelineSection.setPageScrolling(small);
                    if (small) {
                        compactContacts.setContent(detailsPanel);
                        compactContacts.setExpanded(true);
                        compactContacts.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                        resourcesDisclosure.setContent(resourcesPanel);
                        resourcesDisclosure.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                        VBox.setVgrow(timelineSection, Priority.NEVER);
                        compactPage
                                .getChildren()
                                .setAll(
                                        back,
                                        header,
                                        compactContacts,
                                        timelineSection,
                                        resourcesDisclosure);
                        responsiveHost.setCenter(pageScroll);
                    } else {
                        contactScroll.setContent(detailsPanel);
                        contactScroll.setVisible(true);
                        detailsPanel.setVisible(true);
                        resourceScroll.setContent(resourcesPanel);
                        resourcesPanel.setVisible(true);
                        resourcesDisclosure.setContent(resourceScroll);
                        resourcesDisclosure.setMinHeight(0);
                        wide.getChildren().setAll(contactScroll, timelineSection);
                        HBox.setHgrow(timelineSection, Priority.ALWAYS);
                        columns.getChildren().setAll(wide);
                        VBox.setVgrow(wide, Priority.ALWAYS);
                        widePage.getChildren().setAll(back, header, columns, resourcesDisclosure);
                        responsiveHost.setCenter(widePage);
                    }
                };
        responsiveHost.widthProperty().addListener((o, a, b) -> layout.run());
        editingPropertyRefresh = layout;
        layout.run();
    }

    private Runnable editingPropertyRefresh = () -> {};

    public void renderTimeline(List<InteractionPreview> interactions) {
        timelineSection.render(interactions);
    }

    public void renderProfile(ClienteProfile profile) {
        setEditMode(false);
        header.setTitle(profile.ragioneSociale());
        header.setSubtitle(
                java.util.stream.Stream.of(profile.tipoCliente(), profile.statoTrattativa())
                        .filter(v -> v != null && !v.isBlank())
                        .collect(java.util.stream.Collectors.joining(" · ")));
        header.setAcquisitionText("Acquisito " + formatDate(profile.acquisizione()));
        header.setLastInteractionText("Ultima chiamata " + lastCallText(profile.interazioni()));
        header.setNextInteractionText(nextCallText(profile.interazioni()));
        header.setNextDate(latestNextCall(profile.interazioni()));
        resourcesDisclosure.setText(
                "Attrezzature e materiali · "
                        + profile.forni().size()
                        + " forni · "
                        + profile.fresatori().size()
                        + " fresatori · "
                        + profile.materiali().size()
                        + " materiali");
        setFavorite(profile.favorite());
        setInvolvementSliderValue(profile.coinvolgimento());
        dataSection.render(profile);
        relatedSections.renderContacts(profile.contatti());
        relatedSections.renderAddresses(profile.indirizzi());
        timelineSection.render(profile.interazioni());
        resourcesPanel.renderForni(profile.forni());
        resourcesPanel.renderFresatori(profile.fresatori());
        resourcesPanel.renderMateriali(profile.materiali());
    }

    public void renderEditableProfile(EditProfileDraft draft) {
        setEditMode(true);
        header.setTitle(draft.ragioneSociale().isBlank() ? "Cliente" : draft.ragioneSociale());
        header.setSubtitle("Modifica dati cliente");
        header.setAcquisitionText("Acquisito " + formatDate(draft.acquisizione()));
        header.setLastInteractionText(
                "Ultima chiamata " + lastEditableCallText(draft.interazioni()));
        header.setNextInteractionText(
                "Prossima chiamata " + nextEditableCallText(draft.interazioni()));
        setActiveTimelineFilter(TimelineFilter.ALL);
        dataSection.renderEditor(draft);
        relatedSections.setCompanyValueSources(
                dataSection.phoneEditFields(), dataSection.emailEditFields());
        relatedSections.renderContactsEditor(draft.contatti());
        relatedSections.renderAddressesEditor(draft.indirizzi());
        timelineSection.renderEditor(draft.interazioni());
    }

    public EditProfileDraft collectEditDraft() {
        return new EditProfileDraft(
                dataSection.ragioneSociale(),
                dataSection.tipoCliente(),
                dataSection.statoTrattativa(),
                dataSection.coinvolgimento(),
                dataSection.partitaIva(),
                dataSection.codiceFiscale(),
                dataSection.acquisizione(),
                dataSection.telefoni(),
                dataSection.email(),
                dataSection.sitiWeb(),
                relatedSections.collectAddresses(),
                relatedSections.collectContacts(),
                timelineSection.collectInteractions());
    }

    private void setEditMode(boolean editMode) {
        editing = editMode;
        editingPropertyRefresh.run();
        header.setEditMode(editMode);
        timelineSection.setEditMode(editMode);
    }

    public Button getEditForniButton() {
        return resourcesPanel.editForniButton();
    }

    public Button getSaveForniButton() {
        return resourcesPanel.saveForniButton();
    }

    public Button getCancelForniButton() {
        return resourcesPanel.cancelForniButton();
    }

    public void renderStandaloneForniEditor(List<FornoClienteEditInput> forni) {
        resourcesPanel.renderStandaloneForniEditor(forni);
    }

    public void renderForni(List<FornoClienteItem> forni) {
        resourcesPanel.renderForni(forni);
    }

    public List<FornoClienteEditInput> collectForni() {
        return resourcesPanel.collectForni();
    }

    public Button getEditFresatoriButton() {
        return resourcesPanel.editFresatoriButton();
    }

    public Button getSaveFresatoriButton() {
        return resourcesPanel.saveFresatoriButton();
    }

    public Button getCancelFresatoriButton() {
        return resourcesPanel.cancelFresatoriButton();
    }

    public void renderStandaloneFresatoriEditor(List<FresatoreClienteEditInput> fresatori) {
        resourcesPanel.renderStandaloneFresatoriEditor(fresatori);
    }

    public void renderFresatori(List<FresatoreClienteItem> fresatori) {
        resourcesPanel.renderFresatori(fresatori);
    }

    public List<FresatoreClienteEditInput> collectFresatori() {
        return resourcesPanel.collectFresatori();
    }

    public void setForniCatalog(List<FornoCatalogItem> forniCatalog) {
        resourcesPanel.setForniCatalog(forniCatalog);
    }

    public Button getEditMaterialiButton() {
        return resourcesPanel.editMaterialiButton();
    }

    public Button getSaveMaterialiButton() {
        return resourcesPanel.saveMaterialiButton();
    }

    public Button getCancelMaterialiButton() {
        return resourcesPanel.cancelMaterialiButton();
    }

    public void renderStandaloneMaterialiEditor(List<MaterialeClienteEditInput> materiali) {
        resourcesPanel.renderStandaloneMaterialiEditor(materiali);
    }

    public void renderMateriali(List<MaterialeClienteItem> materiali) {
        resourcesPanel.renderMateriali(materiali);
    }

    public List<MaterialeClienteEditInput> collectMateriali() {
        return resourcesPanel.collectMateriali();
    }

    public void setFresatoriCatalog(List<FresatoreCatalogItem> fresatoriCatalog) {
        resourcesPanel.setFresatoriCatalog(fresatoriCatalog);
    }

    public void setMaterialiCatalog(List<MaterialeCatalogItem> materialiCatalog) {
        resourcesPanel.setMaterialiCatalog(materialiCatalog);
    }

    public void setFavorite(boolean favorite) {
        header.setFavorite(favorite);
    }

    public void showNoteEditor() {
        timelineSection.showNoteEditor();
    }

    public void showCallEditor() {
        timelineSection.showCallEditor();
    }

    public void hideNoteEditor() {
        timelineSection.hideNoteEditor();
    }

    public void setActiveTimelineFilter(TimelineFilter filter) {
        timelineSection.setActiveTimelineFilter(filter);
    }

    private String lastCallText(List<InteractionPreview> interactions) {
        return interactions.stream()
                .filter(interaction -> interaction.type() == InteractionType.CHIAMATA)
                .findFirst()
                .map(interaction -> formatDate(interaction.data()))
                .orElse("-");
    }

    private LocalDate latestNextCall(List<InteractionPreview> interactions) {
        return interactions.stream()
                .filter(i -> i.type() == InteractionType.CHIAMATA)
                .findFirst()
                .map(InteractionPreview::prossimoContatto)
                .orElse(null);
    }

    private String nextCallText(List<InteractionPreview> interactions) {
        return "Prossimo richiamo " + formatDate(latestNextCall(interactions));
    }

    private String lastEditableCallText(List<InteractionEditInput> interactions) {
        return interactions.stream()
                .filter(interaction -> interaction.type() == InteractionType.CHIAMATA)
                .findFirst()
                .map(interaction -> formatDate(interaction.data()))
                .orElse("-");
    }

    private String nextEditableCallText(List<InteractionEditInput> interactions) {
        return interactions.stream()
                .map(InteractionEditInput::prossimoContatto)
                .filter(nextContact -> nextContact != null)
                .findFirst()
                .map(DATE_FORMATTER::format)
                .orElse("-");
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMATTER.format(date);
    }

    public void setTipoClienteOptions(List<String> options) {
        dataSection.setTipoClienteOptions(options);
    }

    public void setStatoTrattativaOptions(List<String> options) {
        dataSection.setStatoTrattativaOptions(options);
    }

    public AppSidebar getSidebar() {
        return sidebar;
    }

    public Slider getInvolvementSlider() {
        return header.getInvolvementSlider();
    }

    public boolean isUpdatingInvolvementSlider() {
        return header.isUpdatingInvolvementSlider();
    }

    public int involvementSliderValue() {
        return header.involvementSliderValue();
    }

    public void setInvolvementSliderValue(Integer value) {
        header.setInvolvementSliderValue(value);
    }

    public Button getFavoriteButton() {
        return header.getFavoriteButton();
    }

    public Button getEditProfileButton() {
        return header.getEditProfileButton();
    }

    public Button getSaveProfileEditButton() {
        return header.getSaveProfileEditButton();
    }

    public Button getCancelProfileEditButton() {
        return header.getCancelProfileEditButton();
    }

    public Button getNewNoteButton() {
        return timelineSection.getNewNoteButton();
    }

    public Button getNewCallButton() {
        return timelineSection.getNewCallButton();
    }

    public Button getAllFilterButton() {
        return timelineSection.getAllFilterButton();
    }

    public Button getNotesFilterButton() {
        return timelineSection.getNotesFilterButton();
    }

    public Button getCallsFilterButton() {
        return timelineSection.getCallsFilterButton();
    }

    public DatePicker getNextCallDatePicker() {
        return timelineSection.getNextCallDatePicker();
    }

    public ChoiceBox<CallOutcome> getCallOutcomeChoiceBox() {
        return timelineSection.getCallOutcomeChoiceBox();
    }

    public TextArea getNoteTextArea() {
        return timelineSection.getNoteTextArea();
    }

    public Button getSaveNoteButton() {
        return timelineSection.getSaveNoteButton();
    }

    public Button getCancelNoteButton() {
        return timelineSection.getCancelNoteButton();
    }
}
