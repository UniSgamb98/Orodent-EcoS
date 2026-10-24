package com.orodent.ecos.performance;

import javafx.application.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One pending callback at most: measurement cannot flood the JavaFX queue. */
final class FxHeartbeat implements AutoCloseable {
    private record Tick(String phase, long delayNanos) { }
    private final ConcurrentLinkedQueue<Tick> ticks = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile boolean closed;
    private volatile String phase = "idle";

    FxHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (closed || !pending.compareAndSet(false, true)) return;
            long enqueued = System.nanoTime();
            String current = phase;
            Platform.runLater(() -> {
                try { if (!closed) ticks.add(new Tick(current, System.nanoTime() - enqueued)); }
                finally { pending.set(false); }
            });
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    void phase(String value) { phase = value; }

    void write(Path path) throws Exception {
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write("scenario,delay_ms\n");
            for (Tick tick : ticks) writer.write(String.format(Locale.ROOT, "%s,%.3f%n", tick.phase(), tick.delayNanos() / 1_000_000.0));
        }
    }

    @Override public void close() throws InterruptedException {
        closed = true;
        scheduler.shutdownNow();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
    }
}
