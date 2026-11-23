package com.orodent.ecos.misurazioni.devices;

import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersRequest;
import com.ghgande.j2mod.modbus.msg.ReadMultipleRegistersResponse;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import com.orodent.ecos.misurazioni.config.MeasurementSettings;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;

/** Marzia 1.0.1 register map and scale conversion; failures never become measurements. */
final class ScaleDevice implements MeasurementDevice {
    private final TCPMasterConnection connection;

    ScaleDevice(MeasurementSettings.Endpoint endpoint, int timeoutMs) throws Exception {
        connection = new TCPMasterConnection(InetAddress.getByName(endpoint.host()));
        connection.setPort(endpoint.port());
        connection.setTimeout(timeoutMs);
        try {
            connection.connect();
        } catch (Exception e) {
            close();
            throw e;
        }
    }

    @Override
    public String measure() throws Exception {
        var weight = read(3, 2);
        int raw = (weight.getRegisterValue(0) << 16) | (weight.getRegisterValue(1) & 0xffff);
        int decimals = read(1102, 1).getRegisterValue(0);
        if (decimals > 9)
            throw new IOException("Precisione della bilancia non valida: " + decimals);
        // Original: raw / 10^(decimals + 1) kg, then * 1000 and decimals - 2 places.
        return BigDecimal.valueOf(raw)
                .scaleByPowerOfTen(2 - decimals)
                .setScale(Math.max(decimals - 2, 0), RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
    }

    private ReadMultipleRegistersResponse read(int address, int count) throws Exception {
        var request = new ReadMultipleRegistersRequest(address, count);
        request.setUnitID(255);
        var transaction = new ModbusTCPTransaction(connection);
        transaction.setRetries(0);
        transaction.setRequest(request);
        transaction.execute();
        if (!(transaction.getResponse() instanceof ReadMultipleRegistersResponse response)
                || response.getWordCount() != count)
            throw new IOException("Risposta della bilancia non valida.");
        return response;
    }

    @Override
    public void close() {
        connection.close();
    }
}
