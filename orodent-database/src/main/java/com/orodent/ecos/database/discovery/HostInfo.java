package com.orodent.ecos.database.discovery;

import java.net.InetAddress;
import java.util.Objects;

public record HostInfo(InetAddress address, int dbPort) {
    public HostInfo {
        Objects.requireNonNull(address, "address");
        if (dbPort < 1 || dbPort > 65535) throw new IllegalArgumentException("Porta database non valida");
    }
}
