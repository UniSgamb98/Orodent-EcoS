package com.orodent.ecos.statistiche.features.sales.dashboard.repository;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class SalesStatisticsMetricHistoryTest {
    @Test void quantityUsesNetValuesAndDocumentsAreDistinctPerYearAcrossDatesAndRows() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-metric-projection-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:metrics" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE SCHEMA VENDITE");
                statement.executeUpdate("CREATE TABLE VENDITE.VENDITE_DETTAGLIO (data_vendita DATE, documento_id VARCHAR(20), tipo_operazione VARCHAR(20), quantita DECIMAL(15,3))");
                statement.executeUpdate("INSERT INTO VENDITE.VENDITE_DETTAGLIO VALUES "
                        + "(DATE('2025-01-01'),'D1','VENDITA',10),"
                        + "(DATE('2025-01-01'),'D1','VENDITA',5),"
                        + "(DATE('2025-02-01'),'D1','VENDITA',2),"
                        + "(DATE('2025-12-31'),'D2','VENDITA',20),"
                        + "(DATE('2026-01-01'),'D1','VENDITA',10.125),"
                        + "(DATE('2026-01-01'),'R1','RESO',3),"
                        + "(DATE('2026-01-01'),'N1','NOTA_CREDITO',-2),"
                        + "(DATE('2026-01-03'),'D1','VENDITA',1)");
            }
            var repository = new SalesStatisticsRepositoryImpl(connection);
            var quantity = repository.loadDailyQuantityHistory(2026, 2026);
            assertEquals(new BigDecimal("5.125"), quantity.getFirst().value());
            assertEquals(new BigDecimal("1.000"), quantity.getLast().value());
            var documents = repository.loadDailyDocumentHistory(2025, 2026);
            assertEquals(3, documents.size());
            assertEquals(LocalDate.of(2025, 1, 1), documents.getFirst().date());
            assertEquals(1, documents.getFirst().value().intValueExact());
            assertEquals(1, documents.get(1).value().intValueExact());
            assertEquals(3, documents.getLast().value().intValueExact());
            assertEquals(5, documents.stream().mapToInt(value -> value.value().intValueExact()).sum());
            assertEquals(1, repository.loadDailyDocumentHistory(2026, 2026).size());
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException expected) { if (!"08006".equals(expected.getSQLState())) throw expected; }
        }
    }
}
