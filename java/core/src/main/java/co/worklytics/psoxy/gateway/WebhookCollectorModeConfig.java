package co.worklytics.psoxy.gateway;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * POJO collecting all configuration values for webhook collector mode
 */
@Value
@Builder
public class WebhookCollectorModeConfig {

    /**
     * Factory method to build config from ConfigService
     */
    public static WebhookCollectorModeConfig fromConfigService(ConfigService configService) {
        WebhookCollectorModeConfigBuilder builder = WebhookCollectorModeConfig.builder();
        
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.ACCEPTED_AUTH_KEYS)
            .ifPresent(builder::acceptedAuthKeys);
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.ALLOW_ORIGINS)
            .ifPresent(builder::allowOrigins);
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.AUTH_ISSUER)
            .ifPresent(builder::authIssuer);
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.REQUIRE_AUTHORIZATION_HEADER)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::requireAuthorizationHeader);
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_OUTPUT)
            .ifPresent(builder::webhookOutput);
        configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_BATCH_OUTPUT)
            .ifPresent(builder::webhookBatchOutput);
        
        return builder.build();
    }

    /**
     * a CSV of keys that, if values sent with webhook as authorization header, has been signed by, will
     * be considered valid.
     *
     * Examples:
     * - `aws-kms:aws-kms:arn:aws:kms:REGION:ACCOUNT_ID:alias/ALIAS_NAME`
     * - `base64:BASE64_ENCODED_PUBLIC_KEY` - must be RSA public key in base64 format
     * - `gcp-kms:projects/{project}/locations/{location}/keyRings/{keyRing}/cryptoKeys/{key}/cryptoKeyVersions/{version}`
     *
     */
    String acceptedAuthKeys;

    /**
     * a CSV of origins that are allowed to send webhooks to this collector. aligned with CORS standards
     *
     * default to "*", meaning all origins are allowed.
     */
    @NonNull
    @Builder.Default
    String allowOrigins = "*";

    /**
     * this should actually be URL to collector itself, to be used to produce OpenID Connect Discovery Document
     *
     */
    String authIssuer;

    /**
     * if false, proxy will not require or validate any Authorization header(s) sent with webhooks.
     *
     * false does NOT mean there is no authentication/authorization of webhook collection; simply that it is not done within
     * the proxy runtime itself. Other controls at API Gateway, IAM, or other layers may still be in place to perform auth.
     *
     * defaults to true
     */
    @NonNull
    @Builder.Default
    Boolean requireAuthorizationHeader = true;

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
    String webhookOutput;

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
    String webhookBatchOutput;

    /**
     * Get accepted auth keys as optional
     */
    public Optional<String> getAcceptedAuthKeys() {
        return Optional.ofNullable(acceptedAuthKeys);
    }

    /**
     * Get auth issuer as optional
     */
    public Optional<String> getAuthIssuer() {
        return Optional.ofNullable(authIssuer);
    }

    /**
     * Get webhook output as optional
     */
    public Optional<String> getWebhookOutput() {
        return Optional.ofNullable(webhookOutput);
    }

    /**
     * Get webhook batch output as optional
     */
    public Optional<String> getWebhookBatchOutput() {
        return Optional.ofNullable(webhookBatchOutput);
    }

    /**
     * Internal enum for config property keys
     * 
     * arguably these should be per-endpoint??
     */
    @NoArgsConstructor
    @AllArgsConstructor
    enum WebhookCollectorModeConfigProperty implements ConfigService.ConfigProperty {

        /**
         * a CSV of keys that, if values sent with webhook as authorization header, has been signed by, will
         * be considered valid.
         *
         * Examples:
         * - `aws-kms:aws-kms:arn:aws:kms:REGION:ACCOUNT_ID:alias/ALIAS_NAME`
         * - `base64:BASE64_ENCODED_PUBLIC_KEY` - must be RSA public key in base64 format
         * - `gcp-kms:projects/{project}/locations/{location}/keyRings/{keyRing}/cryptoKeys/{key}/cryptoKeyVersions/{version}`
         *
         */
        ACCEPTED_AUTH_KEYS,

        /**
         * a CSV of origins that are allowed to send webhooks to this collector. aligned with CORS standards
         *
         *default to "*", meaning all origins are allowed.
         */
        ALLOW_ORIGINS,

        /**
         * this should actually be URL to collector itself, to be used to produce OpenID Connect Discovery Document
         *
         */
        AUTH_ISSUER,


        /**
         * if false, proxy will not require or validate any Authorization header(s) sent with webhooks.
         *
         * false does NOT mean there is no authentication/authorization of webhook collection; simply that it is not done within
         * the proxy runtime itself. Other controls at API Gateway, IAM, or other layers may still be in place to perform auth.
         *
         *
         * defaults to true
         */
        REQUIRE_AUTHORIZATION_HEADER,

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
}
