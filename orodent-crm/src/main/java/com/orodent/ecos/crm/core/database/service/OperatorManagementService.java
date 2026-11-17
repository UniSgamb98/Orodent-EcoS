package com.orodent.ecos.crm.core.database.service;

import com.orodent.ecos.crm.core.database.Database;

import java.sql.*;
import java.util.*;

/** Stable operator identities; referenced operators can be disabled without losing history. */
public final class OperatorManagementService {
    public record Operator(UUID id, String name, boolean active, boolean linked, boolean current) {}

    private final Database db;

    public OperatorManagementService(Database db) {
        this.db = db;
    }

    public List<Operator> load() {
        db.observeCatalogue("OPERATORI");
        try (var q = db.getConnection().createStatement();
                var r =
                        q.executeQuery(
                                "SELECT ID,USERNAME,ATTIVO FROM ECOS.OPERATORI ORDER BY"
                                        + " UPPER(USERNAME),ID")) {
            var result = new ArrayList<Operator>();
            while (r.next()) {
                UUID id = UUID.fromString(r.getString(1));
                result.add(
                        new Operator(
                                id,
                                r.getString(2),
                                r.getInt(3) == 1,
                                linked(id),
                                db.operators().isCurrent(id)));
            }
            return List.copyOf(result);
        } catch (SQLException e) {
            throw new IllegalStateException("Lettura operatori non riuscita.", e);
        }
    }

    public UUID create(String value) {
        String name = name(value);
        db.observeCatalogue("OPERATORI");
        db.claimCatalogue("OPERATORI");
        unique(name, null);
        UUID id = UUID.randomUUID();
        try (var q =
                db.getConnection()
                        .prepareStatement(
                                "INSERT INTO ECOS.OPERATORI(ID,NOME,USERNAME) VALUES (?,?,?)")) {
            q.setString(1, id.toString());
            q.setString(2, name);
            q.setString(3, name);
            q.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Creazione operatore non riuscita.", e);
        }
    }

    public void rename(UUID id, String value, String expectedName) {
        String name = name(value);
        db.claimCatalogue("OPERATORI");
        unique(name, id);
        try (var q =
                db.getConnection()
                        .prepareStatement(
                                "UPDATE ECOS.OPERATORI SET USERNAME=?,NOME=?,COGNOME=NULL WHERE"
                                        + " ID=? AND USERNAME=?")) {
            q.setString(1, name);
            q.setString(2, name);
            q.setString(3, id.toString());
            q.setString(4, expectedName);
            if (q.executeUpdate() != 1) throw new Database.Conflict();
        } catch (SQLException e) {
            throw new IllegalStateException("Rinomina operatore non riuscita.", e);
        }
    }

    public void active(UUID id, boolean active) {
        if (!active && db.operators().isCurrent(id))
            throw new IllegalArgumentException(
                    "Prima di disattivare l'operatore in uso, seleziona un altro operatore.");
        db.claimCatalogue("OPERATORI");
        try (var q =
                db.getConnection()
                        .prepareStatement("UPDATE ECOS.OPERATORI SET ATTIVO=? WHERE ID=?")) {
            q.setInt(1, active ? 1 : 0);
            q.setString(2, id.toString());
            require(q.executeUpdate());
        } catch (SQLException e) {
            throw new IllegalStateException("Aggiornamento operatore non riuscito.", e);
        }
    }

    public void remove(UUID id) {
        if (db.operators().isCurrent(id))
            throw new IllegalArgumentException(
                    "Prima di rimuovere l'operatore in uso, seleziona un altro operatore.");
        db.claimCatalogue("OPERATORI");
        if (linked(id)) {
            active(id, false);
            return;
        }
        try (var q = db.getConnection().prepareStatement("DELETE FROM ECOS.OPERATORI WHERE ID=?")) {
            q.setString(1, id.toString());
            require(q.executeUpdate());
        } catch (SQLException e) {
            throw new IllegalStateException("Rimozione operatore non riuscita.", e);
        }
    }

    private boolean linked(UUID id) {
        try {
            for (String table :
                    List.of(
                            "CLIENTI",
                            "NOTE_CLIENTE",
                            "INTERAZIONI",
                            "CLIENTI_PREFERITI",
                            "VISTE_SALVATE")) {
                try (var q =
                        db.getConnection()
                                .prepareStatement(
                                        "SELECT 1 FROM CRM."
                                                + table
                                                + " WHERE OPERATORE_ID=? FETCH FIRST ROW ONLY")) {
                    q.setString(1, id.toString());
                    try (var r = q.executeQuery()) {
                        if (r.next()) return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new IllegalStateException("Verifica dei collegamenti operatore non riuscita.", e);
        }
    }

    private void unique(String name, UUID except) {
        try (var q =
                db.getConnection()
                        .prepareStatement(
                                "SELECT ID FROM ECOS.OPERATORI WHERE UPPER(TRIM(USERNAME))=?")) {
            q.setString(1, name.toUpperCase(Locale.ROOT));
            try (var r = q.executeQuery()) {
                while (r.next())
                    if (!r.getString(1).equals(Objects.toString(except, "")))
                        throw new IllegalArgumentException(
                                "Esiste già un operatore con questo nome, anche con maiuscole"
                                        + " diverse.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Verifica del nome operatore non riuscita.", e);
        }
    }

    public static String name(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isBlank() || value.length() > 80)
            throw new IllegalArgumentException(
                    "Il nome operatore è obbligatorio, massimo 80 caratteri.");
        return value;
    }

    private static void require(int rows) {
        if (rows != 1)
            throw new IllegalArgumentException("Operatore non trovato. Ricarica la pagina.");
    }
}
