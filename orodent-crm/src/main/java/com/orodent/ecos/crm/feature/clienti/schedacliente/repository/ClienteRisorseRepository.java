package com.orodent.ecos.crm.feature.clienti.schedacliente.repository;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FornoClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.FresatoreClienteItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeCatalogItem;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteEditInput;
import com.orodent.ecos.crm.feature.clienti.schedacliente.dto.SchedaClienteModels.MaterialeClienteItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Repository for cliente resource catalogs and cliente-resource associations. */
public final class ClienteRisorseRepository {

    private final Database database;

    public ClienteRisorseRepository(Database database) {
        this.database = database;
    }

    public List<FornoCatalogItem> findForniCatalog() {
        String sql =
                "SELECT ID, TECNOLOGIA, MARCA, MODELLO FROM FORNI ORDER BY MARCA, MODELLO,"
                        + " TECNOLOGIA";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<FornoCatalogItem> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(
                        new FornoCatalogItem(
                                getUuid(resultSet, "ID"),
                                cleanResult(resultSet.getString("TECNOLOGIA")),
                                cleanResult(resultSet.getString("MARCA")),
                                cleanResult(resultSet.getString("MODELLO"))));
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento catalogo forni non riuscito.", e);
        }
    }

    public List<FresatoreCatalogItem> findFresatoriCatalog() {
        String sql = "SELECT ID, MARCA, MODELLO FROM FRESATORI ORDER BY MARCA, MODELLO";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<FresatoreCatalogItem> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(
                        new FresatoreCatalogItem(
                                getUuid(resultSet, "ID"),
                                cleanResult(resultSet.getString("MARCA")),
                                cleanResult(resultSet.getString("MODELLO"))));
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento catalogo fresatori non riuscito.", e);
        }
    }

    public List<MaterialeCatalogItem> findMaterialiCatalog() {
        String sql =
                "SELECT ID, MATERIALE, MARCHIO, MODELLO FROM MATERIALI_DI_CONSUMO ORDER BY"
                        + " MATERIALE, MARCHIO, MODELLO";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            List<MaterialeCatalogItem> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(
                        new MaterialeCatalogItem(
                                getUuid(resultSet, "ID"),
                                cleanResult(resultSet.getString("MATERIALE")),
                                cleanResult(resultSet.getString("MARCHIO")),
                                cleanResult(resultSet.getString("MODELLO"))));
            }
            return values;
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento catalogo materiali non riuscito.", e);
        }
    }

    public List<FornoClienteItem> findClienteForni(UUID clienteId) {
        if (clienteId == null) {
            return List.of();
        }
        String sql =
                """
SELECT CF.ID AS CLIENTE_FORNO_ID, F.ID AS FORNO_ID, F.TECNOLOGIA, CF.ANNO, F.MARCA, F.MODELLO, CF.NOTA
FROM CLIENTI_FORNI CF
JOIN FORNI F ON F.ID = CF.FORNO_ID
WHERE CF.CLIENTE_ID = ?
ORDER BY F.MARCA, F.MODELLO, F.TECNOLOGIA, CF.ANNO
""";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FornoClienteItem> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(
                            new FornoClienteItem(
                                    getUuid(resultSet, "CLIENTE_FORNO_ID"),
                                    getUuid(resultSet, "FORNO_ID"),
                                    cleanResult(resultSet.getString("TECNOLOGIA")),
                                    cleanResult(resultSet.getString("ANNO")),
                                    cleanResult(resultSet.getString("MARCA")),
                                    cleanResult(resultSet.getString("MODELLO")),
                                    cleanResult(resultSet.getString("NOTA"))));
                }
                return values;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento forni cliente non riuscito.", e);
        }
    }

    public void saveClienteForni(UUID clienteId, List<FornoClienteEditInput> forni) {
        database.claim(clienteId);
        if (clienteId == null) {
            return;
        }
        Connection connection = database.getConnection();
        try (PreparedStatement delete =
                        connection.prepareStatement(
                                "DELETE FROM CLIENTI_FORNI WHERE CLIENTE_ID = ?");
                PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO CLIENTI_FORNI (ID, CLIENTE_ID, FORNO_ID, ANNO, NOTA,"
                                        + " UPDATED_AT) VALUES (?, ?, ?, ?, ?, ?)")) {
            delete.setString(1, clienteId.toString());
            delete.executeUpdate();
            LocalDateTime now = LocalDateTime.now();
            for (FornoClienteEditInput forno : forni) {
                FornoClienteEditInput cleanForno = cleanForno(forno);
                if (!hasFornoData(cleanForno)) {
                    continue;
                }
                UUID fornoId = findOrCreateForno(cleanForno);
                insert.setString(1, idOrNew(cleanForno.id()).toString());
                insert.setString(2, clienteId.toString());
                insert.setString(3, fornoId.toString());
                insert.setString(4, nullableClean(cleanForno.anno()));
                insert.setString(5, nullableClean(cleanForno.nota()));
                insert.setTimestamp(6, Timestamp.valueOf(now));
                insert.addBatch();
            }
            insert.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Salvataggio forni cliente non riuscito.", e);
        }
    }

    public List<FresatoreClienteItem> findClienteFresatori(UUID clienteId) {
        if (clienteId == null) {
            return List.of();
        }
        String sql =
                """
SELECT CF.ID AS CLIENTE_FRESATORE_ID, F.ID AS FRESATORE_ID, F.MARCA, F.MODELLO, CF.NOTA
FROM CLIENTI_FRESATORI CF
JOIN FRESATORI F ON F.ID = CF.FRESATORE_ID
WHERE CF.CLIENTE_ID = ?
ORDER BY F.MARCA, F.MODELLO
""";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<FresatoreClienteItem> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(
                            new FresatoreClienteItem(
                                    getUuid(resultSet, "CLIENTE_FRESATORE_ID"),
                                    getUuid(resultSet, "FRESATORE_ID"),
                                    cleanResult(resultSet.getString("MARCA")),
                                    cleanResult(resultSet.getString("MODELLO")),
                                    cleanResult(resultSet.getString("NOTA"))));
                }
                return values;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento fresatori cliente non riuscito.", e);
        }
    }

    public void saveClienteFresatori(UUID clienteId, List<FresatoreClienteEditInput> fresatori) {
        database.claim(clienteId);
        if (clienteId == null) {
            return;
        }
        Connection connection = database.getConnection();
        try (PreparedStatement delete =
                        connection.prepareStatement(
                                "DELETE FROM CLIENTI_FRESATORI WHERE CLIENTE_ID = ?");
                PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO CLIENTI_FRESATORI (ID, CLIENTE_ID, FRESATORE_ID, NOTA,"
                                        + " UPDATED_AT) VALUES (?, ?, ?, ?, ?)")) {
            delete.setString(1, clienteId.toString());
            delete.executeUpdate();
            LocalDateTime now = LocalDateTime.now();
            for (FresatoreClienteEditInput fresatore : fresatori) {
                FresatoreClienteEditInput cleanFresatore = cleanFresatore(fresatore);
                if (!hasFresatoreData(cleanFresatore)) {
                    continue;
                }
                UUID fresatoreId = findOrCreateFresatore(cleanFresatore);
                insert.setString(1, idOrNew(cleanFresatore.id()).toString());
                insert.setString(2, clienteId.toString());
                insert.setString(3, fresatoreId.toString());
                insert.setString(4, nullableClean(cleanFresatore.nota()));
                insert.setTimestamp(5, Timestamp.valueOf(now));
                insert.addBatch();
            }
            insert.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Salvataggio fresatori cliente non riuscito.", e);
        }
    }

    public List<MaterialeClienteItem> findClienteMateriali(UUID clienteId) {
        if (clienteId == null) {
            return List.of();
        }
        String sql =
                """
SELECT CM.ID AS CLIENTE_MATERIALE_ID, M.ID AS MATERIALE_ID, M.MATERIALE, M.MARCHIO, M.MODELLO, CM.CONSUMO, CM.FREQUENZA_ACQUISTO, CM.NOTA
FROM CLIENTI_MATERIALI CM
JOIN MATERIALI_DI_CONSUMO M ON M.ID = CM.MATERIALE_ID
WHERE CM.CLIENTE_ID = ?
ORDER BY M.MATERIALE, M.MARCHIO, M.MODELLO, CM.CONSUMO, CM.FREQUENZA_ACQUISTO
""";
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            statement.setString(1, clienteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MaterialeClienteItem> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(
                            new MaterialeClienteItem(
                                    getUuid(resultSet, "CLIENTE_MATERIALE_ID"),
                                    getUuid(resultSet, "MATERIALE_ID"),
                                    cleanResult(resultSet.getString("MATERIALE")),
                                    cleanResult(resultSet.getString("MARCHIO")),
                                    cleanResult(resultSet.getString("MODELLO")),
                                    cleanResult(resultSet.getString("CONSUMO")),
                                    cleanResult(resultSet.getString("FREQUENZA_ACQUISTO")),
                                    cleanResult(resultSet.getString("NOTA"))));
                }
                return values;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Caricamento materiali cliente non riuscito.", e);
        }
    }

    public void saveClienteMateriali(UUID clienteId, List<MaterialeClienteEditInput> materiali) {
        database.claim(clienteId);
        if (clienteId == null) {
            return;
        }
        Connection connection = database.getConnection();
        try (PreparedStatement delete =
                        connection.prepareStatement(
                                "DELETE FROM CLIENTI_MATERIALI WHERE CLIENTE_ID = ?");
                PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO CLIENTI_MATERIALI (ID, CLIENTE_ID, MATERIALE_ID, NOTA,"
                                    + " CONSUMO, FREQUENZA_ACQUISTO, UPDATED_AT) VALUES (?, ?, ?,"
                                    + " ?, ?, ?, ?)")) {
            delete.setString(1, clienteId.toString());
            delete.executeUpdate();
            LocalDateTime now = LocalDateTime.now();
            for (MaterialeClienteEditInput materiale : materiali) {
                MaterialeClienteEditInput cleanMateriale = cleanMateriale(materiale);
                if (!hasMaterialeData(cleanMateriale)) {
                    continue;
                }
                UUID materialeId = findOrCreateMateriale(cleanMateriale);
                insert.setString(1, idOrNew(cleanMateriale.id()).toString());
                insert.setString(2, clienteId.toString());
                insert.setString(3, materialeId.toString());
                insert.setString(4, nullableClean(cleanMateriale.nota()));
                insert.setString(5, nullableClean(cleanMateriale.consumo()));
                insert.setString(6, nullableClean(cleanMateriale.frequenzaAcquisto()));
                insert.setTimestamp(7, Timestamp.valueOf(now));
                insert.addBatch();
            }
            insert.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Salvataggio materiali cliente non riuscito.", e);
        }
    }

    private UUID findOrCreateForno(FornoClienteEditInput forno) throws SQLException {
        String findSql = "SELECT ID FROM FORNI WHERE TECNOLOGIA = ? AND MARCA = ? AND MODELLO = ?";
        try (PreparedStatement find = database.getConnection().prepareStatement(findSql)) {
            bindFornoIdentity(find, forno);
            try (ResultSet resultSet = find.executeQuery()) {
                if (resultSet.next()) {
                    return getUuid(resultSet, "ID");
                }
            }
        }

        UUID fornoId = UUID.randomUUID();
        try (PreparedStatement insert =
                database.getConnection()
                        .prepareStatement(
                                "INSERT INTO FORNI (ID, TECNOLOGIA, MARCA, MODELLO) VALUES (?, ?,"
                                        + " ?, ?)")) {
            insert.setString(1, fornoId.toString());
            bindFornoIdentity(insert, forno, 2);
            insert.executeUpdate();
        }
        return fornoId;
    }

    private void bindFornoIdentity(PreparedStatement statement, FornoClienteEditInput forno)
            throws SQLException {
        bindFornoIdentity(statement, forno, 1);
    }

    private void bindFornoIdentity(
            PreparedStatement statement, FornoClienteEditInput forno, int startIndex)
            throws SQLException {
        statement.setString(startIndex, forno.tecnologia());
        statement.setString(startIndex + 1, forno.marca());
        statement.setString(startIndex + 2, forno.modello());
    }

    private FornoClienteEditInput cleanForno(FornoClienteEditInput forno) {
        return new FornoClienteEditInput(
                forno.id(),
                forno.fornoId(),
                normalize(forno.tecnologia()),
                normalize(forno.anno()),
                normalize(forno.marca()),
                normalize(forno.modello()),
                normalize(forno.nota()));
    }

    private boolean hasFornoData(FornoClienteEditInput forno) {
        return !forno.tecnologia().isBlank()
                || !forno.anno().isBlank()
                || !forno.marca().isBlank()
                || !forno.modello().isBlank();
    }

    private UUID findOrCreateFresatore(FresatoreClienteEditInput fresatore) throws SQLException {
        String findSql = "SELECT ID FROM FRESATORI WHERE MARCA = ? AND MODELLO = ?";
        try (PreparedStatement find = database.getConnection().prepareStatement(findSql)) {
            bindFresatoreIdentity(find, fresatore);
            try (ResultSet resultSet = find.executeQuery()) {
                if (resultSet.next()) {
                    return getUuid(resultSet, "ID");
                }
            }
        }

        UUID fresatoreId = UUID.randomUUID();
        try (PreparedStatement insert =
                database.getConnection()
                        .prepareStatement(
                                "INSERT INTO FRESATORI (ID, MARCA, MODELLO) VALUES (?, ?, ?)")) {
            insert.setString(1, fresatoreId.toString());
            bindFresatoreIdentity(insert, fresatore, 2);
            insert.executeUpdate();
        }
        return fresatoreId;
    }

    private void bindFresatoreIdentity(
            PreparedStatement statement, FresatoreClienteEditInput fresatore) throws SQLException {
        bindFresatoreIdentity(statement, fresatore, 1);
    }

    private void bindFresatoreIdentity(
            PreparedStatement statement, FresatoreClienteEditInput fresatore, int startIndex)
            throws SQLException {
        statement.setString(startIndex, fresatore.marca());
        statement.setString(startIndex + 1, fresatore.modello());
    }

    private FresatoreClienteEditInput cleanFresatore(FresatoreClienteEditInput fresatore) {
        return new FresatoreClienteEditInput(
                fresatore.id(),
                fresatore.fresatoreId(),
                normalize(fresatore.marca()),
                normalize(fresatore.modello()),
                normalize(fresatore.nota()));
    }

    private boolean hasFresatoreData(FresatoreClienteEditInput fresatore) {
        return !fresatore.marca().isBlank() || !fresatore.modello().isBlank();
    }

    private UUID findOrCreateMateriale(MaterialeClienteEditInput materiale) throws SQLException {
        String findSql =
                "SELECT ID FROM MATERIALI_DI_CONSUMO WHERE MATERIALE = ? AND MARCHIO = ? AND"
                        + " MODELLO = ?";
        try (PreparedStatement find = database.getConnection().prepareStatement(findSql)) {
            bindMaterialeIdentity(find, materiale);
            try (ResultSet resultSet = find.executeQuery()) {
                if (resultSet.next()) {
                    return getUuid(resultSet, "ID");
                }
            }
        }

        UUID materialeId = UUID.randomUUID();
        try (PreparedStatement insert =
                database.getConnection()
                        .prepareStatement(
                                "INSERT INTO MATERIALI_DI_CONSUMO (ID, MATERIALE, MARCHIO, MODELLO)"
                                        + " VALUES (?, ?, ?, ?)")) {
            insert.setString(1, materialeId.toString());
            bindMaterialeIdentity(insert, materiale, 2);
            insert.executeUpdate();
        }
        return materialeId;
    }

    private void bindMaterialeIdentity(
            PreparedStatement statement, MaterialeClienteEditInput materiale) throws SQLException {
        bindMaterialeIdentity(statement, materiale, 1);
    }

    private void bindMaterialeIdentity(
            PreparedStatement statement, MaterialeClienteEditInput materiale, int startIndex)
            throws SQLException {
        statement.setString(startIndex, materiale.materiale());
        statement.setString(startIndex + 1, materiale.marchio());
        statement.setString(startIndex + 2, materiale.modello());
    }

    private MaterialeClienteEditInput cleanMateriale(MaterialeClienteEditInput materiale) {
        return new MaterialeClienteEditInput(
                materiale.id(),
                materiale.materialeId(),
                normalize(materiale.materiale()),
                normalize(materiale.marchio()),
                normalize(materiale.modello()),
                normalize(materiale.consumo()),
                normalize(materiale.frequenzaAcquisto()),
                normalize(materiale.nota()));
    }

    private boolean hasMaterialeData(MaterialeClienteEditInput materiale) {
        return !materiale.materiale().isBlank()
                || !materiale.marchio().isBlank()
                || !materiale.modello().isBlank();
    }

    private UUID getUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private String cleanResult(String value) {
        return value == null ? "" : value;
    }

    private UUID idOrNew(UUID id) {
        return id == null ? UUID.randomUUID() : id;
    }

    private String nullableClean(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
