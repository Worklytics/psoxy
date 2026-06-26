package co.worklytics.psoxy.gateway;

import java.util.Set;
import org.apache.hc.core5.http.ContentType;

/**
 * MIME types for bulk file processing and webhook batch merging.
 *
 * <p>Reuses {@link ContentType} from Apache HttpCore where available. Bulk formats that HttpCore
 * does not define (CSV, Parquet, JSON Lines variants) are registered via {@link ContentType#create}.
 */
public final class BulkContentTypes {

    private BulkContentTypes() {}

    // HttpCore-defined
    public static final ContentType JSON = ContentType.APPLICATION_JSON;
    public static final ContentType NDJSON = ContentType.APPLICATION_NDJSON;
    public static final ContentType FORM_URLENCODED = ContentType.APPLICATION_FORM_URLENCODED;

    // Not defined by HttpCore ContentType
    public static final ContentType NDJSON_ALT = ContentType.create("application/ndjson");
    public static final ContentType JSONLINES = ContentType.create("application/jsonlines");
    public static final ContentType JSONLINES_ALT = ContentType.create("application/x-jsonlines");
    public static final ContentType CSV = ContentType.create("text/csv");
    public static final ContentType APPLICATION_CSV = ContentType.create("application/csv");
    public static final ContentType PARQUET = ContentType.create("application/vnd.apache.parquet");

    /** Inferred CSV content type for object storage metadata (includes charset). */
    public static final String CSV_UTF8 = CSV.getMimeType() + "; charset=utf-8";

    /**
     * Content-Types that cloud consoles often attach to bulk uploads, but that do not reflect the
     * file format.
     */
    public static final Set<String> KNOWN_GENERIC_UPLOAD_TYPES = Set.of(
        FORM_URLENCODED.getMimeType()
    );

    /**
     * Supported bulk Content-Type base values (parameters such as {@code charset} matched separately).
     */
    public static final Set<String> SUPPORTED_BULK_BASES = Set.of(
        CSV.getMimeType(),
        APPLICATION_CSV.getMimeType(),
        JSON.getMimeType(),
        NDJSON.getMimeType(),
        NDJSON_ALT.getMimeType(),
        JSONLINES.getMimeType(),
        JSONLINES_ALT.getMimeType(),
        PARQUET.getMimeType()
    );

    /**
     * Input content types that can be concatenated into newline-delimited JSON output.
     */
    public static final Set<String> MERGEABLE_JSON_RECORD_TYPES = Set.of(
        JSON.getMimeType(),
        NDJSON.getMimeType(),
        NDJSON_ALT.getMimeType(),
        JSONLINES.getMimeType(),
        JSONLINES_ALT.getMimeType()
    );

    public static String describeContentTypes(Set<String> types) {
        return String.join(", ", types);
    }
}
