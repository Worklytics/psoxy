package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CsvRecordReader implements RecordReader {

    final Reader reader;
    
    CSVParser parser;
    Iterator<CSVRecord> iterator;

    List<String> orderedHeaders;

    @Override
    public Map<String, Object> readRecord() throws IOException {
        if (parser == null) {
            parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .get()
                .parse(reader);
            iterator = parser.iterator();
            
            if (parser.getHeaderMap() != null) {
                orderedHeaders = new ArrayList<>(parser.getHeaderMap().size());
                // Sort headers by column index to preserve order
                parser.getHeaderMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEachOrdered(e -> orderedHeaders.add(e.getKey()));
            }
        }

        if (iterator.hasNext()) {
            CSVRecord record = iterator.next();
            if (orderedHeaders != null) {
                LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                for (String header : orderedHeaders) {
                    map.put(header, record.get(header));
                }
                return map;
            }
            return new LinkedHashMap<>(record.toMap());
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
