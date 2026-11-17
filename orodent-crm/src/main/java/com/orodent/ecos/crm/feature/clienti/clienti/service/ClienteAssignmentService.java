package com.orodent.ecos.crm.feature.clienti.clienti.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.feature.auth.login.service.LoginService;
import com.orodent.ecos.crm.feature.auth.login.service.LoginService.LoginUser;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Changes only the client's assignment, preserving the authors of historical activities. */
public final class ClienteAssignmentService {
    public record Assignment(
            UUID clientId,
            String clientName,
            UUID operatorId,
            String operatorName,
            int revision,
            List<LoginUser> activeOperators) {}

    private final Database db;

    public ClienteAssignmentService(Database db) {
        this.db = db;
    }

    public Assignment load(UUID clientId) {
        try (var q =
                db.getConnection()
                        .prepareStatement(
                                "SELECT"
                                    + " C.RAGIONE_SOCIALE,C.OPERATORE_ID,C.VERSIONE_MODIFICA,O.USERNAME,O.NOME,O.COGNOME"
                                    + " FROM CRM.CLIENTI C LEFT JOIN ECOS.OPERATORI O ON"
                                    + " O.ID=C.OPERATORE_ID WHERE C.ID=?")) {
            q.setString(1, clientId.toString());
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException(
                            "Anagrafica non trovata. Ricarica l'elenco.");
                String operator = r.getString(2);
                return new Assignment(
                        clientId,
                        r.getString(1),
                        operator == null ? null : UUID.fromString(operator),
                        operator == null
                                ? null
                                : new LoginUser(
                                                UUID.fromString(operator),
                                                java.util.Objects.requireNonNullElse(
                                                        r.getString(4), ""),
                                                java.util.Objects.requireNonNullElse(
                                                        r.getString(5), ""),
                                                java.util.Objects.requireNonNullElse(
                                                        r.getString(6), ""))
                                        .displayName(),
                        r.getInt(3),
                        List.copyOf(new LoginService(db).loadUsers()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Caricamento assegnazione non riuscito.", e);
        }
    }

    public void assign(Assignment original, UUID operatorId) {
        if (operatorId == null)
            throw new IllegalArgumentException("Seleziona un operatore attivo.");
        try (var q =
                db.getConnection()
                        .prepareStatement("SELECT ATTIVO FROM ECOS.OPERATORI WHERE ID=?")) {
            q.setString(1, operatorId.toString());
            try (var r = q.executeQuery()) {
                if (!r.next() || r.getInt(1) != 1)
                    throw new IllegalArgumentException(
                            "L'operatore scelto non è più attivo. Riapri la scelta"
                                    + " dell'operatore.");
            }
            db.observed(original.clientId(), original.revision());
            db.claim(original.clientId());
            try (var update =
                    db.getConnection()
                            .prepareStatement(
                                    "UPDATE CRM.CLIENTI SET"
                                        + " OPERATORE_ID=?,UPDATED_AT=CURRENT_TIMESTAMP WHERE"
                                        + " ID=?")) {
                update.setString(1, operatorId.toString());
                update.setString(2, original.clientId().toString());
                if (update.executeUpdate() != 1) throw new Database.Conflict();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Salvataggio assegnazione non riuscito.", e);
        }
    }
}
