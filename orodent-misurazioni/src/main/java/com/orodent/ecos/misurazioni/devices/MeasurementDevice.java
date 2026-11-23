package com.orodent.ecos.misurazioni.devices;

public interface MeasurementDevice extends AutoCloseable {
    String measure() throws Exception;

    @Override
    void close();
}
