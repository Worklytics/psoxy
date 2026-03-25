package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.util.Map;

public interface RecordReader extends AutoCloseable {

    /**
     * Reads the next record from the input.
     *
     * @return the record as a Map, or null if end of stream.
     * @throws IOException
     */
    Map<String, Object> readRecord() throws IOException;

    @Override
    void close() throws IOException;
}
