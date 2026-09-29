package co.worklytics.psoxy.storage.impl;

import java.io.UncheckedIOException;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Formats record field values for tabular writers (CSV / Parquet string columns).
 * Scalars stay as-is; Maps/Collections (augment outputs) serialize as JSON strings —
 * not {@link Object#toString()} ({@code {category=...}}).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RecordCellSerialization {

    static Object forTabularCell(Object value, @NonNull ObjectMapper objectMapper) {
        if (value == null
            || value instanceof String
            || value instanceof Number
            || value instanceof Boolean) {
            return value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(
                "Failed to JSON-serialize structured record cell value", e);
        }
    }
}
