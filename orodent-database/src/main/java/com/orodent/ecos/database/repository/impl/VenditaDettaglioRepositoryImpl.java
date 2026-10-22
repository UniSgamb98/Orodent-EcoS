package com.orodent.ecos.database.repository.impl;

import com.orodent.ecos.database.model.TipoOperazione;
import com.orodent.ecos.database.model.VenditaDettaglio;
import com.orodent.ecos.database.repository.RepositoryException;
import com.orodent.ecos.database.repository.VenditaDettaglioRepository;

import java.sql.Connection;
import java.sql.BatchUpdateException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record VenditaDettaglioRepositoryImpl(Connection conn) implements VenditaDettaglioRepository {

    private static final int INSERT_BATCH_SIZE = 500;

    public VenditaDettaglioRepositoryImpl(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
    }

    @Override
    public VenditaDettaglio insert(VenditaDettaglio vendita) {
        String sql = """
                INSERT INTO vendite_dettaglio (
                    sorgente,
                    documento_id,
                    numero_documento,
                    numero_riga,
                    data_vendita,
                    codice_cliente,
                    codice_prodotto,
                    descrizione_prodotto,
                    categoria_prodotto,
                    quantita,
                    prezzo_unitario,
                    sconto_percentuale,
                    sconto_importo,
                    importo_netto,
                    tipo_operazione,
                    canale,
                    agente,
                    importato_il
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setValues(ps, vendita);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new RepositoryException("ID della vendita non generato");
                }
                return withId(vendita, keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'inserimento della vendita", e);
        }
    }

    @Override
    public int insertAll(List<VenditaDettaglio> vendite) {
        if (vendite.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO vendite_dettaglio (
                    sorgente,
                    documento_id,
                    numero_documento,
                    numero_riga,
                    data_vendita,
                    codice_cliente,
                    codice_prodotto,
                    descrizione_prodotto,
                    categoria_prodotto,
                    quantita,
                    prezzo_unitario,
                    sconto_percentuale,
                    sconto_importo,
                    importo_netto,
                    tipo_operazione,
                    canale,
                    agente,
                    importato_il
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int inserted = 0;
            for (int batchStart = 0; batchStart < vendite.size(); batchStart += INSERT_BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + INSERT_BATCH_SIZE, vendite.size());
                ps.clearBatch();
                for (int index = batchStart; index < batchEnd; index++) {
                    setValues(ps, vendite.get(index));
                    ps.addBatch();
                }
                try {
                    int[] results = ps.executeBatch();
                    inserted += countSuccessfulStatements(results);
                } catch (BatchUpdateException e) {
                    int failedIndex = Math.min(batchStart + successfulBeforeFailure(e), vendite.size() - 1);
                    throw new RepositoryException(batchFailureMessage(vendite.get(failedIndex), e), e);
                }
            }
            return inserted;
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'inserimento delle vendite: " + databaseMessage(e), e);
        }
    }

    private int countSuccessfulStatements(int[] results) {
        int successful = 0;
        for (int result : results) {
            if (result != Statement.EXECUTE_FAILED) {
                successful++;
            }
        }
        return successful;
    }

    private int successfulBeforeFailure(BatchUpdateException exception) {
        int successful = 0;
        for (int result : exception.getUpdateCounts()) {
            if (result == Statement.EXECUTE_FAILED) {
                break;
            }
            successful++;
        }
        return successful;
    }

    private String batchFailureMessage(VenditaDettaglio vendita, SQLException exception) {
        return "Errore alla riga CSV " + (vendita.numeroRiga() + 1)
                + " (documento " + vendita.numeroDocumento()
                + ", prodotto " + vendita.codiceProdotto() + "): "
                + databaseMessage(exception);
    }

    private String databaseMessage(SQLException exception) {
        SQLException current = exception;
        String message = exception.getMessage();
        while (current.getNextException() != null) {
            current = current.getNextException();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
        }
        String state = current.getSQLState();
        return (message == null || message.isBlank() ? "errore database" : message)
                + (state == null ? "" : " [SQLState " + state + "]");
    }

    @Override
    public void update(VenditaDettaglio vendita) {
        if (vendita.venditaId() == null) {
            throw new IllegalArgumentException("venditaId è obbligatorio per l'aggiornamento");
        }

        String sql = """
                UPDATE vendite_dettaglio
                SET sorgente = ?,
                    documento_id = ?,
                    numero_documento = ?,
                    numero_riga = ?,
                    data_vendita = ?,
                    codice_cliente = ?,
                    codice_prodotto = ?,
                    descrizione_prodotto = ?,
                    categoria_prodotto = ?,
                    quantita = ?,
                    prezzo_unitario = ?,
                    sconto_percentuale = ?,
                    sconto_importo = ?,
                    importo_netto = ?,
                    tipo_operazione = ?,
                    canale = ?,
                    agente = ?,
                    importato_il = ?
                WHERE vendita_id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setValues(ps, vendita);
            ps.setLong(19, vendita.venditaId());

            if (ps.executeUpdate() == 0) {
                throw new RepositoryException("Vendita non trovata: " + vendita.venditaId());
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'aggiornamento della vendita", e);
        }
    }

    @Override
    public Optional<VenditaDettaglio> findById(long venditaId) {
        String sql = selectColumns() + " WHERE vendita_id = ?";
        List<VenditaDettaglio> vendite = query(sql, ps -> ps.setLong(1, venditaId));
        return vendite.stream().findFirst();
    }

    @Override
    public Optional<VenditaDettaglio> findByDocumento(
            String sorgente,
            String documentoId,
            int numeroRiga
    ) {
        String sql = selectColumns() + """
                 WHERE sorgente = ?
                   AND documento_id = ?
                   AND numero_riga = ?
                """;

        List<VenditaDettaglio> vendite = query(sql, ps -> {
            ps.setString(1, sorgente);
            ps.setString(2, documentoId);
            ps.setInt(3, numeroRiga);
        });
        return vendite.stream().findFirst();
    }

    @Override
    public List<VenditaDettaglio> findAll() {
        String sql = selectColumns() + " ORDER BY vendita_id";
        return query(sql, ps -> {
        });
    }

    @Override
    public List<VenditaDettaglio> findByDateRange(LocalDate dal, LocalDate al) {
        String sql = selectColumns() + """
                 WHERE data_vendita BETWEEN ? AND ?
                 ORDER BY data_vendita, vendita_id
                """;

        return queryByDateRange(sql, dal, al, 1);
    }

    @Override
    public List<VenditaDettaglio> findByClienteAndDateRange(
            String codiceCliente,
            LocalDate dal,
            LocalDate al
    ) {
        String sql = selectColumns() + """
                 WHERE codice_cliente = ?
                   AND data_vendita BETWEEN ? AND ?
                 ORDER BY data_vendita, vendita_id
                """;

        checkDateRange(dal, al);
        return query(sql, ps -> {
            ps.setString(1, codiceCliente);
            setDateRange(ps, dal, al, 2);
        });
    }

    @Override
    public List<VenditaDettaglio> findByProdottoAndDateRange(
            String codiceProdotto,
            LocalDate dal,
            LocalDate al
    ) {
        String sql = selectColumns() + """
                 WHERE codice_prodotto = ?
                   AND data_vendita BETWEEN ? AND ?
                 ORDER BY data_vendita, vendita_id
                """;

        checkDateRange(dal, al);
        return query(sql, ps -> {
            ps.setString(1, codiceProdotto);
            setDateRange(ps, dal, al, 2);
        });
    }

    @Override
    public List<VenditaDettaglio> findByClienteAndProdottoAndDateRange(
            String codiceCliente,
            String codiceProdotto,
            LocalDate dal,
            LocalDate al
    ) {
        String sql = selectColumns() + """
                 WHERE codice_cliente = ?
                   AND codice_prodotto = ?
                   AND data_vendita BETWEEN ? AND ?
                 ORDER BY data_vendita, vendita_id
                """;

        checkDateRange(dal, al);
        return query(sql, ps -> {
            ps.setString(1, codiceCliente);
            ps.setString(2, codiceProdotto);
            setDateRange(ps, dal, al, 3);
        });
    }

    @Override
    public List<VenditaDettaglio> findByCategoriaAndDateRange(
            String categoria,
            LocalDate dal,
            LocalDate al
    ) {
        String sql = selectColumns() + """
                 WHERE categoria_prodotto = ?
                   AND data_vendita BETWEEN ? AND ?
                 ORDER BY data_vendita, vendita_id
                """;

        checkDateRange(dal, al);
        return query(sql, ps -> {
            ps.setString(1, categoria);
            setDateRange(ps, dal, al, 2);
        });
    }

    @Override
    public void deleteById(long venditaId) {
        String sql = "DELETE FROM vendite_dettaglio WHERE vendita_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, venditaId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante l'eliminazione della vendita", e);
        }
    }

    @Override
    public int deleteByDateRange(LocalDate from, LocalDate through) {
        if (from == null || through == null || through.isBefore(from)) {
            throw new IllegalArgumentException("Intervallo di cancellazione non valido");
        }
        String sql = """
                DELETE FROM vendite_dettaglio
                WHERE data_vendita >= ?
                  AND data_vendita < ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(through.plusDays(1)));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(
                    "Errore durante l'eliminazione delle vendite dal " + from + " al " + through,
                    e
            );
        }
    }

    private List<VenditaDettaglio> query(String sql, StatementBinder binder) {
        List<VenditaDettaglio> vendite = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            binder.bind(ps);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    vendite.add(mapRow(rs));
                }
            }
            return vendite;
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante la lettura delle vendite", e);
        }
    }

    private List<VenditaDettaglio> queryByDateRange(
            String sql,
            LocalDate dal,
            LocalDate al,
            int firstParameter
    ) {
        checkDateRange(dal, al);
        return query(sql, ps -> setDateRange(ps, dal, al, firstParameter));
    }

    private void checkDateRange(LocalDate dal, LocalDate al) {
        Objects.requireNonNull(dal, "dal");
        Objects.requireNonNull(al, "al");
        if (dal.isAfter(al)) {
            throw new IllegalArgumentException("La data iniziale non può essere successiva a quella finale");
        }
    }

    private void setDateRange(
            PreparedStatement ps,
            LocalDate dal,
            LocalDate al,
            int firstParameter
    ) throws SQLException {
        ps.setDate(firstParameter, Date.valueOf(dal));
        ps.setDate(firstParameter + 1, Date.valueOf(al));
    }

    private void setValues(PreparedStatement ps, VenditaDettaglio vendita) throws SQLException {
        ps.setString(1, vendita.sorgente());
        ps.setString(2, vendita.documentoId());
        ps.setString(3, vendita.numeroDocumento());
        ps.setInt(4, vendita.numeroRiga());
        ps.setDate(5, Date.valueOf(vendita.dataVendita()));
        ps.setString(6, vendita.codiceCliente());
        ps.setString(7, vendita.codiceProdotto());
        ps.setString(8, vendita.descrizioneProdotto());
        ps.setString(9, vendita.categoriaProdotto());
        ps.setBigDecimal(10, vendita.quantita());
        ps.setBigDecimal(11, vendita.prezzoUnitario());
        ps.setBigDecimal(12, vendita.scontoPercentuale());
        ps.setBigDecimal(13, vendita.scontoImporto());
        ps.setBigDecimal(14, vendita.importoNetto());
        TipoOperazione tipoOperazione = vendita.tipoOperazione() == null
                ? TipoOperazione.VENDITA
                : vendita.tipoOperazione();
        ps.setString(15, tipoOperazione.name());
        ps.setString(16, vendita.canale());
        ps.setString(17, vendita.agente());
        ps.setTimestamp(18, Timestamp.valueOf(
                vendita.importatoIl() == null ? LocalDateTime.now() : vendita.importatoIl()
        ));
    }

    private VenditaDettaglio mapRow(ResultSet rs) throws SQLException {
        Timestamp importatoIl = rs.getTimestamp("importato_il");

        return new VenditaDettaglio(
                rs.getLong("vendita_id"),
                rs.getString("sorgente"),
                rs.getString("documento_id"),
                rs.getString("numero_documento"),
                rs.getInt("numero_riga"),
                rs.getDate("data_vendita").toLocalDate(),
                rs.getString("codice_cliente"),
                rs.getString("codice_prodotto"),
                rs.getString("descrizione_prodotto"),
                rs.getString("categoria_prodotto"),
                rs.getBigDecimal("quantita"),
                rs.getBigDecimal("prezzo_unitario"),
                rs.getBigDecimal("sconto_percentuale"),
                rs.getBigDecimal("sconto_importo"),
                rs.getBigDecimal("importo_netto"),
                TipoOperazione.valueOf(rs.getString("tipo_operazione")),
                rs.getString("canale"),
                rs.getString("agente"),
                importatoIl == null ? null : importatoIl.toLocalDateTime()
        );
    }

    private VenditaDettaglio withId(VenditaDettaglio vendita, long venditaId) {
        return new VenditaDettaglio(
                venditaId,
                vendita.sorgente(),
                vendita.documentoId(),
                vendita.numeroDocumento(),
                vendita.numeroRiga(),
                vendita.dataVendita(),
                vendita.codiceCliente(),
                vendita.codiceProdotto(),
                vendita.descrizioneProdotto(),
                vendita.categoriaProdotto(),
                vendita.quantita(),
                vendita.prezzoUnitario(),
                vendita.scontoPercentuale(),
                vendita.scontoImporto(),
                vendita.importoNetto(),
                vendita.tipoOperazione(),
                vendita.canale(),
                vendita.agente(),
                vendita.importatoIl()
        );
    }

    private String selectColumns() {
        return """
                SELECT vendita_id,
                       sorgente,
                       documento_id,
                       numero_documento,
                       numero_riga,
                       data_vendita,
                       codice_cliente,
                       codice_prodotto,
                       descrizione_prodotto,
                       categoria_prodotto,
                       quantita,
                       prezzo_unitario,
                       sconto_percentuale,
                       sconto_importo,
                       importo_netto,
                       tipo_operazione,
                       canale,
                       agente,
                       importato_il
                FROM vendite_dettaglio
                """;
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }
}
