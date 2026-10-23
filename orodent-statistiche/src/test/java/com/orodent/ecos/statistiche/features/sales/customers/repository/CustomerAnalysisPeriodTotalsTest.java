package com.orodent.ecos.statistiche.features.sales.customers.repository;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CustomerAnalysisPeriodTotalsTest {
    @Test
    void comparesExactDatesCountsDocumentsAndExcludesReturnsAndOtherCustomers() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-customer-period-test.log");
        String url = "jdbc:derby:memory:customerPeriods" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("CREATE SCHEMA VENDITE");
                sql.executeUpdate("CREATE TABLE VENDITE.VENDITE_DETTAGLIO (data_vendita DATE, codice_cliente VARCHAR(20), "
                        + "documento_id VARCHAR(20), importo_netto DECIMAL(15,2), quantita DECIMAL(15,3), tipo_operazione VARCHAR(20))");
                sql.executeUpdate("INSERT INTO VENDITE.VENDITE_DETTAGLIO VALUES "
                        + "(DATE('2025-01-01'),'A','P1',100,10,'VENDITA'),"
                        + "(DATE('2025-10-07'),'A','P2',200,20,'VENDITA'),"
                        + "(DATE('2025-10-08'),'A','P3',9000,900,'VENDITA'),"
                        + "(DATE('2025-12-10'),'A','P4',47000,4700,'VENDITA'),"
                        + "(DATE('2026-01-01'),'A','N1',40,4,'VENDITA'),"
                        + "(DATE('2026-10-07'),'A','N2',60,6,'VENDITA'),"
                        + "(DATE('2026-10-07'),'A','N2',50,5,'VENDITA'),"
                        + "(DATE('2026-10-08'),'A','N3',9000,900,'VENDITA'),"
                        + "(DATE('2026-02-01'),'A','R1',500,50,'RESO'),"
                        + "(DATE('2026-02-01'),'A','R2',500,50,'NOTA_CREDITO'),"
                        + "(DATE('2026-02-01'),'B','B1',500,50,'VENDITA')");
            }
            var repository = new CustomerAnalysisRepositoryImpl(connection);
            var annual = repository.loadPeriodTotals("A", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 7));
            assertEquals(new BigDecimal("150.00"), annual.revenue());
            assertEquals(new BigDecimal("15.000"), annual.quantity());
            assertEquals(2, annual.orders());
            var previous = repository.loadPeriodTotals("A", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 10, 7));
            assertEquals(new BigDecimal("300.00"), previous.revenue());
            var rolling = repository.loadPeriodTotals("A", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 30));
            assertEquals(new BigDecimal("56240.00"), rolling.revenue());
            assertEquals(4, rolling.orders());
            var absent = repository.loadPeriodTotals("UNKNOWN", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 7));
            assertEquals(0, absent.orders());
            assertEquals(0, absent.revenue().signum());
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException expected) { if (!"08006".equals(expected.getSQLState())) throw expected; }
        }
    }
}
