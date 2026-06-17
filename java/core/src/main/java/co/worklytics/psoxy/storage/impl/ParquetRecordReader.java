package co.worklytics.psoxy.storage.impl;

import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ParquetReader;
import lombok.extern.java.Log;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@Log
public class ParquetRecordReader implements RecordReader {

    private final File tempFile;
    private final Stream<Map<String, Object>> stream;
    private final Iterator<Map<String, Object>> iterator;

    public ParquetRecordReader(InputStream in) throws IOException {
        // Create temp file
        this.tempFile = Files.createTempFile("input-", ".parquet").toFile();

        try {
            // Copy input stream to temp file
            FileUtils.copyInputStreamToFile(in, tempFile);

            // Open stream of content as Maps using custom Hydrator
            Hydrator<Map<String, Object>, Map<String, Object>, String[]> hydrator = new Hydrator<Map<String, Object>, Map<String, Object>, String[]>() {
                @Override
                public Map<String, Object> start() {
                    return new LinkedHashMap<>();
                }

                @Override
                public Map<String, Object> add(Map<String, Object> map, String[] heading, Object o) {
                    map.put(heading[0], o);
                    return map;
                }

                @Override
                public Map<String, Object> finish(Map<String, Object> map) {
                    return map;
                }
            };

            this.stream = ParquetReader.streamContent(tempFile, hydrator);
            this.iterator = stream.iterator();
        } catch (Exception e) {
            if (this.tempFile.exists()) {
                if (!this.tempFile.delete()) {
                    log.warning("Failed to delete temporary parquet file during initialization failure: " + this.tempFile.getAbsolutePath());
                }
            }
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to initialize ParquetRecordReader", e);
        }
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
        try {
            if (stream != null) {
                stream.close();
            }
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (!tempFile.delete()) {
                    log.warning("Failed to delete temporary parquet file on close: " + tempFile.getAbsolutePath());
                }
            }
        }
    }
}
