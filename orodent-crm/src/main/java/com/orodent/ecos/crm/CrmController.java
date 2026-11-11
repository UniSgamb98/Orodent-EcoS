package com.orodent.ecos.crm;

import com.orodent.ecos.crm.core.async.AsyncLoader;
import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.derby.*;
import com.orodent.ecos.crm.core.database.service.*;
import com.orodent.ecos.crm.core.session.FeatureSessionStateStore;
import com.orodent.ecos.crm.core.ui.AppSidebar;
import com.orodent.ecos.crm.feature.auth.login.service.LoginService;
import com.orodent.ecos.crm.feature.auth.login.view.LoginView;
import com.orodent.ecos.crm.feature.calendario.controller.CalendarioController;
import com.orodent.ecos.crm.feature.calendario.service.CalendarioService;
import com.orodent.ecos.crm.feature.calendario.view.CalendarioView;
import com.orodent.ecos.crm.feature.clienti.clienti.controller.ClientiController;
import com.orodent.ecos.crm.feature.clienti.clienti.service.*;
import com.orodent.ecos.crm.feature.clienti.clienti.view.ClientiView;
import com.orodent.ecos.crm.feature.clienti.navigator.ClientiNav;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.controller.NuovoClienteController;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.service.NuovoClienteService;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.view.NuovoClienteView;
import com.orodent.ecos.crm.feature.clienti.schedacliente.controller.SchedaClienteController;
import com.orodent.ecos.crm.feature.clienti.schedacliente.service.SchedaClienteService;
import com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView;
import com.orodent.ecos.crm.feature.impostazioni.controller.ImpostazioniController;
import com.orodent.ecos.crm.feature.impostazioni.service.ImpostazioniService;
import com.orodent.ecos.crm.feature.impostazioni.view.ImpostazioniView;
import com.orodent.ecos.module.*;

import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.util.*;

final class CrmController implements ModuleSession, ClientiNav {
    private final ModuleContext context;
    private final Database database;
    private final BorderPane root = new BorderPane();
    private final Label operator = new Label(), status = new Label();
    private final Button home = new Button("⌂ Home Orodent-EcoS"),
            change = new Button("Cambia operatore"),
            reload = new Button("Ricarica pagina");
    private final FeatureSessionStateStore states = new FeatureSessionStateStore();
    private AsyncLoader tasks;
    private EditorGuard guard;
    private boolean initial, disposed;
    private Runnable currentRoute;
    private Window window;
    private final EventHandler<WindowEvent> closing =
            e -> {
                if (!canLeave()) e.consume();
            };
    private final ChangeListener<Window> windowListener = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneListener =
            (o, b, a) -> {
                if (b != null) b.windowProperty().removeListener(windowListener);
                if (a != null) a.windowProperty().addListener(windowListener);
                attachWindow(a == null ? null : a.getWindow());
            };

    CrmController(ModuleContext context) {
        this.context = context;
        database = new Database(context.connections(), context.operators());
        root.setId("crm-module");
        root.getStyleClass().add("crm-module");
        root.getStylesheets().add(getClass().getResource("/crm/css/global.css").toExternalForm());
        root.getStylesheets().add(getClass().getResource("/crm/css/ecos.css").toExternalForm());
        home.setId("crm-home");
        home.getStyleClass().add("crm-primary");
        change.setId("crm-change-operator");
        reload.setId("crm-reload");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox header = new HBox(14, home, new Label("CRM"), gap, operator, reload, change);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("crm-header");
        root.setTop(header);
        status.setWrapText(true);
        status.setPadding(new Insets(6, 20, 6, 20));
        root.setBottom(status);
        home.setOnAction(
                e -> {
                    if (canLeave()) context.showHome().run();
                });
        change.setOnAction(
                e -> {
                    if (canLeave()) {
                        context.operators().clear();
                        showLogin();
                    }
                });
        reload.setOnAction(
                e -> {
                    if (canLeave() && currentRoute != null) currentRoute.run();
                });
        root.sceneProperty().addListener(sceneListener);
        if (context.operators().current().isPresent()) verifyOperator();
        else showLogin();
    }

