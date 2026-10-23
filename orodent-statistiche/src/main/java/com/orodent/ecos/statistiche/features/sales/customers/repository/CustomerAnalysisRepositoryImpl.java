package com.orodent.ecos.statistiche.features.sales.customers.repository;

import com.orodent.ecos.statistiche.features.sales.projection.model.*;

import com.orodent.ecos.database.repository.RepositoryException;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class CustomerAnalysisRepositoryImpl implements CustomerAnalysisRepository {
    private final Connection connection;
    public CustomerAnalysisRepositoryImpl(Connection connection) { this.connection = Objects.requireNonNull(connection); }

    @Override public CustomerDetail loadBaseDetail(String code, int year) {
        String sql = """
                SELECT v.codice_cliente, COALESCE(MAX(c.ragione_sociale),v.codice_cliente) nome,
                       MAX(c.codice_iso) paese, MAX(c.categoria) categoria, MAX(c.listino) listino,
                       MAX(c.agente) agente, MAX(c.tipo_cliente) tipo_cliente,
                       COALESCE(SUM(v.importo_netto),0) fatturato, COALESCE(SUM(v.quantita),0) quantita,
                       COUNT(DISTINCT v.documento_id) documenti, MIN(v.data_vendita) primo, MAX(v.data_vendita) ultimo
                FROM VENDITE.VENDITE_DETTAGLIO v LEFT JOIN ANAGRAFICHE.CLIENTI c ON c.codice_cliente=v.codice_cliente
                WHERE v.codice_cliente=? AND YEAR(v.data_vendita)=? AND v.tipo_operazione='VENDITA'
                GROUP BY v.codice_cliente
                """;
        try (PreparedStatement ps=connection.prepareStatement(sql)) {
            ps.setString(1,code); ps.setInt(2,year);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) throw new RepositoryException("Nessuna vendita trovata per il cliente " + code);
                BigDecimal revenue=rs.getBigDecimal("fatturato"), quantity=rs.getBigDecimal("quantita");
                int documents=rs.getInt("documenti");
                return new CustomerDetail(code,rs.getString("nome"),rs.getString("paese"),rs.getString("categoria"),
                        rs.getString("listino"),rs.getString("agente"),rs.getString("tipo_cliente"),revenue,quantity,
                        documents,average(revenue,documents),average(quantity,documents),null,null,
                        rs.getDate("primo").toLocalDate(),rs.getDate("ultimo").toLocalDate(),0);
            }
        } catch(SQLException e){throw failure(e);}
    }

    @Override public Map<String, List<LocalDate>> loadPurchaseDates(int year, LocalDate through) {
        String sql = """
                SELECT DISTINCT codice_cliente, data_vendita FROM VENDITE.VENDITE_DETTAGLIO
                WHERE data_vendita BETWEEN ? AND ? AND tipo_operazione='VENDITA'
                ORDER BY codice_cliente, data_vendita
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, java.sql.Date.valueOf(LocalDate.of(year, 1, 1)));
            statement.setDate(2, java.sql.Date.valueOf(through));
            try (ResultSet result = statement.executeQuery()) {
                Map<String, List<LocalDate>> dates = new LinkedHashMap<>();
                while (result.next()) dates.computeIfAbsent(result.getString(1), ignored -> new ArrayList<>())
                        .add(result.getDate(2).toLocalDate());
                dates.replaceAll((code, values) -> List.copyOf(values));
                return Map.copyOf(dates);
            }
        } catch (SQLException exception) { throw failure(exception); }
    }

    @Override public List<LocalDate> loadPurchaseDates(String code,int year){
        String sql="SELECT DISTINCT data_vendita FROM VENDITE.VENDITE_DETTAGLIO WHERE codice_cliente=? AND YEAR(data_vendita)=? AND tipo_operazione='VENDITA' ORDER BY data_vendita";
        try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,code);ps.setInt(2,year);
            try(ResultSet rs=ps.executeQuery()){List<LocalDate> out=new ArrayList<>();while(rs.next())out.add(rs.getDate(1).toLocalDate());return List.copyOf(out);}
        }catch(SQLException e){throw failure(e);}
    }

    @Override public List<CustomerYearSummary> loadYearlyHistory(String code){
        String sql="""
                SELECT YEAR(data_vendita) anno, SUM(importo_netto) fatturato, SUM(quantita) quantita,
                       COUNT(DISTINCT documento_id) documenti FROM VENDITE.VENDITE_DETTAGLIO
                WHERE codice_cliente=? AND tipo_operazione='VENDITA' GROUP BY YEAR(data_vendita) ORDER BY anno
                """;
        try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,code);
            try(ResultSet rs=ps.executeQuery()){List<CustomerYearSummary> out=new ArrayList<>();while(rs.next())out.add(new CustomerYearSummary(rs.getInt("anno"),rs.getBigDecimal("fatturato"),rs.getBigDecimal("quantita"),rs.getInt("documenti")));return List.copyOf(out);}
        }catch(SQLException e){throw failure(e);}
    }

    @Override public List<CustomerMonthlyValue> loadMonthlyHistory(String code,int fromYear,int toYear){
        String sql="""
                SELECT YEAR(data_vendita) anno, MONTH(data_vendita) mese, SUM(importo_netto) fatturato,
                       SUM(quantita) quantita, COUNT(DISTINCT documento_id) documenti FROM VENDITE.VENDITE_DETTAGLIO
                WHERE codice_cliente=? AND YEAR(data_vendita) BETWEEN ? AND ? AND tipo_operazione='VENDITA'
                GROUP BY YEAR(data_vendita),MONTH(data_vendita) ORDER BY anno,mese
                """;
        try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,code);ps.setInt(2,fromYear);ps.setInt(3,toYear);
            try(ResultSet rs=ps.executeQuery()){List<CustomerMonthlyValue> out=new ArrayList<>();while(rs.next())out.add(new CustomerMonthlyValue(rs.getInt("anno"),rs.getInt("mese"),rs.getBigDecimal("fatturato"),rs.getBigDecimal("quantita"),rs.getInt("documenti")));return List.copyOf(out);}
        }catch(SQLException e){throw failure(e);}
    }

    @Override
    public CustomerPeriodTotals loadPeriodTotals(String code, LocalDate from, LocalDate through) {
        String sql = """
                SELECT COALESCE(SUM(importo_netto), 0) fatturato,
                       COALESCE(SUM(quantita), 0) quantita, COUNT(DISTINCT documento_id) documenti
                FROM VENDITE.VENDITE_DETTAGLIO
                WHERE codice_cliente=? AND data_vendita BETWEEN ? AND ? AND tipo_operazione='VENDITA'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setDate(2, java.sql.Date.valueOf(from));
            statement.setDate(3, java.sql.Date.valueOf(through));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new CustomerPeriodTotals(result.getBigDecimal("fatturato"),
                        result.getBigDecimal("quantita"), result.getInt("documenti"));
            }
        } catch (SQLException failure) {
            throw failure(failure);
        }
    }

    @Override public List<CustomerProductItem> loadProducts(String code,int year){
        String sql="""
                SELECT codice_prodotto,MAX(descrizione_prodotto) descrizione,SUM(importo_netto) fatturato,SUM(quantita) quantita
                FROM VENDITE.VENDITE_DETTAGLIO WHERE codice_cliente=? AND YEAR(data_vendita)=? AND tipo_operazione='VENDITA'
                GROUP BY codice_prodotto ORDER BY fatturato DESC FETCH FIRST 20 ROWS ONLY
                """;
        try(PreparedStatement ps=connection.prepareStatement(sql)){ps.setString(1,code);ps.setInt(2,year);
            try(ResultSet rs=ps.executeQuery()){List<CustomerProductItem> out=new ArrayList<>();while(rs.next())out.add(new CustomerProductItem(rs.getString("codice_prodotto"),rs.getString("descrizione"),rs.getBigDecimal("fatturato"),rs.getBigDecimal("quantita")));return List.copyOf(out);}
        }catch(SQLException e){throw failure(e);}
    }

    @Override public SalesDataCoverage loadDataCoverage() {
        String sql = "SELECT MIN(data_vendita), MAX(data_vendita) FROM VENDITE.VENDITE_DETTAGLIO "
                + "WHERE tipo_operazione='VENDITA'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getDate(1) == null) {
                    throw new RepositoryException("Nessun dato di vendita disponibile");
                }
                return new SalesDataCoverage(rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate());
            }
        } catch (SQLException e) { throw failure(e); }
    }

    @Override public List<DailyRevenueValue> loadDailyRevenueHistory(String code, int fromYear, int toYear) {
        String sql = """
                SELECT data_vendita, SUM(importo_netto) fatturato FROM VENDITE.VENDITE_DETTAGLIO
                WHERE codice_cliente=? AND YEAR(data_vendita) BETWEEN ? AND ? AND tipo_operazione='VENDITA'
                GROUP BY data_vendita ORDER BY data_vendita
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code); ps.setInt(2, fromYear); ps.setInt(3, toYear);
            try (ResultSet rs = ps.executeQuery()) {
                List<DailyRevenueValue> values = new ArrayList<>();
                while (rs.next()) values.add(new DailyRevenueValue(
                        rs.getDate("data_vendita").toLocalDate(), rs.getBigDecimal("fatturato")));
                return List.copyOf(values);
            }
        } catch (SQLException e) { throw failure(e); }
    }

    @Override public List<DailyMetricValue> loadDailyQuantityHistory(String code, int fromYear, int toYear) {
        String sql = """
                SELECT data_vendita, SUM(quantita) quantita FROM VENDITE.VENDITE_DETTAGLIO
                WHERE codice_cliente=? AND YEAR(data_vendita) BETWEEN ? AND ? AND tipo_operazione='VENDITA'
                GROUP BY data_vendita ORDER BY data_vendita
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, code); ps.setInt(2, fromYear); ps.setInt(3, toYear);
            try (ResultSet rs = ps.executeQuery()) {
                List<DailyMetricValue> values = new ArrayList<>();
                while (rs.next()) values.add(new DailyMetricValue(
                        rs.getDate("data_vendita").toLocalDate(), rs.getBigDecimal("quantita")));
                return List.copyOf(values);
            }
        } catch (SQLException e) { throw failure(e); }
    }

    private BigDecimal average(BigDecimal value,int count){return count==0?BigDecimal.ZERO:value.divide(BigDecimal.valueOf(count),2,java.math.RoundingMode.HALF_UP);}
    private RepositoryException failure(SQLException e){return new RepositoryException("Errore durante l'analisi del cliente",e);}
}
