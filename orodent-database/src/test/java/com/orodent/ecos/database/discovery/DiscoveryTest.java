package com.orodent.ecos.database.discovery;

import org.junit.jupiter.api.Test;
import java.net.DatagramSocket;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    @Test
    void rejectsUnrelatedMalformedAndUncorrelatedResponses() throws Exception {
        var address = InetAddress.getLoopbackAddress();
        String response = DiscoveryProtocol.response(DiscoveryProtocol.request(NONCE), 1528).orElseThrow();
        assertEquals(1528, DiscoveryProtocol.host(response, NONCE, address).orElseThrow().dbPort());
        assertTrue(DiscoveryProtocol.response("CLIZR_DISCOVER", 1528).isEmpty());
        assertTrue(DiscoveryProtocol.response(DiscoveryProtocol.request("invalid"), 1528).isEmpty());
        for (String invalid : new String[]{response.replace("v=1", "v=2"), response.replace("OrodentEcoS", "Other"),
                response.replace(NONCE, "abcdef0123456789abcdef0123456789"), response + ";extra=1",
                response.replace("1528", "0"), response.replace("1528", "65536"), response.replace("1528", "abc")}) {
            assertTrue(DiscoveryProtocol.host(invalid, NONCE, address).isEmpty(), invalid);
        }
    }

    @Test
    void discoversLocalHostAndCanRejectAnUnusableCandidate() throws Exception {
        int port;
        try (var socket = new DatagramSocket(0)) { port = socket.getLocalPort(); }
        try (var server = new DiscoveryServer(port, 1528)) {
            server.start();
            assertEquals(1528, new DiscoveryClient(port, 500).findHost().orElseThrow().dbPort());
            assertTrue(new DiscoveryClient(port, 100).findHost(host -> false).isEmpty());
        }
        try (var socket = new DatagramSocket(port)) { assertEquals(port, socket.getLocalPort()); }
    }

    @Test
    void closingBeforeStartReleasesTheReservedPort() throws Exception {
        int port;
        try (var socket = new DatagramSocket(0)) { port = socket.getLocalPort(); }
        var server = new DiscoveryServer(port, 1528);
        server.close();
        server.close();
        try (var socket = new DatagramSocket(port)) { assertEquals(port, socket.getLocalPort()); }
    }
}
