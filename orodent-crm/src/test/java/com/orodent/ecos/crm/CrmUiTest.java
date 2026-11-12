package com.orodent.ecos.crm;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.view.NuovoClienteView;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.*;
import com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("javafx")
class CrmUiTest {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void opensCreatesLeadRecordsCallAndKeepsOperatorAcrossReopening() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:crm_ui_" + UUID.randomUUID().toString().replace("-", "");
        AtomicInteger fxSql = new AtomicInteger();
        ConnectionProvider db =
                () -> {
                    if (Platform.isFxApplicationThread()) fxSql.incrementAndGet();
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(3);
        var homes = new AtomicInteger();
        var operator = new OperatorSession();
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
        }
        Platform.setImplicitExit(false);
        var context =
                new ModuleContext(
                        db,
                        workers,
                        new ClientiCsvImportService(db, workers),
                        new VenditeCsvImportService(db, workers),
                        homes::incrementAndGet,
                        operator);
        try {
            fx(
                    () -> {
                        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                        session = new CrmModule().open(context);
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1440, 900));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        return null;
                    });
            await(
                    () ->
                            button("crm-select-operator") != null
                                    && enabledButton("crm-select-operator"));
            fx(
                    () -> {
                        button("crm-select-operator").fire();
                        return null;
                    });
            await(() -> button("crm-create-lead") != null && enabledButton("crm-create-lead"));
            fx(
                    () -> {
                        button("crm-create-lead").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-new-lead") != null
                                    && enabledButton("crm-save-lead"));
            fx(
                    () -> {
                        var page = (NuovoClienteView) stage.getScene().lookup("#crm-new-lead");
                        page.getNameField().setText("Laboratorio Rossi");
                        page.getPhoneField().setText("123456789");
                        page.getEmailField().setText("info@example.test");
                        button("crm-save-lead").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-profile") != null
                                    && enabledButton("crm-new-call"));
            fx(
                    () -> {
                        var initialProfile =
                                (SchedaClienteView) stage.getScene().lookup("#crm-profile");
                        assertFalse(initialProfile.hasUnsavedChanges());
                        assertFalse(button("crm-save-profile").isVisible());
                        assertNull(button("crm-edit-profile"));
                        var name = (TextField) stage.getScene().lookup("#crm-profile-name");
                        name.setText("Laboratorio Rossi modificato");
                        assertTrue(initialProfile.hasUnsavedChanges());
                        assertTrue(button("crm-save-profile").isVisible());
                        name.setText("Laboratorio Rossi");
                        assertFalse(initialProfile.hasUnsavedChanges());
                        name.setText("Laboratorio Rossi modificato");
                        button("crm-new-call").fire();
                        var profile = (SchedaClienteView) stage.getScene().lookup("#crm-profile");
                        profile.getNoteTextArea()
                                .setText("Inviato listino, richiamare la prossima settimana");
                        profile.getNextCallDatePicker()
                                .setValue(java.time.LocalDate.now().plusDays(2));
                        profile.getCallOutcomeChoiceBox().setValue(CallOutcome.LISTINO);
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("crm-home").fire();
                        assertEquals(0, homes.get());
                        button("crm-save-profile").fire();
                        return null;
                    });
            await(() -> button("crm-new-call") != null && enabledButton("crm-new-call"));
            fx(
                    () -> {
                        var profile = (SchedaClienteView) stage.getScene().lookup("#crm-profile");
                        assertFalse(profile.hasUnsavedChanges());
                        assertFalse(button("crm-save-profile").isVisible());
                        profile.getNotesFilterButton().fire();
                        assertFalse(profile.hasUnsavedChanges());
                        profile.getAllFilterButton().fire();
                        snapshot("target/crm-profile.png");
                        // Reload also protects drafts; accepting discards the pending edit.
                        ((TextField) stage.getScene().lookup("#crm-profile-name"))
                                .setText("Da scartare");
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("crm-reload").fire();
                        assertTrue(profile.hasUnsavedChanges());
                        Platform.runLater(() -> answer(ButtonType.OK));
                        button("crm-reload").fire();
                        return null;
                    });
            await(
                    () ->
                            button("crm-new-call") != null
                                    && enabledButton("crm-new-call")
                                    && stage.getScene().lookup("#crm-profile-name") != null);
            fx(
                    () -> {
                        assertEquals(
                                "Laboratorio Rossi modificato",
                                ((TextField) stage.getScene().lookup("#crm-profile-name"))
                                        .getText());
                        button("crm-nav-calendar").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-calendar") != null
                                    && !stage.getScene().lookup("#crm-calendar").isDisabled());
            fx(
                    () -> {
                        snapshot("target/crm-calendar.png");
                        button("crm-nav-settings").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-settings") != null
                                    && !stage.getScene().lookup("#crm-settings").isDisabled());
            fx(
                    () -> {
                        snapshot("target/crm-settings.png");
                        button("crm-home").fire();
                        assertEquals(1, homes.get());
                        session.close();
                        session = new CrmModule().open(context);
                        stage.getScene().setRoot(session.root());
                        return null;
                    });
            await(() -> button("crm-create-lead") != null && enabledButton("crm-create-lead"));
            fx(
                    () -> {
                        assertNull(stage.getScene().lookup("#crm-login"));
                        snapshot("target/crm-list.png");
                        return null;
                    });
            assertEquals(0, fxSql.get(), "No JDBC should run on FX");
            assertTrue(errors.isEmpty(), errors::toString);
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r = q.executeQuery("SELECT COUNT(*) FROM CRM.INTERAZIONI")) {
                            r.next();
                            assertEquals(1, r.getInt(1));
                        }
                        return null;
                    });
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window w : List.copyOf(Window.getWindows())) w.hide();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }

    @Test
    void profileKeepsImportedHistoryReadableAndAdaptsWithoutLosingEditFields() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
        }
        Platform.setImplicitExit(false);
        var preview = new SchedaClienteView[1];
        var sample = new ClienteProfile[1];
        try {
            fx(
                    () -> {
                        var today = java.time.LocalDate.now();
                        var phone = new ValueItem(UUID.randomUUID(), "+39 0123 456789");
                        var mail = new ValueItem(UUID.randomUUID(), "adrian@example.test");
                        var calls =
                                List.of(
                                        new InteractionPreview(
                                                UUID.randomUUID(),
                                                null,
                                                today,
                                                today.atTime(9, 30),
                                                InteractionType.NOTA,
                                                null,
                                                null,
                                                "Riepilogo originale CRM2\n"
                                                        + "note_id: identificativo originale\n"
                                                        + "volte_contattati: 2\n"
                                                        + "checkpoint: 0"),
                                        new InteractionPreview(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                today.minusDays(20),
                                                today.minusDays(20).atStartOfDay(),
                                                InteractionType.CHIAMATA,
                                                today.minusDays(2),
                                                CallOutcome.LISTINO,
                                                "[Chiamata #2]\n"
                                                    + "Stato precedente: LISTINO\n"
                                                    + "Durata: 5\n"
                                                    + "Messaggio: false\n\n"
                                                    + "Parlato con il referente. Il laboratorio sta"
                                                    + " valutando la nuova zirconia; inviare la"
                                                    + " documentazione tecnica e ricontattare dopo"
                                                    + " le prove."),
                                        new InteractionPreview(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                today.minusMonths(3),
                                                today.minusMonths(3).atStartOfDay(),
                                                InteractionType.CHIAMATA,
                                                null,
                                                CallOutcome.PRESENTAZIONE,
                                                "Presentazione dell’azienda e dei prodotti. "
                                                        .repeat(35)));
                        var history = new ArrayList<>(calls);
                        for (int i = 0; i < 6; i++) {
                            var day = today.minusDays(i + 1);
                            history.add(
                                    i + 1,
                                    new InteractionPreview(
                                            UUID.randomUUID(),
                                            UUID.randomUUID(),
                                            day,
                                            day.atStartOfDay(),
                                            InteractionType.CHIAMATA,
                                            i == 0 ? today.minusDays(1) : null,
                                            CallOutcome.NON_TROVATO,
                                            "[Chiamata #"
                                                    + (8 - i)
                                                    + "]\r\n"
                                                    + "Stato precedente: LISTINO\r\n"
                                                    + "Nuovo stato: NON_TROVATO\r\n"
                                                    + "Checkpoint: 0\r\n"
                                                    + "Durata: 0\r\n"
                                                    + "Messaggio: false"));
                        }
                        history.add(
                                new InteractionPreview(
                                        UUID.randomUUID(),
                                        null,
                                        today.minusDays(10),
                                        today.minusDays(10).atTime(10, 0),
                                        InteractionType.NOTA,
                                        null,
                                        null,
                                        "Inviata la scheda tecnica richiesta dal laboratorio."));
                        var model =
                                new ClienteProfile(
                                        UUID.randomUUID(),
                                        "Laboratorio Rossi",
                                        "Laboratorio",
                                        "Listino",
                                        4,
                                        "IT12345678901",
                                        "",
                                        today.minusYears(1),
                                        false,
                                        List.of(phone),
                                        List.of(mail),
                                        List.of(
                                                new ValueItem(
                                                        UUID.randomUUID(), "https://example.test")),
                                        List.of(
                                                new AddressItem(
                                                        UUID.randomUUID(),
                                                        "Italia",
                                                        "Veneto",
                                                        "VR",
                                                        "Verona",
                                                        "Via del Laboratorio",
                                                        "12",
                                                        "37100",
                                                        true)),
                                        List.of(
                                                new ContactItem(
                                                        UUID.randomUUID(),
                                                        "Adrian · Responsabile laboratorio",
                                                        List.of(phone),
                                                        List.of(mail))),
                                        List.of(
                                                new FornoClienteItem(
                                                        UUID.randomUUID(),
                                                        UUID.randomUUID(),
                                                        "Sinterizzazione",
                                                        "2022",
                                                        "Marca forno",
                                                        "Modello forno",
                                                        "Nota forno")),
                                        List.of(
                                                new FresatoreClienteItem(
                                                        UUID.randomUUID(),
                                                        UUID.randomUUID(),
                                                        "Marca fresatore",
                                                        "Modello fresatore",
                                                        "Nota fresatore")),
                                        List.of(
                                                new MaterialeClienteItem(
                                                        UUID.randomUUID(),
                                                        UUID.randomUUID(),
                                                        "Zirconia",
                                                        "Marchio",
                                                        "Modello",
                                                        "10 kg",
                                                        "Mensile",
                                                        "Nota materiale")),
                                        history);
                        var page = new SchedaClienteView();
                        preview[0] = page;
                        sample[0] = model;
                        page.getSidebar()
                                .getChildren()
                                .setAll(
                                        page.getSidebar().getClientsButton(),
                                        page.getSidebar().getCalendarButton(),
                                        page.getSidebar().getSettingsButton());
                        page.renderProfile(model);
                        var root = new javafx.scene.layout.BorderPane(page);
                        root.getStyleClass().add("crm-module");
                        root.setId("crm-module");
                        for (String css :
                                List.of(
                                        "global.css",
                                        "ecos.css",
                                        "features/clienti.css",
                                        "features/scheda-cliente.css"))
                            root.getStylesheets()
                                    .add(
                                            getClass()
                                                    .getResource("/crm/css/" + css)
                                                    .toExternalForm());
                        stage = new Stage();
                        stage.setScene(new Scene(root, 1740, 900));
                        stage.show();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().getWidth() > 1300
                                    && stage.getScene().lookup(".client-profile-details-panel")
                                            != null
                                    && ((javafx.scene.layout.Region)
                                                            stage.getScene()
                                                                    .lookup(
                                                                            ".client-profile-details-panel"))
                                                    .getWidth()
                                            >= 340);
            fx(
                    () -> {
                        var page = preview[0];
                        var root = stage.getScene().getRoot();
                        root.applyCss();
                        root.layout();
                        var imported =
                                root.lookupAll(".titled-pane").stream()
                                        .map(n -> (TitledPane) n)
                                        .filter(
                                                n ->
                                                        n.getText()
                                                                .startsWith(
                                                                        "Informazioni importate"))
                                        .findFirst()
                                        .orElseThrow();
                        assertFalse(imported.isExpanded());
                        assertNotNull(root.lookup(".client-profile-overdue"));
                        snapshot("target/crm-profile-redesign.png");
                        var toggles =
                                root.lookupAll(".crm-activity-toggle").stream()
                                        .map(n -> (Button) n)
                                        .toList();
                        var unanswered =
                                toggles.stream()
                                        .filter(n -> n.getAccessibleText().contains("Non trovato"))
                                        .findFirst()
                                        .orElseThrow();
                        var compactRow = (javafx.scene.layout.VBox) unanswered.getParent();
                        assertTrue(root.lookup("#crm-contact-scroll").isVisible());
                        assertTrue(
                                compactRow.getHeight() < 70, "Metadata-only calls occupy one row");
                        assertFalse(
                                compactRow.getChildren().get(1).isManaged(),
                                "No metadata in the conversation preview");
                        unanswered.fire();
                        root.applyCss();
                        root.layout();
                        assertTrue(compactRow.getChildren().get(2).isManaged());
                        var raw = (TitledPane) compactRow.lookup(".titled-pane");
                        assertTrue(
                                ((Label) raw.getContent()).getText().contains("Messaggio: false"));
                        unanswered.fire();
                        var longCall =
                                toggles.stream()
                                        .filter(
                                                n ->
                                                        n.getAccessibleText()
                                                                .contains("Presentazione"))
                                        .findFirst()
                                        .orElseThrow();
                        longCall.fire();
                        assertTrue(longCall.getAccessibleHelp().contains("Comprimi"));
                        // Scrolling the history doesn't move company contacts.
                        var contact = root.lookup(".client-profile-contact-card");
                        double contactY =
                                contact.localToScene(contact.getBoundsInLocal()).getMinY();
                        var scroll = (ScrollPane) root.lookup("#crm-history-scroll");
                        scroll.setVvalue(1);
                        root.applyCss();
                        root.layout();
                        assertEquals(
                                contactY,
                                contact.localToScene(contact.getBoundsInLocal()).getMinY(),
                                0.5);
                        snapshot("target/crm-profile-history-scrolled.png");
                        scroll.setVvalue(0);
                        page.getNewNoteButton().fire();
                        page.showNoteEditor();
                        page.getNoteTextArea().setText("Appunto non ancora salvato");
                        stage.setWidth(1020);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() < 1100);
            fx(
                    () -> {
                        var page = preview[0];
                        var model = sample[0];
                        var phone = model.telefoni().getFirst();
                        var mail = model.email().getFirst();
                        var root = stage.getScene().getRoot();
                        root.applyCss();
                        root.layout();
                        assertEquals(
                                "Appunto non ancora salvato", page.getNoteTextArea().getText());
                        assertNull(root.lookup("#crm-contact-scroll"));
                        assertNull(root.lookup("#crm-history-scroll"));
                        assertNull(root.lookup("#crm-resource-scroll"));
                        var pageScroll = (ScrollPane) root.lookup("#crm-page-scroll");
                        assertNotNull(pageScroll);
                        var resources =
                                root.lookupAll(".titled-pane").stream()
                                        .map(n -> (TitledPane) n)
                                        .filter(
                                                n ->
                                                        n.getText()
                                                                .startsWith(
                                                                        "Attrezzature e materiali"))
                                        .findFirst()
                                        .orElseThrow();
                        resources.setExpanded(true);
                        root.applyCss();
                        root.layout();
                        snapshot("target/crm-profile-compact.png");
                        var details =
                                (javafx.scene.layout.Region)
                                        root.lookup(".client-profile-details-panel");
                        assertEquals(
                                details.prefHeight(details.getWidth()),
                                details.getHeight(),
                                2,
                                "Compact contacts use their full natural height");
                        assertTrue(
                                pageScroll.getContent().getLayoutBounds().getHeight()
                                        > pageScroll.getViewportBounds().getHeight() + 100);
                        pageScroll.setVvalue(1);
                        root.applyCss();
                        root.layout();
                        snapshot("target/crm-profile-compact-bottom.png");
                        pageScroll.setVvalue(0);
                        page.hideNoteEditor();
                        page.renderEditableProfile(
                                new EditProfileDraft(
                                        model.ragioneSociale(),
                                        model.tipoCliente(),
                                        model.statoTrattativa(),
                                        model.coinvolgimento(),
                                        model.partitaIva(),
                                        model.codiceFiscale(),
                                        model.acquisizione(),
                                        List.of(new ValueEditInput(phone.id(), phone.value())),
                                        List.of(new ValueEditInput(mail.id(), mail.value())),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of()));
                        root.applyCss();
                        root.layout();
                        var draft = page.collectEditDraft();
                        assertEquals(model.ragioneSociale(), draft.ragioneSociale());
                        assertEquals(phone.value(), draft.telefoni().getFirst().value());
                        snapshot("target/crm-profile-editor.png");
                        stage.setWidth(1740);
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().getWidth() > 1300
                                    && stage.getScene().lookup("#crm-contact-scroll") != null);
            fx(
                    () -> {
                        var page = preview[0];
                        assertNotNull(stage.getScene().lookup("#crm-history-scroll"));
                        assertNull(stage.getScene().lookup("#crm-page-scroll"));
                        stage.getScene().getRoot().applyCss();
                        stage.getScene().getRoot().layout();
                        assertTrue(
                                ((ScrollPane) stage.getScene().lookup("#crm-resource-scroll"))
                                                .getHeight()
                                        >= 200,
                                "Expanded resources remain usable after returning to the wide"
                                        + " layout");
                        assertTrue(stage.getScene().lookup("#crm-contact-scroll").isVisible());
                        assertEquals(
                                sample[0].ragioneSociale(),
                                page.collectEditDraft().ragioneSociale());
                        snapshot("target/crm-profile-wide-restored.png");
                        page.renderInlineProfile(sample[0]);
                        assertFalse(page.hasUnsavedChanges());
                        assertFalse(page.getSaveProfileEditButton().isVisible());
                        var original =
                                sample[0].interazioni().stream()
                                        .map(InteractionPreview::testo)
                                        .toList();
                        assertEquals(
                                original,
                                page.collectWorkspace().profile().interazioni().stream()
                                        .map(InteractionEditInput::testo)
                                        .toList());
                        page.filterInline(TimelineFilter.NOTES);
                        page.filterInline(TimelineFilter.ALL);
                        assertFalse(page.hasUnsavedChanges());
                        var inlineToggle =
                                stage
                                        .getScene()
                                        .getRoot()
                                        .lookupAll(".crm-activity-toggle")
                                        .stream()
                                        .map(n -> (Button) n)
                                        .filter(n -> n.getAccessibleText().contains("Non trovato"))
                                        .findFirst()
                                        .orElseThrow();
                        var inlineCard =
                                (javafx.scene.layout.VBox) inlineToggle.getParent().getParent();
                        inlineToggle.fire();
                        rootLayout();
                        var editor = (TextArea) inlineCard.lookup(".crm-inline-conversation");
                        assertEquals("", editor.getText());
                        editor.setText("Nuova conversazione");
                        assertTrue(page.hasUnsavedChanges());
                        assertTrue(page.getSaveProfileEditButton().isVisible());
                        assertTrue(
                                page.captureForSave().profile().interazioni().stream()
                                        .anyMatch(
                                                i ->
                                                        i.testo()
                                                                .endsWith(
                                                                        "\n\n"
                                                                            + "Nuova"
                                                                            + " conversazione")));
                        editor.setText("");
                        assertFalse(page.hasUnsavedChanges());
                        var date =
                                inlineCard.lookupAll(".date-picker").stream()
                                        .map(n -> (DatePicker) n)
                                        .filter(d -> "Data contatto".equals(d.getPromptText()))
                                        .findFirst()
                                        .orElseThrow();
                        String valid = date.getEditor().getText();
                        date.getEditor().setText("data non valida");
                        assertTrue(page.hasUnsavedChanges());
                        assertThrows(IllegalArgumentException.class, page::captureForSave);
                        assertEquals("data non valida", date.getEditor().getText());
                        date.getEditor().setText(valid);
                        assertFalse(page.hasUnsavedChanges());
                        snapshot("target/crm-profile-inline.png");
                        verifyExpandableResources(page);
                        var closeAll =
                                (Button) stage.getScene().lookup("#crm-close-all-activities");
                        assertTrue(closeAll.isVisible());
                        closeAll.fire();
                        assertTrue(
                                stage.getScene().getRoot().lookupAll(".crm-activity").stream()
                                        .noneMatch(
                                                n ->
                                                        n.getPseudoClassStates()
                                                                .contains(
                                                                        javafx.css.PseudoClass
                                                                                .getPseudoClass(
                                                                                        "expanded"))));
                        assertFalse(
                                page.hasUnsavedChanges(), "Closing activities preserves the draft");
                        snapshot("target/crm-resources-inline.png");
                        stage.setWidth(1020);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() < 1100);
            fx(
                    () -> {
                        assertFalse(
                                preview[0].hasUnsavedChanges(),
                                "Resizing doesn't create unsaved edits");
                        ((TextField) stage.getScene().lookup("#crm-profile-name"))
                                .setText("Nome in bozza");
                        assertTrue(preview[0].hasUnsavedChanges());
                        snapshot("target/crm-profile-inline-compact.png");
                        return null;
                    });
        } finally {
            fx(
                    () -> {
                        if (preview[0] != null) preview[0].disposeInline();
                        if (stage != null) stage.hide();
                        return null;
                    });
        }
    }

    void verifyExpandableResources(SchedaClienteView page) {
        var baseline = page.collectWorkspace();
        var toggles = stage.getScene().getRoot().lookupAll(".crm-resource-toggle");
        assertEquals(3, toggles.size());
        for (var node : toggles) {
            var toggle = (Button) node;
            var card = (javafx.scene.layout.VBox) toggle.getParent().getParent();
            var body = card.getChildren().get(1);
            assertFalse(body.isManaged(), "Saved resources start collapsed");
            toggle.fire();
            assertTrue(body.isManaged());
            toggle.fire();
            assertFalse(body.isManaged());
        }
        assertEquals(baseline, page.collectWorkspace());
        assertFalse(page.hasUnsavedChanges());
        stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && "+ Forno".equals(b.getText()))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow()
                .fire();
        var newToggle =
                stage.getScene().getRoot().lookupAll(".crm-resource-toggle").stream()
                        .map(n -> (Button) n)
                        .filter(n -> "Chiudi forno".equals(n.getAccessibleHelp()))
                        .findFirst()
                        .orElseThrow();
        var card = (javafx.scene.layout.VBox) newToggle.getParent().getParent();
        assertTrue(card.getChildren().get(1).isManaged(), "New resource starts open");
        var brand =
                card.lookupAll(".combo-box").stream()
                        .map(n -> (ComboBox<?>) n)
                        .filter(n -> "Marca".equals(n.getPromptText()))
                        .findFirst()
                        .orElseThrow();
        brand.getEditor().setText("Nuova marca");
        ((TextArea) card.lookup(".text-area")).setText("Bozza forno");
        assertEquals(
                "Nuova marca",
                ((Label) newToggle.getGraphic().lookup(".crm-resource-name")).getText());
        newToggle.fire();
        assertFalse(card.getChildren().get(1).isManaged());
        assertEquals(2, page.collectWorkspace().forni().size());
        assertEquals("Nuova marca", page.collectWorkspace().forni().getLast().marca());
        assertEquals("Bozza forno", page.collectWorkspace().forni().getLast().nota());
        assertTrue(page.hasUnsavedChanges());
        newToggle.fire();
        assertEquals("Bozza forno", ((TextArea) card.lookup(".text-area")).getText());
        var menu = (MenuButton) card.lookup(".crm-row-menu");
        assertEquals("Azioni forno", menu.getTooltip().getText());
        assertEquals("Elimina forno dalla scheda cliente", menu.getItems().getFirst().getText());
        menu.getItems().getFirst().fire();
        assertEquals(
                baseline,
                page.collectWorkspace(),
                "Removing the new row restores the original draft");
        assertFalse(page.hasUnsavedChanges());
        toggles.stream()
                .map(n -> (Button) n)
                .filter(n -> n.getAccessibleText().endsWith("forno"))
                .findFirst()
                .orElseThrow()
                .fire();
        rootLayout();
    }

    boolean enabledButton(String id) {
        Button button = button(id);
        return button != null && !button.isDisabled();
    }

    Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    void answer(ButtonType type) {
        for (Window w : Window.getWindows())
            if (w != stage && w.isShowing() && w.getScene().getRoot() instanceof DialogPane pane) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
        fail("Dialogo atteso non presente");
    }

    <T> T fx(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(task.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(60, TimeUnit.SECONDS);
    }

    void await(Callable<Boolean> check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime() < deadline) {
            if (fx(check)) return;
            Thread.sleep(50);
        }
        fail("Timeout CRM");
    }

    void rootLayout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    void snapshot(String path) throws Exception {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        var image = stage.getScene().snapshot(null);
        var out =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(path).toFile());
    }
}
