package com.orodent.ecos.statistiche.features.sales.products.repository;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProductAnalysisRepositoryTest {
    @Test void loadsBoundedDocumentAggregatesAndSignsCorrectionsExactlyOnce() throws Exception {
        System.setProperty("derby.stream.error.file","target/derby-products-test.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:products" + UUID.randomUUID().toString().replace("-","");
        try (var connection = DriverManager.getConnection(url + ";create=true")) {
            try(var statement=connection.createStatement()) {
                statement.executeUpdate("CREATE SCHEMA ANAGRAFICHE");
                statement.executeUpdate("CREATE TABLE ANAGRAFICHE.CLIENTI (codice_cliente VARCHAR(20) PRIMARY KEY, ragione_sociale VARCHAR(100))");
                statement.executeUpdate("INSERT INTO ANAGRAFICHE.CLIENTI VALUES ('C1','Cliente Uno')");
                statement.executeUpdate("CREATE SCHEMA VENDITE");
                statement.executeUpdate("CREATE TABLE VENDITE.VENDITE_DETTAGLIO (codice_prodotto VARCHAR(30), descrizione_prodotto VARCHAR(100), data_vendita DATE, codice_cliente VARCHAR(20), documento_id VARCHAR(20), tipo_operazione VARCHAR(20), importo_netto DECIMAL(15,2), quantita DECIMAL(15,3))");
                statement.executeUpdate("INSERT INTO VENDITE.VENDITE_DETTAGLIO VALUES "
                        + "('ZR9812MSTA2','MST A2',DATE('2025-01-01'),'C1','D1','VENDITA',100,10),"
                        + "('ZR9812MSTA2','MST A2',DATE('2025-01-01'),'C1','D1','VENDITA',200,20),"
                        + "('ZR9812MSTA2','MST A2',DATE('2025-01-02'),'C1','R1','RESO',50,5),"
                        + "('ZR9812MSTA2','MST A2',DATE('2025-01-03'),'C1','R2','NOTA_CREDITO',-25,-2),"
                        + "('ZRPC','Altro',DATE('2026-01-01'),'C2','D2','VENDITA',20,2)");
            }
            var repository = new ProductAnalysisRepositoryImpl(connection);
            assertEquals(List.of(2026,2025),repository.findYears());
            assertEquals(LocalDate.of(2025,1,1),repository.loadCoverage().from());
            var sales=repository.loadSales(LocalDate.of(2025,1,1),LocalDate.of(2025,1,3));
            assertEquals(3,sales.size());
            assertEquals(new BigDecimal("300.00"),sales.getFirst().revenue());
            assertEquals("Cliente Uno",sales.getFirst().customerName());
            assertEquals(new BigDecimal("-50.00"),sales.get(1).revenue());
            assertEquals(new BigDecimal("-2.000"),sales.get(2).quantity());
            assertFalse(sales.get(2).sale());
            var snapshot = new com.orodent.ecos.statistiche.features.sales.products.model.ProductSalesSnapshot(
                    List.of(2025),2025,LocalDate.of(2025,1,3),LocalDate.of(2025,1,1),sales);
            var pace = new com.orodent.ecos.statistiche.features.sales.products.service.ProductSalesPaceCalculator().calculate(snapshot,sales);
            assertEquals(1,pace.documents(), "Le due righe vendute appartengono allo stesso documento");
            assertEquals(0,new BigDecimal("30").compareTo(pace.meanDocumentQuantity()));
            assertEquals(0,new BigDecimal("30").compareTo(pace.medianDocumentQuantity()));
            assertEquals("C2",repository.loadSales(LocalDate.of(2026,1,1),LocalDate.of(2026,1,1)).getFirst().customerName());
        } finally {
            try {DriverManager.getConnection(url+";drop=true");}
            catch(SQLException expected){if(!"08006".equals(expected.getSQLState()))throw expected;}
        }
    }
}
