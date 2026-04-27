package co.worklytics.psoxy.storage.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import com.jayway.jsonpath.Configuration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class NdjsonRecordWriter implements RecordWriter {

    final Writer writer;
    final Configuration jsonConfiguration;
    
    BufferedWriter bufferedWriter;

    @Override
    public void beginRecordSet() throws IOException {
        bufferedWriter = new BufferedWriter(writer);
    }

    @Override
    public void writeRecord(Map<String, Object> record) throws IOException {
        bufferedWriter.write(jsonConfiguration.jsonProvider().toJson(record));
        bufferedWriter.newLine();
    }

    @Override
    public void endRecordSet() throws IOException {
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
