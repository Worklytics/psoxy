package co.worklytics.psoxy.gateway;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public enum WebhookCollectorModeConfigProperty implements ConfigService.ConfigProperty {

    /**
     * if set, output will be written to this
     * only applicable in webhook collector mode.
     *
     * full queue URL:  https://sqs.us-east-1.amazonaws.com/177715257436/MyQueue
     *
     * generalize, and expect it to be a URI?
     *   - SQS: https://sqs.us-east-1.amazonaws.com/177715257436/MyQueue
     *   - S3: https://s3.us-east-1.amazonaws.com/bucket-name/key
     *   - PubSub topic : https://pubsub.googleapis.com/projects/{PROJECT_ID}/topics/{TOPIC_ID}
     *
     * TODO: ambiguiity here that single lambdas implement two stages:
     *    webhook --> queue
     *    queue --> output
     *
     */
    OUTPUT_PROCESSED_WEBHOOKS,
    OUTPUT_QUEUE,
    ;


    @Getter(onMethod_ = @Override)
    private SupportedSource supportedSource = SupportedSource.ENV_VAR;
}
