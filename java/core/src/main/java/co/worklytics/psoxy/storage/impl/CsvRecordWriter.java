package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CsvRecordWriter implements RecordWriter {

    // TODO: in the future, we may need to expose configuration to let users change this
    private static final String RECORD_SEPARATOR = "\n";

    final Writer writer;
    
    CSVPrinter printer;
    String[] headers;

    @Override
    public void beginRecordSet() throws IOException {
        // no-op; headers not known until we see the first record.
    }

    @Override
    public void writeRecord(Object record) throws IOException {
        if (!(record instanceof Map)) {
            throw new IllegalArgumentException("CsvRecordWriter expects Map records");
        }
        Map<String, Object> map = (Map<String, Object>) record;
        
        if (printer == null) {
            // this is the first row; create printer, initialized with headers we see in first record
            Set<String> keys = map.keySet();
            headers = keys.toArray(new String[0]);
            printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .setRecordSeparator(RECORD_SEPARATOR)
                .get());
        }
        
        for (String header : headers) {
            printer.print(map.get(header));
        }
        printer.println();
    }

    @Override
    public void endRecordSet() throws IOException {
        if (printer != null) {
            printer.flush();
        }
    }

    @Override
    public void close() throws IOException {
        // do not close the underlying writer as we didn't open it; just ensure we flush
        if (printer != null) {
            printer.flush();
        } else {
            writer.flush();
        }
    }
}
