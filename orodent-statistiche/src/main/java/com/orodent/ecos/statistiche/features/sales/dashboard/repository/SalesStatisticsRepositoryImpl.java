package com.orodent.ecos.statistiche.features.sales.dashboard.repository;

import com.orodent.ecos.database.repository.RepositoryException;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.MonthlySales;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesFilter;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesRankingItem;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.SalesSummary;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.orodent.ecos.statistiche.features.sales.dashboard.model.TopCustomerHistory;
import com.orodent.ecos.statistiche.features.sales.projection.model.DailyRevenueValue;
import com.orodent.ecos.statistiche.features.sales.projection.model.DailyMetricValue;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;

public final class SalesStatisticsRepositoryImpl implements SalesStatisticsRepository {

    private static final String SIGNED_REVENUE = """
            CASE tipo_operazione
                WHEN 'VENDITA' THEN COALESCE(importo_netto, 0)
                ELSE -ABS(COALESCE(importo_netto, 0))
            END
            """;
    private static final String SIGNED_QUANTITY = """
            CASE tipo_operazione
                WHEN 'VENDITA' THEN quantita
                ELSE -ABS(quantita)
            END
            """;

    private final Connection conn;

    public SalesStatisticsRepositoryImpl(Connection conn) {
        this.conn = Objects.requireNonNull(conn, "conn");
    }

