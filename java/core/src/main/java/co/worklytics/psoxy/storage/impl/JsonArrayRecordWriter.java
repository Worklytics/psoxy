package co.worklytics.psoxy.storage.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class JsonArrayRecordWriter implements RecordWriter {

    private static final String START_ARRAY = "[";
    private static final String END_ARRAY = "]";
    private static final String DELIMITER = ",";

    final Writer writer;
    final ObjectMapper objectMapper;
    final Configuration jsonConfiguration;

    BufferedWriter bufferedWriter;
    boolean first = true;

    @Override
    public void beginRecordSet() throws IOException {
        bufferedWriter = new BufferedWriter(writer);
        bufferedWriter.write(START_ARRAY);
    }

    @Override
    public void writeRecord(Object record) throws IOException {
        if (!first) {
            bufferedWriter.write(DELIMITER);
        }
        bufferedWriter.write(jsonConfiguration.jsonProvider().toJson(record));
        first = false;
    }

    @Override
    public void endRecordSet() throws IOException {
        bufferedWriter.write(END_ARRAY);
        bufferedWriter.flush();
    }

    @Override
    public void close() throws IOException {
        // do not close the underlying writer as we didn't open it; just ensure we flush
        if (bufferedWriter != null) {
            bufferedWriter.flush();
        } else {
            writer.flush();
        }
    }
}
