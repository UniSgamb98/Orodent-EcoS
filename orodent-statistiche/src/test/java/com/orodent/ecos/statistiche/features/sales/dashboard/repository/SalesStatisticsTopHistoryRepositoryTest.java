package com.orodent.ecos.statistiche.features.sales.dashboard.repository;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class SalesStatisticsTopHistoryRepositoryTest {
    @Test
    void selectsAnnualNetLeadersAndLoadsTheirWholeHistory() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-top-history-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:topHistory" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var sql = connection.createStatement()) {
                sql.executeUpdate("CREATE TABLE clienti (codice_cliente VARCHAR(20), ragione_sociale VARCHAR(100))");
                sql.executeUpdate("INSERT INTO clienti VALUES ('B','Cliente Beta')");
                sql.executeUpdate("CREATE TABLE vendite_dettaglio (data_vendita DATE, codice_cliente VARCHAR(20), importo_netto DECIMAL(15,2), tipo_operazione VARCHAR(20))");
                sql.executeUpdate("INSERT INTO vendite_dettaglio VALUES "
                        + "(DATE('2025-01-10'),'A',2000,'VENDITA'),"
                        + "(DATE('2025-02-10'),'B',100,'VENDITA'),"
                        + "(DATE('2026-01-10'),'A',1000,'VENDITA'),"
                        + "(DATE('2026-02-10'),'A',950,'RESO'),"
                        + "(DATE('2026-03-10'),'B',200,'VENDITA'),"
                        + "(DATE('2026-04-10'),'C',190,'VENDITA'),"
                        + "(DATE('2026-05-10'),'D',180,'VENDITA'),"
                        + "(DATE('2026-06-10'),'E',170,'VENDITA'),"
                        + "(DATE('2026-07-10'),'F',160,'VENDITA')");
            }
            SalesStatisticsRepository repository = new SalesStatisticsRepositoryImpl(connection);
            var history = repository.loadTopCustomerHistory(2026, 5);
            assertEquals(Set.of("B","C","D","E","F"), history.stream()
                    .map(value -> value.customerCode()).collect(Collectors.toSet()));
            var beta = history.stream().filter(value -> value.customerCode().equals("B")).toList();
            assertEquals(2, beta.size());
            assertEquals(2025, beta.getFirst().year());
            assertEquals("Cliente Beta", beta.getFirst().customerName());
            assertEquals(new BigDecimal("200.00"), beta.getLast().revenue());
            assertEquals("C", history.stream().filter(value -> value.customerCode().equals("C")).findFirst().orElseThrow().customerName());
            var previousLeaders = repository.loadTopCustomerHistory(2025, 1);
            assertTrue(previousLeaders.stream().allMatch(value -> value.customerCode().equals("A")));
            assertEquals(new BigDecimal("50.00"), previousLeaders.getLast().revenue());
            assertTrue(repository.loadTopCustomerHistory(2024, 5).isEmpty());
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException expected) { if (!"08006".equals(expected.getSQLState())) throw expected; }
        }
    }
}
