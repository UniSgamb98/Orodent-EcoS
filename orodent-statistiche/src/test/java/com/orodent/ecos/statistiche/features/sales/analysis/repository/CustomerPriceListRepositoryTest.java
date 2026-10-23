package com.orodent.ecos.statistiche.features.sales.analysis.repository;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPriceListRepositoryTest {
    @Test void readsImportedPriceListAndKeepsCustomersWithoutAnAssignedListOrAnagraphic() throws Exception {
        System.setProperty("derby.stream.error.file", "target/derby-price-list-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:priceList" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE clienti (codice_cliente VARCHAR(30), ragione_sociale VARCHAR(100), listino VARCHAR(100))");
                statement.executeUpdate("CREATE TABLE vendite_dettaglio (codice_cliente VARCHAR(30), data_vendita DATE, documento_id VARCHAR(30), tipo_operazione VARCHAR(20), importo_netto DECIMAL(15,2), quantita DECIMAL(15,3))");
                statement.executeUpdate("INSERT INTO clienti VALUES ('20ZR988','3D Openminds BV','24_ZR55'),('20DR1030','Cliente senza listino',NULL)");
                statement.executeUpdate("INSERT INTO vendite_dettaglio VALUES "
                        + "('20ZR988',DATE('2026-10-20'),'D1','VENDITA',100,2),"
                        + "('20ZR988',DATE('2026-10-20'),'D1','VENDITA',50,1),"
                        + "('20DR1030',DATE('2026-10-20'),'D2','VENDITA',20,1),"
                        + "('SCONOSCIUTO',DATE('2026-10-20'),'D3','VENDITA',10,1)");
            }
            var rows = new SalesAnalysisRepositoryImpl(connection).loadCustomers(2026);
            assertEquals(3, rows.size());
            assertEquals("24_ZR55", rows.getFirst().priceList());
            assertEquals(new BigDecimal("150.00"), rows.getFirst().revenue());
            assertEquals(1, rows.getFirst().documents());
            assertNull(rows.get(1).priceList());
            assertEquals("SCONOSCIUTO", rows.get(2).name());
            assertNull(rows.get(2).priceList());
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE clienti SET listino='26_50' WHERE codice_cliente='20ZR988'");
            }
            assertEquals("26_50", new SalesAnalysisRepositoryImpl(connection).loadCustomers(2026).getFirst().priceList());
        } finally {
            try { DriverManager.getConnection(url + ";drop=true"); }
            catch (SQLException exception) { if (!"08006".equals(exception.getSQLState())) throw exception; }
        }
    }
}
