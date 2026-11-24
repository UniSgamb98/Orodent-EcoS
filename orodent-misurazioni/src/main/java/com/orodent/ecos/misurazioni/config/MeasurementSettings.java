package com.orodent.ecos.misurazioni.config;

import java.util.Properties;

public record MeasurementSettings(Endpoint micrometer, Endpoint scale, int timeoutMs) {
    public record Endpoint(String host, int port) {}

    public static boolean enabled(Properties settings) {
        return Boolean.parseBoolean(settings.getProperty("misurazioni.enabled", "false").strip());
    }

    public static MeasurementSettings from(Properties settings) {
        return new MeasurementSettings(
                endpoint(settings, "micrometro", "192.168.1.150", 8600),
                endpoint(settings, "bilancia", "192.168.1.201", 1800),
                number(settings, "misurazioni.timeout.ms", 2000, 100, 30000));
    }

    private static Endpoint endpoint(Properties settings, String name, String host, int port) {
        String prefix = "misurazioni." + name;
        String configured = settings.getProperty(prefix + ".host", host).strip();
        if (configured.isEmpty())
            throw new IllegalArgumentException(prefix + ".host obbligatorio.");
        return new Endpoint(configured, number(settings, prefix + ".port", port, 1, 65535));
    }

    private static int number(Properties settings, String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(settings.getProperty(key, "" + fallback).strip());
            if (value >= min && value <= max) return value;
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException(
                key + ": valore richiesto tra " + min + " e " + max + ".");
    }
}
