package co.worklytics.psoxy.storage.impl;

import java.io.IOException;

public interface RecordReader extends AutoCloseable {

    /**
     * Reads the next record from the input.
     *
     * @return the record as a JSON-compatible object (Map, List, etc), or null if end of stream.
     * @throws IOException
     */
    Object readRecord() throws IOException;

    @Override
    void close() throws IOException;
}