    private void verifyOperator() {
        LoginView page = new LoginView();
        install(page, false, this::verifyOperator);
        var login = new LoginService(database);
        var selected = context.operators().current().orElseThrow();
        tasks.run(
                login::loadUsers,
                users -> {
                    if (users.stream().anyMatch(u -> u.id().equals(selected.id()))) showClienti();
                    else {
                        context.operators().clear();
                        showLogin();
                    }
                },
                this::error);
    }

    private void showLogin() {
        LoginView page = new LoginView();
        page.setId("crm-login");
        install(page, false, this::showLogin);
        var service = new LoginService(database);
        page.getLoginButton().setId("crm-select-operator");
        tasks.run(service::loadUsers, page::setUsers, e -> page.showError(message(e)));
        page.createOperatorButton().setId("crm-create-operator");
        page.createOperatorButton()
                .setOnAction(
                        e -> {
                            TextInputDialog dialog = new TextInputDialog();
                            dialog.setHeaderText("Nome del nuovo operatore");
                            if (window != null) dialog.initOwner(window);
                            dialog.showAndWait()
                                    .ifPresent(
                                            name ->
                                                    tasks.run(
                                                            () -> service.createUser(name),
                                                            user -> {
                                                                context.operators()
                                                                        .select(
                                                                                user.id(),
                                                                                user.username());
                                                                showClienti();
                                                            },
                                                            error ->
                                                                    page.showError(
                                                                            message(error))));
                        });
        page.getLoginButton().setText("Usa questo operatore");
        page.getLoginButton()
                .setOnAction(
                        e -> {
                            var user = page.getSelectedUser();
                            if (user == null) return;
                            context.operators().select(user.id(), user.username());
                            showClienti();
                        });
    }

    public void showClienti() {
        if (!navigate()) return;
        ClientiView page = new ClientiView();
        install(
                page,
                false,
                this::showClienti,
                "features/clienti.css",
                "features/lista-clienti.css");
        configure(page.getSidebar());
        var current = database.operators();
        var controller =
                new ClientiController(
                        page,
                        tasks,
                        this,
                        new ClientiService(database),
                        states,
                        current,
                        new VistaSalvataService(database, current),
                        new ClientiViewStateCodec());
        tasks.onClose(controller::dispose);
        controller.loadPreviewClientsAsync();
    }

    public void showNuovoCliente() {
        if (!navigate()) return;
        NuovoClienteView page = new NuovoClienteView();
        page.setId("crm-new-lead");
        install(
                page,
                true,
                this::showNuovoCliente,
                "features/clienti.css",
                "features/nuovo-cliente.css");
        configure(page.getSidebar());
        new NuovoClienteController(
                page,
                this,
                new NuovoClienteService(
                        new ClientePersistenceService(database),
                        database.operators(),
                        new DerbyTipoClienteQuery(database),
                        new DerbyStatoTrattativaQuery(database)),
                tasks);
    }

    public void showSchedaCliente(UUID id) {
        if (!navigate()) return;
        SchedaClienteView page = new SchedaClienteView();
        page.setId("crm-profile");
        install(
                page,
                true,
                () -> showSchedaCliente(id),
                "features/clienti.css",
                "features/scheda-cliente.css");
        configure(page.getSidebar());
        new SchedaClienteController(page, this, new SchedaClienteService(database), id, tasks);
    }

    private void showCalendar() {
        if (!navigate()) return;
        CalendarioView page = new CalendarioView();
        page.setId("crm-calendar");
        install(page, false, this::showCalendar, "features/calendario.css");
        configure(page.getSidebar());
        new CalendarioController(
                page, new CalendarioService(database), this::showSchedaCliente, tasks);
    }

    private void showSettings() {
        if (!navigate()) return;
        ImpostazioniView page = new ImpostazioniView();
        page.setId("crm-settings");
        install(page, true, this::showSettings, "features/impostazioni.css");
        configure(page.getSidebar());
        new ImpostazioniController(page, new ImpostazioniService(database), tasks);
    }

