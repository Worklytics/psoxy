package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.util.Map;

public interface RecordWriter extends AutoCloseable {

    @Override
    void close() throws IOException;

    /**
     * Called before writing any records.
     * @throws IOException
     */
    void beginRecordSet() throws IOException;

    /**
     * Writes a record.
     * @param record the record to write
     * @throws IOException
     */
    void writeRecord(Map<String, Object> record) throws IOException;

    /**
     * Called after writing all records.
     * @throws IOException
     */
    void endRecordSet() throws IOException;
}
