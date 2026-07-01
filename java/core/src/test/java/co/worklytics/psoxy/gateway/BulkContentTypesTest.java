package co.worklytics.psoxy.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BulkContentTypesTest {

    @Test
    void describeContentTypes_sortsForStableOutput() {
        assertEquals(
            "application/json, application/x-ndjson",
            BulkContentTypes.describeContentTypes(Set.of(
                BulkContentTypes.NDJSON.getMimeType(),
                BulkContentTypes.JSON.getMimeType())));
    }
}