    private void configure(AppSidebar sidebar) {
        sidebar.getDashboardButton().setVisible(false);
        sidebar.getDashboardButton().setManaged(false);
        sidebar.getDashboardButton().setText("Contatti e lead");
        sidebar.getDashboardButton().setOnAction(e -> showClienti());
        sidebar.getClientsButton().setOnAction(e -> showClienti());
        sidebar.getClientsButton().setText("Contatti e lead");
        sidebar.getClientsButton().setId("crm-nav-leads");
        sidebar.getCalendarButton().setOnAction(e -> showCalendar());
        sidebar.getCalendarButton().setId("crm-nav-calendar");
        sidebar.getSettingsButton().setOnAction(e -> showSettings());
        sidebar.getSettingsButton().setId("crm-nav-settings");
        sidebar.getContactsButton().setVisible(false);
        sidebar.getContactsButton().setManaged(false);
        sidebar.getOpportunitiesButton().setVisible(false);
        sidebar.getOpportunitiesButton().setManaged(false);
        sidebar.getActivitiesButton().setVisible(false);
        sidebar.getActivitiesButton().setManaged(false);
        sidebar.getReportsButton().setVisible(false);
        sidebar.getReportsButton().setManaged(false);
    }

    private boolean navigate() {
        return !disposed && (tasks == null || canLeave());
    }

    private void install(Parent page, boolean editable, Runnable route, String... css) {
        if (tasks != null) tasks.close();
        currentRoute = route;
        guard = editable ? new EditorGuard(page) : null;
        initial = true;
        tasks = new AsyncLoader(database, context.workers());
        page.getStylesheets()
                .addAll(
                        Arrays.stream(css)
                                .map(p -> getClass().getResource("/crm/css/" + p).toExternalForm())
                                .toList());
        root.setCenter(page);
        operator.setText(
                context.operators()
                        .current()
                        .map(o -> "Operatore: " + o.username())
                        .orElse("Seleziona operatore"));
        status.setText("");
        tasks.onClean(
                () -> {
                    if (guard != null) guard.clean();
                });
        tasks.onChanged(
                () -> {
                    boolean busy = tasks.busy();
                    page.setDisable(busy || tasks.requiresReload());
                    reload.setDisable(busy);
                    change.setDisable(busy);
                    if (!busy && initial) {
                        initial = false;
                        if (guard != null) guard.clean();
                    }
                    if (busy) status.setText("Operazione in corso…");
                    else if (status.getText().equals("Operazione in corso…")) status.setText("");
                });
    }

    private boolean canLeave() {
        if (tasks != null && tasks.busy()) {
            status.setText("Attendi il completamento dell'operazione in corso.");
            return false;
        }
        if (guard == null || !guard.dirty()) return true;
        Alert dialog =
                new Alert(
                        Alert.AlertType.CONFIRMATION,
                        "Vuoi scartare le modifiche non salvate?",
                        ButtonType.CANCEL,
                        ButtonType.OK);
        dialog.setHeaderText("Modifiche al CRM");
        if (window != null) dialog.initOwner(window);
        boolean leave = dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        if (leave) guard.clean();
        return leave;
    }

    private void error(Throwable error) {
        status.setText(message(error));
    }

    static String message(Throwable error) {
        return com.orodent.ecos.crm.core.async.CrmErrors.message(error);
    }

    private void attachWindow(Window next) {
        if (window != null) window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
        window = next;
        if (next != null) next.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
    }

    public Parent root() {
        return root;
    }

    public void close() {
        disposed = true;
        if (tasks != null) tasks.close();
        root.sceneProperty().removeListener(sceneListener);
        if (root.getScene() != null)
            root.getScene().windowProperty().removeListener(windowListener);
        attachWindow(null);
        home.setOnAction(null);
        change.setOnAction(null);
        reload.setOnAction(null);
    }
}
