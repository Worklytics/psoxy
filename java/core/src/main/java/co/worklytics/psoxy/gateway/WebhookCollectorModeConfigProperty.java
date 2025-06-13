package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 *
 * arguably these should be per-endpoint??
 *
 */
@NoArgsConstructor
@AllArgsConstructor
public enum WebhookCollectorModeConfigProperty implements ConfigService.ConfigProperty {

    /**
     * where any webhooks received by this collector should be sent.
     *
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
     * where any *batches* of webhooks received by this collector should be sent.
     *
     * (atm, batches only received via queues, such as SQS or PubSub that support batching of messages)
     *
     * could be S3, GCS, etc ...
     *
     *  s3://my-bucket/prefix/
     *  gs://my-bucket/prefix/
     *
     * should be something URI-like, with the 'type' of the Output able to be inferred from the URI scheme.
     */
    WEBHOOK_BATCH_OUTPUT,

    ;


    @Getter(onMethod_ = @Override)
    private SupportedSource supportedSource = SupportedSource.ENV_VAR;
}
