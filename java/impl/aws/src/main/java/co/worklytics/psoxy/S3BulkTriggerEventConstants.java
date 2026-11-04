package co.worklytics.psoxy;

/**
 * Event shape identifiers for bulk S3 object-created triggers (native S3 notification vs EventBridge).
 */
final class S3BulkTriggerEventConstants {

    static final String EVENTBRIDGE_SOURCE_AWS_S3 = "aws.s3";
    static final String EVENTBRIDGE_DETAIL_TYPE_OBJECT_CREATED = "Object Created";

    private S3BulkTriggerEventConstants() {
    }
}
