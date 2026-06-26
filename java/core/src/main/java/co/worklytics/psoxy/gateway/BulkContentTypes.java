package co.worklytics.psoxy.gateway;

import java.util.Set;
import org.apache.hc.core5.http.ContentType;

/**
 * MIME types and related constants for bulk file processing and webhook batch merging.
 */
public final class BulkContentTypes {

    private BulkContentTypes() {}

    /**
     * Base MIME type values for bulk formats (without parameters such as {@code charset}).
     */
    public static final class MimeType {

        private MimeType() {}

        public static final String JSON = ContentType.APPLICATION_JSON.getMimeType();
        public static final String NDJSON = ContentType.APPLICATION_NDJSON.getMimeType();
        public static final String NDJSON_ALT = "application/ndjson";
        public static final String JSONLINES = "application/jsonlines";
        public static final String JSONLINES_ALT = "application/x-jsonlines";
        public static final String CSV = "text/csv";
        public static final String APPLICATION_CSV = "application/csv";
        public static final String PARQUET = "application/vnd.apache.parquet";
        public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

        public static final String CSV_UTF8 = CSV + "; charset=utf-8";
    }

    /**
     * Content-Types that cloud consoles often attach to bulk uploads, but that do not reflect the
     * file format.
     */
    public static final Set<String> KNOWN_GENERIC_UPLOAD_TYPES = Set.of(
        MimeType.FORM_URLENCODED
    );

    /**
     * Supported bulk Content-Type base values (parameters such as {@code charset} matched separately).
     */
    public static final Set<String> SUPPORTED_BULK_BASES = Set.of(
        MimeType.CSV,
        MimeType.APPLICATION_CSV,
        MimeType.JSON,
        MimeType.NDJSON,
        MimeType.NDJSON_ALT,
        MimeType.JSONLINES,
        MimeType.JSONLINES_ALT,
        MimeType.PARQUET
    );

    /**
     * Input content types that can be concatenated into newline-delimited JSON output.
     */
    public static final Set<String> MERGEABLE_JSON_RECORD_TYPES = Set.of(
        MimeType.JSON,
        MimeType.NDJSON,
        MimeType.NDJSON_ALT,
        MimeType.JSONLINES,
        MimeType.JSONLINES_ALT
    );

    public static String describeContentTypes(Set<String> types) {
        return String.join(", ", types);
    }
}
