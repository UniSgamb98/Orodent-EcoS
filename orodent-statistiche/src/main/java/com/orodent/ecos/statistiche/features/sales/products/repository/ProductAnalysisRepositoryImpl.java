package com.orodent.ecos.statistiche.features.sales.products.repository;

import com.orodent.ecos.database.repository.RepositoryException;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductSale;
import com.orodent.ecos.statistiche.features.sales.projection.model.SalesDataCoverage;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ProductAnalysisRepositoryImpl implements ProductAnalysisRepository {
    private final Connection connection;
    public ProductAnalysisRepositoryImpl(Connection connection) { this.connection = connection; }

    @Override public List<Integer> findYears() {
        try (var statement = connection.prepareStatement("SELECT DISTINCT YEAR(data_vendita) FROM VENDITE.VENDITE_DETTAGLIO ORDER BY 1 DESC");
             var result = statement.executeQuery()) {
            List<Integer> years = new ArrayList<>();
            while (result.next()) years.add(result.getInt(1));
            return List.copyOf(years);
        } catch (SQLException failure) { throw failure(failure); }
    }

    @Override public SalesDataCoverage loadCoverage() {
        try (var statement = connection.prepareStatement("SELECT MIN(data_vendita), MAX(data_vendita) FROM VENDITE.VENDITE_DETTAGLIO");
             var result = statement.executeQuery()) {
            result.next();
            return result.getDate(1) == null ? null : new SalesDataCoverage(result.getDate(1).toLocalDate(), result.getDate(2).toLocalDate());
        } catch (SQLException failure) { throw failure(failure); }
    }

    @Override public List<ProductSale> loadSales(LocalDate from, LocalDate through) {
        String sql = """
                SELECT v.codice_prodotto, MAX(v.descrizione_prodotto) descrizione, v.data_vendita,
                       v.codice_cliente, COALESCE(MAX(c.ragione_sociale), v.codice_cliente) nome,
                       v.documento_id, v.tipo_operazione,
                       SUM(CASE WHEN v.tipo_operazione='VENDITA' THEN COALESCE(v.importo_netto,0)
                           ELSE -ABS(COALESCE(v.importo_netto,0)) END) fatturato,
                       SUM(CASE WHEN v.tipo_operazione='VENDITA' THEN v.quantita ELSE -ABS(v.quantita) END) quantita
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE v.data_vendita BETWEEN ? AND ?
                GROUP BY v.codice_prodotto, v.data_vendita, v.codice_cliente, v.documento_id, v.tipo_operazione
                ORDER BY v.data_vendita, v.codice_prodotto, v.codice_cliente
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(from)); statement.setDate(2, Date.valueOf(through));
            try (var result = statement.executeQuery()) {
                List<ProductSale> sales = new ArrayList<>();
                while (result.next()) sales.add(new ProductSale(result.getString("codice_prodotto"),
                        result.getString("descrizione"), result.getDate("data_vendita").toLocalDate(),
                        result.getString("codice_cliente"), result.getString("nome"), result.getString("documento_id"),
                        "VENDITA".equals(result.getString("tipo_operazione")), result.getBigDecimal("fatturato"), result.getBigDecimal("quantita")));
                return List.copyOf(sales);
            }
        } catch (SQLException failure) { throw failure(failure); }
    }

    private RepositoryException failure(SQLException failure) {
        return new RepositoryException("Errore durante il caricamento dell'analisi prodotti", failure);
    }
}
