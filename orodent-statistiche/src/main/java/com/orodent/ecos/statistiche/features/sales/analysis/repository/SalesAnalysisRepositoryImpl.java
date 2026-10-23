package com.orodent.ecos.statistiche.features.sales.analysis.repository;

import com.orodent.ecos.database.model.TipoOperazione;
import com.orodent.ecos.database.repository.RepositoryException;
import com.orodent.ecos.statistiche.features.sales.analysis.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SalesAnalysisRepositoryImpl implements SalesAnalysisRepository {
    private final Connection connection;

    public SalesAnalysisRepositoryImpl(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    @Override
    public List<Integer> findAvailableYears() {
        return queryYears("SELECT DISTINCT YEAR(data_vendita) anno FROM VENDITE.VENDITE_DETTAGLIO ORDER BY anno DESC");
    }

    @Override
    public List<CustomerAnalysisItem> loadCustomers(int year) {
        String sql = """
                SELECT v.codice_cliente codice, COALESCE(MAX(c.ragione_sociale), v.codice_cliente) nome,
                       SUM(CASE WHEN v.tipo_operazione='VENDITA' THEN COALESCE(v.importo_netto,0)
                                ELSE -ABS(COALESCE(v.importo_netto,0)) END) fatturato,
                       SUM(CASE WHEN v.tipo_operazione='VENDITA' THEN v.quantita ELSE -ABS(v.quantita) END) quantita,
                       COUNT(DISTINCT v.documento_id) documenti, MAX(v.data_vendita) ultima_vendita,
                       MAX(c.listino) listino
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE YEAR(v.data_vendita)=? GROUP BY v.codice_cliente ORDER BY fatturato DESC
                """;
        try (PreparedStatement ps = prepareYear(sql, year); ResultSet rs = ps.executeQuery()) {
            List<CustomerAnalysisItem> items = new ArrayList<>();
            while (rs.next()) items.add(new CustomerAnalysisItem(rs.getString("codice"), rs.getString("nome"),
                    rs.getBigDecimal("fatturato"), rs.getBigDecimal("quantita"), rs.getInt("documenti"),
                    rs.getDate("ultima_vendita").toLocalDate(), rs.getString("listino")));
            return List.copyOf(items);
        } catch (SQLException e) { throw failure("clienti", e); }
    }

    @Override
    public List<ProductAnalysisItem> loadProducts(int year) {
        String sql = """
                SELECT codice_prodotto codice, MAX(descrizione_prodotto) descrizione,
                       SUM(CASE WHEN tipo_operazione='VENDITA' THEN COALESCE(importo_netto,0)
                                ELSE -ABS(COALESCE(importo_netto,0)) END) fatturato,
                       SUM(CASE WHEN tipo_operazione='VENDITA' THEN quantita ELSE -ABS(quantita) END) quantita,
                       COUNT(DISTINCT codice_cliente) clienti, COUNT(DISTINCT documento_id) documenti
                FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita)=?
                GROUP BY codice_prodotto ORDER BY fatturato DESC
                """;
        try (PreparedStatement ps = prepareYear(sql, year); ResultSet rs = ps.executeQuery()) {
            List<ProductAnalysisItem> items = new ArrayList<>();
            while (rs.next()) items.add(new ProductAnalysisItem(rs.getString("codice"), rs.getString("descrizione"),
                    rs.getBigDecimal("fatturato"), rs.getBigDecimal("quantita"), rs.getInt("clienti"),
                    rs.getInt("documenti")));
            return List.copyOf(items);
        } catch (SQLException e) { throw failure("prodotti", e); }
    }

    @Override
    public AnalysisSummary loadDiscountSummary(int year) {
        String sql = """
                SELECT COALESCE(SUM(sconto_importo),0) sconto,
                       COALESCE(AVG(sconto_percentuale),0) percentuale,
                       COALESCE(SUM(importo_netto),0) netto, COUNT(DISTINCT documento_id) documenti
                FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita)=? AND tipo_operazione='VENDITA'
                """;
        return summary(sql, year, "sconto", "percentuale", "netto", "documenti", "sconti");
    }

    @Override
    public List<DiscountAnalysisItem> loadDiscountsByCustomer(int year) {
        String sql = """
                SELECT v.codice_cliente codice, COALESCE(MAX(c.ragione_sociale),v.codice_cliente) nome,
                       COALESCE(SUM(v.importo_netto),0) fatturato, COALESCE(SUM(v.sconto_importo),0) sconto,
                       COALESCE(AVG(v.sconto_percentuale),0) percentuale
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE YEAR(v.data_vendita)=? AND v.tipo_operazione='VENDITA'
                GROUP BY v.codice_cliente ORDER BY sconto DESC
                """;
        try (PreparedStatement ps = prepareYear(sql, year); ResultSet rs = ps.executeQuery()) {
            List<DiscountAnalysisItem> items = new ArrayList<>();
            while (rs.next()) items.add(new DiscountAnalysisItem(rs.getString("codice"), rs.getString("nome"),
                    rs.getBigDecimal("fatturato"), rs.getBigDecimal("sconto"), rs.getBigDecimal("percentuale")));
            return List.copyOf(items);
        } catch (SQLException e) { throw failure("sconti", e); }
    }

    @Override
    public AnalysisSummary loadReturnSummary(int year) {
        String sql = """
                SELECT COALESCE(SUM(ABS(importo_netto)),0) importo, COALESCE(SUM(ABS(quantita)),0) quantita,
                       CAST(0 AS DECIMAL(15,2)) vuoto, COUNT(DISTINCT documento_id) documenti
                FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita)=? AND tipo_operazione<>'VENDITA'
                """;
        return summary(sql, year, "importo", "quantita", "vuoto", "documenti", "resi");
    }

    @Override
    public List<ReturnAnalysisItem> loadReturnsByCustomer(int year) {
        String sql = """
                SELECT v.codice_cliente codice, COALESCE(MAX(c.ragione_sociale),v.codice_cliente) nome,
                       SUM(ABS(COALESCE(v.importo_netto,0))) importo, SUM(ABS(v.quantita)) quantita,
                       COUNT(DISTINCT v.documento_id) documenti
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE YEAR(v.data_vendita)=? AND v.tipo_operazione<>'VENDITA'
                GROUP BY v.codice_cliente ORDER BY importo DESC
                """;
        try (PreparedStatement ps = prepareYear(sql, year); ResultSet rs = ps.executeQuery()) {
            List<ReturnAnalysisItem> items = new ArrayList<>();
            while (rs.next()) items.add(new ReturnAnalysisItem(rs.getString("codice"), rs.getString("nome"),
                    rs.getBigDecimal("importo"), rs.getBigDecimal("quantita"), rs.getInt("documenti")));
            return List.copyOf(items);
        } catch (SQLException e) { throw failure("resi e note di credito", e); }
    }

    @Override
    public List<SalesArchiveItem> loadArchive(int year, String search, int limit) {
        String term = search == null ? "" : search.trim().toUpperCase();
        String sql = """
                SELECT v.data_vendita, v.numero_documento, v.codice_cliente,
                       COALESCE(c.ragione_sociale,v.codice_cliente) nome_cliente,
                       v.codice_prodotto, v.descrizione_prodotto, v.quantita,
                       v.sconto_percentuale, v.importo_netto, v.tipo_operazione
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE YEAR(v.data_vendita)=? AND (
                    ?='' OR UPPER(v.numero_documento) LIKE ? OR UPPER(v.codice_cliente) LIKE ?
                    OR UPPER(COALESCE(c.ragione_sociale,'')) LIKE ? OR UPPER(v.codice_prodotto) LIKE ?
                    OR UPPER(COALESCE(v.descrizione_prodotto,'')) LIKE ?)
                ORDER BY v.data_vendita DESC, v.numero_documento DESC FETCH FIRST %d ROWS ONLY
                """.formatted(limit);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + term + "%";
            ps.setInt(1, year); ps.setString(2, term);
            for (int i = 3; i <= 7; i++) ps.setString(i, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<SalesArchiveItem> items = new ArrayList<>();
                while (rs.next()) items.add(new SalesArchiveItem(rs.getDate("data_vendita").toLocalDate(),
                        rs.getString("numero_documento"), rs.getString("codice_cliente"),
                        rs.getString("nome_cliente"), rs.getString("codice_prodotto"),
                        rs.getString("descrizione_prodotto"), rs.getBigDecimal("quantita"),
                        rs.getBigDecimal("sconto_percentuale"), rs.getBigDecimal("importo_netto"),
                        TipoOperazione.valueOf(rs.getString("tipo_operazione"))));
                return List.copyOf(items);
            }
        } catch (SQLException e) { throw failure("archivio vendite", e); }
    }

    private AnalysisSummary summary(String sql, int year, String first, String second,
                                    String third, String count, String context) {
        try (PreparedStatement ps = prepareYear(sql, year); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new AnalysisSummary(rs.getBigDecimal(first), rs.getBigDecimal(second),
                    rs.getBigDecimal(third), rs.getInt(count));
        } catch (SQLException e) { throw failure(context, e); }
    }

    private PreparedStatement prepareYear(String sql, int year) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setInt(1, year);
        return statement;
    }

    private List<Integer> queryYears(String sql) {
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Integer> years = new ArrayList<>();
            while (rs.next()) years.add(rs.getInt("anno"));
            return List.copyOf(years);
        } catch (SQLException e) { throw failure("anni disponibili", e); }
    }

    private RepositoryException failure(String context, SQLException cause) {
        return new RepositoryException("Errore durante la lettura di " + context, cause);
    }
}
