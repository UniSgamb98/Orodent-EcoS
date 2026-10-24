package com.orodent.ecos.performance;

import com.orodent.ecos.core.ConnectionProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Test-only JDBC timing. execute* time excludes row fetching and Java calculations. */
final class SqlMeasurements implements ConnectionProvider {
    private record Key(String scenario, String sql) { }
    private static final class Stats {
        final LongAdder count = new LongAdder(), nanos = new LongAdder(), failures = new LongAdder();
        final LongAccumulator maximum = new LongAccumulator(Long::max, 0);
    }
    private final ConnectionProvider delegate;
    private final Map<Key, Stats> queries = new ConcurrentHashMap<>();
    private final AtomicInteger open = new AtomicInteger();
    private volatile String scenario = "setup";

    SqlMeasurements(ConnectionProvider delegate) { this.delegate = delegate; }
    void scenario(String value) { scenario = value; }
    int openConnections() { return open.get(); }

    @Override
    public Connection openConnection() {
        Connection connection = delegate.openConnection();
        open.incrementAndGet();
        AtomicBoolean closed = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = call(connection, method, arguments);
                        if (result instanceof Statement statement) {
                            String sql = arguments != null && arguments.length > 0 && arguments[0] instanceof String value ? value : null;
                            return statement(statement, sql);
                        }
                        return result;
                    } finally {
                        if (method.getName().equals("close") && closed.compareAndSet(false, true)) open.decrementAndGet();
                    }
                });
    }

    private Object statement(Statement statement, String preparedSql) {
        Class<?> type = statement instanceof CallableStatement ? CallableStatement.class
                : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (!method.getName().startsWith("execute")) return call(statement, method, arguments);
            String sql = preparedSql != null ? preparedSql
                    : arguments != null && arguments.length > 0 && arguments[0] instanceof String value ? value : method.getName();
            Stats stats = queries.computeIfAbsent(new Key(scenario, sql.replaceAll("\\s+", " ").strip()), key -> new Stats());
            long started = System.nanoTime();
            try { return call(statement, method, arguments); }
            catch (Throwable failure) { stats.failures.increment(); throw failure; }
            finally {
                long elapsed = System.nanoTime() - started;
                stats.count.increment(); stats.nanos.add(elapsed); stats.maximum.accumulate(elapsed);
            }
        });
    }

    private static Object call(Object target, Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    void write(Path path) throws Exception {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("scenario,executions,execute_total_ms,execute_max_ms,failures,sql\n");
            for (var entry : queries.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<Key, Stats>>comparingLong(e -> e.getValue().nanos.sum()).reversed()).toList()) {
                Stats stats = entry.getValue();
                writer.write(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%d,%s%n", entry.getKey().scenario(), stats.count.sum(),
                        stats.nanos.sum() / 1_000_000.0, stats.maximum.get() / 1_000_000.0, stats.failures.sum(),
                        '"' + entry.getKey().sql().replace("\"", "\"\"") + '"'));
            }
        }
    }
}
