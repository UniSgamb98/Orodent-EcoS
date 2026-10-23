package com.orodent.ecos.database.repository.impl;

import com.orodent.ecos.database.model.TipoOperazione;
import com.orodent.ecos.database.model.VenditaDettaglio;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VenditaDettaglioRepositoryImplTest {

    @Test
    void deletesTheInclusiveImportedDateRange() {
        List<Date> parameters = new ArrayList<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setDate" -> { parameters.add((Date) args[1]); yield null; }
                    case "executeUpdate" -> 4;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement")
                        ? statement
                        : defaultValue(method.getReturnType())
        );

        int deleted = new VenditaDettaglioRepositoryImpl(connection).deleteByDateRange(
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2026, 10, 6)
        );

        assertEquals(4, deleted);
        assertEquals(List.of(Date.valueOf("2025-10-01"), Date.valueOf("2026-10-07")), parameters);
    }

    @Test
    void splitsLargeImportsIntoBoundedBatches() {
        List<Integer> executedBatchSizes = new ArrayList<>();
        int[] pending = {0};
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addBatch" -> { pending[0]++; yield null; }
                    case "clearBatch" -> { pending[0] = 0; yield null; }
                    case "executeBatch" -> {
                        executedBatchSizes.add(pending[0]);
                        int[] result = new int[pending[0]];
                        java.util.Arrays.fill(result, Statement.SUCCESS_NO_INFO);
                        yield result;
                    }
                    case "close", "setString", "setInt", "setDate", "setBigDecimal", "setTimestamp" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                }
        );
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> method.getName().equals("prepareStatement")
                        ? statement
                        : defaultValue(method.getReturnType())
        );
        List<VenditaDettaglio> rows = java.util.stream.IntStream.rangeClosed(1, 1001)
                .mapToObj(this::sale)
                .toList();

        int inserted = new VenditaDettaglioRepositoryImpl(connection).insertAll(rows);

        assertEquals(1001, inserted);
        assertEquals(List.of(500, 500, 1), executedBatchSizes);
    }

    private VenditaDettaglio sale(int row) {
        return new VenditaDettaglio(
                null, "MAGMOD", "000001", "000001", row, LocalDate.of(2023, 1, 9),
                "CLIENTE", "PRODOTTO", "Prodotto", null, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                TipoOperazione.VENDITA, null, null, null
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
