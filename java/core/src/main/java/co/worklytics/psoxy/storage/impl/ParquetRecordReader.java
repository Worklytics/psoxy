package co.worklytics.psoxy.storage.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ParquetReader;

public class ParquetRecordReader implements RecordReader {

    private final File tempFile;
    private final Stream<Map<String, Object>> stream;
    private final Iterator<Map<String, Object>> iterator;

    public ParquetRecordReader(InputStream in) throws IOException {
        // Create temp file
        this.tempFile = Files.createTempFile("input-", ".parquet").toFile();
        this.tempFile.deleteOnExit();

        // Copy input stream to temp file
        FileUtils.copyInputStreamToFile(in, tempFile);

        // Open stream of content as Maps using custom Hydrator
        this.stream = ParquetReader.streamContent(tempFile, (descriptors) -> new Hydrator<Map<String, Object>, Map<String, Object>>() {
            @Override
            public Map<String, Object> start() {
                return new LinkedHashMap<>();
            }

            @Override
            public Map<String, Object> add(Map<String, Object> map, String key, Object value) {
                map.put(key, value);
                return map;
            }

            @Override
            public Map<String, Object> finish(Map<String, Object> map) {
                return map;
            }
        });
        this.iterator = stream.iterator();
    }

    @Override
    public Map<String, Object> readRecord() throws IOException {
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
        if (tempFile.exists()) {
            tempFile.delete();
        }
    }
}
