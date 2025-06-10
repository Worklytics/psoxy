package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TODO: refactor this
 *   WEBHOOK_OUTPUT
 *   WEBHOOK_BATCH_OUTPUT
 *
 * arguably these should be per-endpoint??
 *
 */
@NoArgsConstructor
@AllArgsConstructor
public enum WebhookCollectorModeConfigProperty implements ConfigService.ConfigProperty {

    /**
     * could be SQS, PubSub, GCS, S3, etc ..
     *
     *  (if expect each webhook payload small, and we want to group them into single gzipped files, than
     *   solution is to go through queue like SQS/PubSub first, then write to S3/GCS)
     *
     * should be something URI-like, with the 'type' of the Output able to be inferred from the URI scheme.
     *
     *  - PubSub topic : https://pubsub.googleapis.com/projects/{PROJECT_ID}/topics/{TOPIC_ID}
     *  - SQS: https://sqs.us-east-1.amazonaws.com/177715257436/MyQueue
     *
     */
    WEBHOOK_OUTPUT,

    /**
     * if set, this is the output that webhook payloads received via queue callbacks will be written to.
     *
     * (q: is this ACTUALLY webhook specific? arguably not ... but for simplicity leave it here for now;
     * could introduce a QueuedBatch collector mode in the future ...)
     *
     * could be S3, GCS, etc ...
     *
     *  s3://my-bucket/prefix/
     *  gs://my-bucket/prefix/
     *
     * should be something URI-like, with the 'type' of the Output able to be inferred from the URI scheme.
     */
    QUEUED_WEBHOOK_OUTPUT,

    ;


    @Getter(onMethod_ = @Override)
    private SupportedSource supportedSource = SupportedSource.ENV_VAR;
}
