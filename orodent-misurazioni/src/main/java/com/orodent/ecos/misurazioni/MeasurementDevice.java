package com.orodent.ecos.misurazioni;

interface MeasurementDevice extends AutoCloseable {
    String measure() throws Exception;

    @Override
    void close();
}
