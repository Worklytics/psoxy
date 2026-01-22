package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CsvRecordReader implements RecordReader {

    final Reader reader;
    
    CSVParser parser;
    Iterator<CSVRecord> iterator;

    @Override
    public Object readRecord() throws IOException {
        if (parser == null) {
            parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .get()
                .parse(reader);
            iterator = parser.iterator();
        }

        if (iterator.hasNext()) {
            return iterator.next().toMap();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (parser != null) {
            parser.close();
        } else {
            reader.close();
        }
    }
}
