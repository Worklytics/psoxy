package co.worklytics.psoxy;

import static co.worklytics.psoxy.S3BulkTriggerEventConstants.EVENTBRIDGE_DETAIL_TYPE_OBJECT_CREATED;
import static co.worklytics.psoxy.S3BulkTriggerEventConstants.EVENTBRIDGE_SOURCE_AWS_S3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class S3BulkObjectCreatedEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void fromEventBridgeObjectCreated() throws Exception {
        String json = """
            {
              "version": "0",
              "id": "abc",
              "detail-type": "%s",
              "source": "%s",
              "account": "123456789012",
              "time": "2026-01-01T00:00:00Z",
              "region": "us-east-1",
              "resources": ["arn:aws:s3:::my-input-bucket"],
              "detail": {
                "version": "0",
                "bucket": { "name": "my-input-bucket" },
                "object": { "key": "exports/psoxy/data.csv", "size": 10, "etag": "x", "sequencer": "y" },
                "request-id": "r",
                "requester": "123456789012",
                "source-ip-address": "1.2.3.4",
                "reason": "PutObject"
              }
            }
            """.formatted(EVENTBRIDGE_DETAIL_TYPE_OBJECT_CREATED, EVENTBRIDGE_SOURCE_AWS_S3);
        @SuppressWarnings("unchecked")
        Map<String, Object> event = MAPPER.readValue(json, Map.class);

        S3BulkObjectCreatedEvent parsed = S3BulkObjectCreatedEvent.fromLambdaEvent(event);

        assertEquals("my-input-bucket", parsed.getBucketName());
        assertEquals("exports/psoxy/data.csv", parsed.getObjectKey());
    }

    @Test
    void rejectsEventBridgeWithWrongDetailType() {
        assertThrows(IllegalArgumentException.class, () ->
            S3BulkObjectCreatedEvent.fromLambdaEvent(Map.of(
                "source", EVENTBRIDGE_SOURCE_AWS_S3,
                "detail-type", "Object Deleted",
                "detail", Map.of()
            )));
    }

    @Test
    void rejectsUnknownShape() {
        assertThrows(IllegalArgumentException.class, () ->
            S3BulkObjectCreatedEvent.fromLambdaEvent(Map.of("foo", "bar")));
    }
}
