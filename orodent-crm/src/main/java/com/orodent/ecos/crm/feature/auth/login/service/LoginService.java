package com.orodent.ecos.crm.feature.auth.login.service;

import com.orodent.ecos.crm.core.database.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoginService {

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
        String name = username == null ? "" : username.strip();
        if (name.isBlank() || name.length() > 80)
            throw new IllegalArgumentException(
                    "Il nome operatore è obbligatorio, massimo 80 caratteri.");
        UUID id = UUID.randomUUID();
        try (var q =
                database.getConnection()
                        .prepareStatement(
                                "INSERT INTO ECOS.OPERATORI(ID,NOME,USERNAME) VALUES (?,?,?)")) {
            q.setString(1, id.toString());
            q.setString(2, name);
            q.setString(3, name);
            q.executeUpdate();
            return new LoginUser(id, name, name, "");
        } catch (SQLException error) {
            throw new IllegalArgumentException(
                    "Creazione operatore non riuscita. Verifica che il nome non sia già"
                        + " utilizzato.",
                    error);
        }
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
            return fullName.isBlank() ? username : fullName + " (" + username + ")";
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    public record LoginSession(UUID userId, String username, String displayName) {}
}
