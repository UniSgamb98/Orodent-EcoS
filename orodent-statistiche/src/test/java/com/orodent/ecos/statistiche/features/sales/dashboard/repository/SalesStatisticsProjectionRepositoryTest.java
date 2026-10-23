package com.orodent.ecos.statistiche.features.sales.dashboard.repository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SalesStatisticsProjectionRepositoryTest {
    @Test
    void aggregatesDailyNetRevenueAndReadsCoverageForTheRequestedYear() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-projection-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:projection" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE vendite_dettaglio (data_vendita DATE, importo_netto DECIMAL(15,2), tipo_operazione VARCHAR(20))");
                statement.executeUpdate("INSERT INTO vendite_dettaglio VALUES "
                        + "(DATE('2025-12-31'), 100, 'VENDITA'),"
                        + "(DATE('2026-01-10'), 100, 'VENDITA'),"
                        + "(DATE('2026-01-10'), 20, 'RESO'),"
                        + "(DATE('2026-01-10'), -10, 'NOTA_CREDITO'),"
                        + "(DATE('2026-02-05'), 50, 'VENDITA')");
            }
            SalesStatisticsRepository repository = new SalesStatisticsRepositoryImpl(connection);
            var coverage = repository.loadDataCoverage(2026).orElseThrow();
            assertEquals(LocalDate.of(2026, 1, 10), coverage.from());
            assertEquals(LocalDate.of(2026, 2, 5), coverage.through());
            assertTrue(repository.loadDataCoverage(2024).isEmpty());
            var history = repository.loadDailyRevenueHistory(2026, 2026);
            assertEquals(2, history.size());
            assertEquals(new BigDecimal("70.00"), history.getFirst().revenue());
            assertEquals(LocalDate.of(2026, 1, 10), history.getFirst().date());
            assertEquals(new BigDecimal("50.00"), history.getLast().revenue());
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException expected) { if (!"08006".equals(expected.getSQLState())) throw expected; }
        }
    }
}
