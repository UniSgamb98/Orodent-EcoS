package com.orodent.ecos.launcher.update;

public record ReleaseVersion(int major, int minor, int patch) implements Comparable<ReleaseVersion> {
    public static ReleaseVersion parse(String value) {
        if (value == null || !value.matches("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})"))
            throw new IllegalArgumentException("Versione release non valida: " + value);
        String[] parts = value.split("\\.");
        return new ReleaseVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
    @Override public int compareTo(ReleaseVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }
    @Override public String toString() { return major + "." + minor + "." + patch; }
}
