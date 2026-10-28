package co.worklytics.psoxy;

import static co.worklytics.psoxy.S3BulkTriggerEventConstants.EVENTBRIDGE_DETAIL_TYPE_OBJECT_CREATED;
import static co.worklytics.psoxy.S3BulkTriggerEventConstants.EVENTBRIDGE_SOURCE_AWS_S3;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Value;

/**
 * Normalized bucket/key from either a direct S3 notification or an EventBridge S3 Object Created event.
 */
@Value
class S3BulkObjectCreatedEvent {

    String bucketName;
    String objectKey;

    static S3BulkObjectCreatedEvent fromLambdaEvent(Map<String, Object> input) {
        if (EVENTBRIDGE_SOURCE_AWS_S3.equals(input.get("source"))) {
            if (!EVENTBRIDGE_DETAIL_TYPE_OBJECT_CREATED.equals(input.get("detail-type"))) {
                throw new IllegalArgumentException(
                    "Unsupported EventBridge detail-type for bulk S3 trigger: " + input.get("detail-type"));
            }
            return fromEventBridge(input);
        }
        if (input.get("Records") instanceof List<?> records && !records.isEmpty()) {
            return fromS3Notification(input);
        }
        throw new IllegalArgumentException("Unsupported bulk S3 trigger event shape");
    }

    private static S3BulkObjectCreatedEvent fromS3Notification(Map<String, Object> input) {
        ObjectMapper mapper = new ObjectMapper();
        S3Event s3Event = mapper.convertValue(input, S3Event.class);
        List<S3EventNotification.S3EventNotificationRecord> records = s3Event.getRecords();
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("S3 event has no records");
        }
        S3EventNotification.S3EventNotificationRecord record = records.get(0);
        String bucket = record.getS3().getBucket().getName();
        String key = record.getS3().getObject().getUrlDecodedKey();
        return new S3BulkObjectCreatedEvent(bucket, key);
    }

    @SuppressWarnings("unchecked")
    private static S3BulkObjectCreatedEvent fromEventBridge(Map<String, Object> input) {
        Map<String, Object> detail = (Map<String, Object>) input.get("detail");
        if (detail == null) {
            throw new IllegalArgumentException("EventBridge S3 event missing detail");
        }
        Map<String, Object> bucket = (Map<String, Object>) detail.get("bucket");
        Map<String, Object> object = (Map<String, Object>) detail.get("object");
        if (bucket == null || object == null) {
            throw new IllegalArgumentException("EventBridge S3 event missing bucket or object");
        }
        String bucketName = (String) bucket.get("name");
        String key = (String) object.get("key");
        if (bucketName == null || key == null) {
            throw new IllegalArgumentException("EventBridge S3 event missing bucket name or object key");
        }
        return new S3BulkObjectCreatedEvent(bucketName, urlDecodeKey(key));
    }

    private static String urlDecodeKey(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }
}
