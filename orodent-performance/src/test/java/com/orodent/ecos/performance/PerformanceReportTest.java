package com.orodent.ecos.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceReportTest {
    @TempDir Path temporary;

    @Test
    void calculatesMedianAndNearestRankPercentileIncludingSmallSamples() {
        assertEquals(new PerformanceReport.Summary(1, 2.5, 4, 4), PerformanceReport.summarize(List.of(4.0, 1.0, 3.0, 2.0)));
        assertEquals(19, PerformanceReport.summarize(java.util.stream.IntStream.rangeClosed(1, 20).mapToDouble(i -> i).boxed().toList()).p95());
        assertThrows(IllegalArgumentException.class, () -> PerformanceReport.summarize(List.of()));
    }

    @Test
    void keepsFailedPartialRunsExplicitAndUsesStableDecimalFormatting() throws Exception {
        Files.writeString(temporary.resolve("environment.properties"), "status=FAILED\nfailure=interrupted\n");
        Files.writeString(temporary.resolve("settings.properties"), "performance.ui=false\n");
        Files.writeString(temporary.resolve("headless-samples.csv"), "scenario,iteration,duration_ms,heap_bytes,threads,ecos_workers,worker_cpu_ms\ndashboard,1,12.345,100,2,0,0\n");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ITALY);
            PerformanceReport.write(temporary);
        } finally { Locale.setDefault(previous); }
        String report = Files.readString(temporary.resolve("report.md"));
        assertTrue(report.contains("**FAILED**"));
        assertTrue(report.contains("incompleta"));
        assertTrue(report.contains("12.345"));
        assertTrue(report.contains("non sono microbenchmark"));
    }
}
