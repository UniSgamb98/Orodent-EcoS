package com.orodent.ecos.database.discovery;

import java.net.InetAddress;
import java.util.Optional;

/** Application and protocol identifiers prevent discovery of unrelated Derby applications. */
final class DiscoveryProtocol {
    private static final String REQUEST = "TONV3_DISCOVER;v=1;database=TonV3Database;nonce=";
    private static final String RESPONSE = "TONV3_HOST;v=1;database=TonV3Database;nonce=";

    private DiscoveryProtocol() { }

    static String request(String nonce) { return REQUEST + nonce; }

    static Optional<String> response(String request, int port) {
        if (!request.startsWith(REQUEST)) return Optional.empty();
        String nonce = request.substring(REQUEST.length());
        if (!nonce.matches("[a-f0-9]{32}")) return Optional.empty();
        return Optional.of(RESPONSE + nonce + ";port=" + port);
    }

    static Optional<HostInfo> host(String response, String nonce, InetAddress address) {
        String prefix = RESPONSE + nonce + ";port=";
        if (!response.startsWith(prefix)) return Optional.empty();
        String port = response.substring(prefix.length());
        if (!port.matches("[0-9]{1,5}")) return Optional.empty();
        try {
            return Optional.of(new HostInfo(address, Integer.parseInt(port)));
        } catch (IllegalArgumentException invalidResponse) {
            return Optional.empty();
        }
    }
}
