package co.worklytics.psoxy.storage.impl;

import java.io.IOException;

public interface RecordWriter extends AutoCloseable {

    /**
     * Called before writing any records.
     * @throws IOException
     */
    void beginRecordSet() throws IOException;

    /**
     * Writes a record.
     * @param record the record to write (JSON-compatible object)
     * @throws IOException
     */
    void writeRecord(Object record) throws IOException;

    /**
     * Called after writing all records.
     * @throws IOException
     */
    void endRecordSet() throws IOException;
}