    @Override
    public List<Integer> findAvailableYears() {
        String sql = """
                SELECT DISTINCT YEAR(data_vendita) AS anno
                FROM VENDITE.VENDITE_DETTAGLIO
                ORDER BY anno DESC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Integer> years = new ArrayList<>();
            while (rs.next()) {
                years.add(rs.getInt("anno"));
            }
            return List.copyOf(years);
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante la lettura degli anni disponibili", e);
        }
    }

    @Override
    public List<TopCustomerHistory> loadTopCustomerHistory(int selectedYear, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Il limite deve essere positivo");
        String sql = """
                SELECT v.codice_cliente, COALESCE(MAX(c.ragione_sociale),v.codice_cliente) AS nome,
                       YEAR(v.data_vendita) AS anno, SUM(%s) AS fatturato
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE v.codice_cliente IN (
                    SELECT codice_cliente FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita)=?
                    GROUP BY codice_cliente ORDER BY SUM(%s) DESC, codice_cliente
                    FETCH FIRST %d ROWS ONLY)
                GROUP BY v.codice_cliente,YEAR(v.data_vendita) ORDER BY anno,v.codice_cliente
                """.formatted(SIGNED_REVENUE, SIGNED_REVENUE, limit);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedYear);
            try (ResultSet rs = ps.executeQuery()) {
                List<TopCustomerHistory> values = new ArrayList<>();
                while (rs.next()) values.add(new TopCustomerHistory(rs.getString("codice_cliente"),
                        rs.getString("nome"), rs.getInt("anno"), rs.getBigDecimal("fatturato")));
                return List.copyOf(values);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante la lettura dello storico dei migliori clienti", e);
        }
    }

    @Override
    public Optional<SalesDataCoverage> loadDataCoverage(int year) {
        String sql = "SELECT MIN(data_vendita), MAX(data_vendita) FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita)=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getDate(1) == null) return Optional.empty();
                return Optional.of(new SalesDataCoverage(rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante la lettura della copertura annuale", e);
        }
    }

    @Override
    public List<DailyRevenueValue> loadDailyRevenueHistory(int fromYear, int toYear) {
        String sql = """
                SELECT data_vendita, SUM(%s) AS fatturato FROM VENDITE.VENDITE_DETTAGLIO
                WHERE YEAR(data_vendita) BETWEEN ? AND ?
                GROUP BY data_vendita ORDER BY data_vendita
                """.formatted(SIGNED_REVENUE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fromYear);
            ps.setInt(2, toYear);
            try (ResultSet rs = ps.executeQuery()) {
                List<DailyRevenueValue> values = new ArrayList<>();
                while (rs.next()) values.add(new DailyRevenueValue(rs.getDate("data_vendita").toLocalDate(),
                        rs.getBigDecimal("fatturato")));
                return List.copyOf(values);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante la lettura dello storico giornaliero vendite", e);
        }
    }

    @Override
    public List<DailyMetricValue> loadDailyQuantityHistory(int fromYear, int toYear) {
        String sql = """
                SELECT data_vendita giorno, SUM(%s) valore
                FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita) BETWEEN ? AND ?
                GROUP BY data_vendita ORDER BY data_vendita
                """.formatted(SIGNED_QUANTITY);
        return loadDailyMetric(sql, fromYear, toYear);
    }

    @Override
    public List<DailyMetricValue> loadDailyDocumentHistory(int fromYear, int toYear) {
        // Assign each document once per year, even if its lines span several dates.
        String sql = """
                SELECT prima giorno, COUNT(*) valore FROM (
                    SELECT YEAR(data_vendita) anno, documento_id, MIN(data_vendita) prima
                    FROM VENDITE.VENDITE_DETTAGLIO WHERE YEAR(data_vendita) BETWEEN ? AND ?
                    GROUP BY YEAR(data_vendita), documento_id
                ) documenti GROUP BY prima ORDER BY prima
                """;
        return loadDailyMetric(sql, fromYear, toYear);
    }

    private List<DailyMetricValue> loadDailyMetric(String sql, int fromYear, int toYear) {
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, fromYear); statement.setInt(2, toYear);
            try (ResultSet result = statement.executeQuery()) {
                List<DailyMetricValue> values = new ArrayList<>();
                while (result.next()) values.add(new DailyMetricValue(result.getDate("giorno").toLocalDate(), result.getBigDecimal("valore")));
                return List.copyOf(values);
            }
        } catch (SQLException failure) {
            throw new RepositoryException("Errore durante il caricamento dello storico per le proiezioni", failure);
        }
    }

    @Override
    public SalesSummary loadSummary(SalesFilter filter) {
        String sql = """
                SELECT COALESCE(SUM(%s), 0) AS fatturato,
                       COALESCE(SUM(%s), 0) AS quantita,
                       COALESCE(SUM(sconto_importo), 0) AS sconto
                FROM VENDITE.VENDITE_DETTAGLIO
                WHERE data_vendita BETWEEN ? AND ?
                """.formatted(SIGNED_REVENUE, SIGNED_QUANTITY);
        BigDecimal revenue;
        BigDecimal quantity;
        BigDecimal discount;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setDateRange(ps, filter);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                revenue = rs.getBigDecimal("fatturato");
                quantity = rs.getBigDecimal("quantita");
                discount = rs.getBigDecimal("sconto");
            }
            return new SalesSummary(
                    revenue,
                    quantity,
                    countDistinct("codice_cliente", filter),
                    countDistinct("documento_id", filter),
                    discount
            );
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante il calcolo del riepilogo vendite", e);
        }
    }

    private int countDistinct(String column, SalesFilter filter) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT " + column + ") AS totale "
                + "FROM VENDITE.VENDITE_DETTAGLIO WHERE data_vendita BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setDateRange(ps, filter);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt("totale");
            }
        }
    }

    @Override
    public List<MonthlySales> loadMonthlySales(SalesFilter filter) {
        String sql = """
                SELECT MONTH(data_vendita) AS mese,
                       SUM(%s) AS fatturato,
                       SUM(%s) AS quantita,
                       COUNT(DISTINCT documento_id) AS documenti
                FROM VENDITE.VENDITE_DETTAGLIO
                WHERE data_vendita BETWEEN ? AND ?
                GROUP BY MONTH(data_vendita)
                ORDER BY mese
                """.formatted(SIGNED_REVENUE, SIGNED_QUANTITY);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setDateRange(ps, filter);
            try (ResultSet rs = ps.executeQuery()) {
                List<MonthlySales> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new MonthlySales(
                            rs.getInt("mese"),
                            rs.getBigDecimal("fatturato"),
                            rs.getBigDecimal("quantita"),
                            rs.getInt("documenti")
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante il calcolo dell'andamento mensile", e);
        }
    }

    @Override
    public List<SalesRankingItem> loadTopCustomers(SalesFilter filter, int limit) {
        return loadRanking(
                "v.codice_cliente",
                "COALESCE(MAX(c.ragione_sociale), v.codice_cliente)",
                "LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente = v.codice_cliente",
                filter,
                limit
        );
    }

    @Override
    public List<SalesRankingItem> loadTopProducts(SalesFilter filter, int limit) {
        return loadRanking(
                "v.codice_prodotto",
                "MAX(v.descrizione_prodotto)",
                "",
                filter,
                limit
        );
    }

    private List<SalesRankingItem> loadRanking(
            String codeColumn,
            String descriptionExpression,
            String joinClause,
            SalesFilter filter,
            int limit
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException("Il limite deve essere positivo");
        }
        String sql = """
                SELECT %s AS codice,
                       %s AS descrizione,
                       SUM(%s) AS fatturato,
                       SUM(%s) AS quantita,
                       COUNT(DISTINCT documento_id) AS documenti
                FROM VENDITE.VENDITE_DETTAGLIO v
                %s
                WHERE v.data_vendita BETWEEN ? AND ?
                GROUP BY %s
                ORDER BY fatturato DESC
                FETCH FIRST %d ROWS ONLY
                """.formatted(
                codeColumn,
                descriptionExpression,
                SIGNED_REVENUE,
                SIGNED_QUANTITY,
                joinClause,
                codeColumn,
                limit
        );
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setDateRange(ps, filter);
            try (ResultSet rs = ps.executeQuery()) {
                List<SalesRankingItem> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new SalesRankingItem(
                            rs.getString("codice"),
                            rs.getString("descrizione"),
                            rs.getBigDecimal("fatturato"),
                            rs.getBigDecimal("quantita"),
                            rs.getInt("documenti"),
                            BigDecimal.ZERO
                    ));
                }
                return List.copyOf(result);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Errore durante il calcolo della classifica vendite", e);
        }
    }

    private void setDateRange(PreparedStatement ps, SalesFilter filter) throws SQLException {
        ps.setDate(1, Date.valueOf(filter.from()));
        ps.setDate(2, Date.valueOf(filter.to()));
    }
}
