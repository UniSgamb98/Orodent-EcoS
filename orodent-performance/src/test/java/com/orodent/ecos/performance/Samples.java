package com.orodent.ecos.performance;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Raw measurements, deliberately independent of machine-specific pass/fail thresholds. */
final class Samples implements AutoCloseable {
    private final java.io.BufferedWriter writer;

    Samples(Path path) throws IOException {
        writer = Files.newBufferedWriter(path);
        writer.write("scenario,iteration,duration_ms,heap_bytes,threads,ecos_workers,worker_cpu_ms\n");
    }

    synchronized void record(String scenario, int iteration, long elapsedNanos) throws IOException {
        var threads = ManagementFactory.getThreadMXBean();
        int workers = 0;
        long workerCpu = threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled() ? 0 : -1;
        for (var thread : threads.getThreadInfo(threads.getAllThreadIds())) {
            if (thread != null && (thread.getThreadName().startsWith("ecos-worker-")
                    || thread.getThreadName().startsWith("ecos-import-"))) {
                workers++;
                if (workerCpu >= 0) workerCpu += Math.max(0, threads.getThreadCpuTime(thread.getThreadId()));
            }
        }
        writer.write(String.format(Locale.ROOT, "%s,%d,%.3f,%d,%d,%d,%.3f%n", scenario, iteration,
                elapsedNanos / 1_000_000.0, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
                threads.getThreadCount(), workers, workerCpu < 0 ? -1 : workerCpu / 1_000_000.0));
        writer.flush();
    }

    @Override public void close() throws IOException { writer.close(); }
}
