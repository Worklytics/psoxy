package co.worklytics.psoxy.storage.impl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Types;
import blue.strategic.parquet.ParquetWriter;

public class ParquetRecordWriter implements RecordWriter {

    private final OutputStream outputStream;
    private File tempFile;
    private ParquetWriter<Map<String, Object>> writer;
    private boolean initialized = false;

    public ParquetRecordWriter(OutputStream out) {
        this.outputStream = out;
    }

    @Override
    public void beginRecordSet() throws IOException {
        // Create temp file
        this.tempFile = Files.createTempFile("psoxy-parquet-out-", ".parquet").toFile();
        this.tempFile.deleteOnExit();
    }

    @Override
    public void writeRecord(Map<String, Object> record) throws IOException {
        if (!initialized) {
            initializeWriter(record);
        }

        if (writer != null) {
            writer.write(record);
        }
    }

    @Override
    public void endRecordSet() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null; // Prevent double close
        }
        
        // Copy temp file to output stream
        if (tempFile != null && tempFile.exists()) {
            FileUtils.copyFile(tempFile, outputStream);
        }
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore, we are closing anyway
            }
        }
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    private void initializeWriter(Map<String, Object> firstRecord) throws IOException {
        // Infer schema from firstRecord keys
        Types.MessageTypeBuilder builder = Types.buildMessage();
        List<String> fieldNames = new ArrayList<>(firstRecord.keySet());
        Map<String, PrimitiveType.PrimitiveTypeName> fieldTypes = new HashMap<>();

        for (String key : fieldNames) {
            Object val = firstRecord.get(key);
            PrimitiveType.PrimitiveTypeName type = PrimitiveType.PrimitiveTypeName.BINARY;
            LogicalTypeAnnotation logicalType = LogicalTypeAnnotation.stringType();

            if (val instanceof Integer) {
                type = PrimitiveType.PrimitiveTypeName.INT32;
                logicalType = null;
            } else if (val instanceof Long) {
                type = PrimitiveType.PrimitiveTypeName.INT64;
                logicalType = null;
            } else if (val instanceof Double) {
                type = PrimitiveType.PrimitiveTypeName.DOUBLE;
                logicalType = null;
            } else if (val instanceof Boolean) {
                type = PrimitiveType.PrimitiveTypeName.BOOLEAN;
                logicalType = null;
            }

            fieldTypes.put(key, type);

            Types.PrimitiveBuilder<PrimitiveType> fieldBuilder = Types.optional(type);
            if (logicalType != null) {
                fieldBuilder = fieldBuilder.as(logicalType);
            }
            builder.addField(fieldBuilder.named(key));
        }
        
        MessageType schema = builder.named("Record");

        // Initialize wrapper writer
        this.writer = ParquetWriter.writeFile(schema, tempFile, (map, valueWriter) -> {
            for (String key : fieldNames) {
                Object val = map.get(key);
                if (val != null) {
                    PrimitiveType.PrimitiveTypeName type = fieldTypes.get(key);
                    switch (type) {
                        case INT32:
                            valueWriter.write(key, (Integer) val);
                            break;
                        case INT64:
                            valueWriter.write(key, (Long) val);
                            break;
                        case DOUBLE:
                            valueWriter.write(key, (Double) val);
                            break;
                        case BOOLEAN:
                            valueWriter.write(key, (Boolean) val);
                            break;
                        default:
                            valueWriter.write(key, String.valueOf(val));
                            break;
                    }
                }
            }
        });

        initialized = true;
    }
}
