package com.orodent.ecos.statistiche.features.sales.customers.repository;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPurchaseDatesRepositoryTest {
    @Test void batchQueryDeduplicatesDaysAndExcludesReturnsOtherYearsAndDatesAfterTheCutoff() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-follow-up-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:followUp" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE SCHEMA VENDITE");
                statement.executeUpdate("CREATE TABLE VENDITE.VENDITE_DETTAGLIO (codice_cliente VARCHAR(20), data_vendita DATE, tipo_operazione VARCHAR(20))");
                statement.executeUpdate("INSERT INTO VENDITE.VENDITE_DETTAGLIO VALUES "
                        + "('A', DATE('2025-12-31'), 'VENDITA'),"
                        + "('A', DATE('2026-01-01'), 'VENDITA'),"
                        + "('A', DATE('2026-01-01'), 'VENDITA'),"
                        + "('A', DATE('2026-01-11'), 'VENDITA'),"
                        + "('A', DATE('2026-01-12'), 'RESO'),"
                        + "('A', DATE('2026-01-21'), 'VENDITA'),"
                        + "('B', DATE('2026-01-20'), 'VENDITA')");
            }
            var dates = new CustomerAnalysisRepositoryImpl(connection).loadPurchaseDates(2026, LocalDate.of(2026, 1, 20));
            assertEquals(2, dates.size());
            assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11)), dates.get("A"));
            assertEquals(List.of(LocalDate.of(2026, 1, 20)), dates.get("B"));
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException exception) { if (!"08006".equals(exception.getSQLState())) throw exception; }
        }
    }
}
