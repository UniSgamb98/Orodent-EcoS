package com.orodent.ecos.crm.feature.auth.login.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.service.OperatorManagementService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LoginService {

    public static final String AUTO_LOGIN_OPERATOR_ID = "crm.auto-login.operator-id";

    private final Database database;

    public LoginService(Database database) {
        this.database = database;
    }

    public List<LoginUser> loadUsers() {
        String sql =
                "SELECT ID, NOME, COGNOME, USERNAME FROM ECOS.OPERATORI WHERE ATTIVO = 1 ORDER BY"
                        + " USERNAME";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<LoginUser> users = new ArrayList<>();
            while (resultSet.next()) {
                users.add(
                        new LoginUser(
                                UUID.fromString(resultSet.getString("ID")),
                                valueOrEmpty(resultSet.getString("USERNAME")),
                                valueOrEmpty(resultSet.getString("NOME")),
                                valueOrEmpty(resultSet.getString("COGNOME"))));
            }
            return users;
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento utenti.", e);
        }
    }

    public LoginUser createUser(String username) {
        String name = OperatorManagementService.name(username);
        UUID id = new OperatorManagementService(database).create(name);
        return new LoginUser(id, name, name, "");
    }

    public Optional<LoginUser> automaticUser(String configuredId) {
        if (configuredId == null || configuredId.isBlank()) return Optional.empty();
        UUID id;
        try {
            String value = configuredId.strip();
            id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "Accesso automatico: "
                            + AUTO_LOGIN_OPERATOR_ID
                            + " deve contenere un ID operatore valido. Copialo dalle impostazioni"
                            + " CRM.",
                    failure);
        }
        return Optional.of(
                loadUsers().stream()
                        .filter(user -> user.id().equals(id))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Accesso automatico: operatore '"
                                                        + id
                                                        + "' non trovato o disattivato. Seleziona"
                                                        + " un operatore oppure correggi "
                                                        + AUTO_LOGIN_OPERATOR_ID
                                                        + ".")));
    }

    public LoginSession login(LoginUser user) {
        if (user == null) {
            throw new IllegalArgumentException("Seleziona un utente.");
        }

        return new LoginSession(user.id(), user.username(), user.displayName());
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    public record LoginUser(UUID id, String username, String nome, String cognome) {
        public String displayName() {
            String fullName = String.join(" ", nome, cognome).trim();
            return fullName.isBlank() || fullName.equalsIgnoreCase(username)
                    ? username
                    : fullName + " (" + username + ")";
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    public record LoginSession(UUID userId, String username, String displayName) {}
}
