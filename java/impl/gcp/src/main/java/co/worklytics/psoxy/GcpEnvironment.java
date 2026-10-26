package co.worklytics.psoxy;

import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import com.google.common.annotations.VisibleForTesting;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpEnvironment implements HostEnvironment {

    // https://cloud.google.com/functions/docs/configuring/env-var#newer_runtimes
    // now: https://cloud.google.com/run/docs/container-contract#env-vars  ??
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
        ;
    }

    /**
     * GCP-specific URLs/topics for the API data connector (distinct from core {@link co.worklytics.psoxy.gateway.ApiModeConfig}).
     */
    @Builder
    @Value
    static class GcpApiModeConfig {

        /**
         * the URL of the service, e.g. "https://my-service-12345.a.run.app", if known/applicable
         */
        Optional<String> serviceUrl;

        /**
         * Pub/Sub topic to which to queue requests for async processing, if any
         *
         * eg, "projects/my-project/topics/my-topic"
         */
        Optional<String> asyncPubSubQueue;

        @VisibleForTesting
        enum GcpApiModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {

            ASYNC_PUB_SUB_QUEUE,
            SERVICE_URL,
        }

        static GcpApiModeConfig fromConfigService(ConfigService configService) {
            if (!(configService instanceof CompositeConfigService)) {
                throw new IllegalStateException("configService must be a CompositeConfigService");
            }

            return GcpApiModeConfig.builder()
                .serviceUrl(configService.getConfigPropertyAsOptional(GcpApiModeConfigProperty.SERVICE_URL))
                .asyncPubSubQueue(configService.getConfigPropertyAsOptional(GcpApiModeConfigProperty.ASYNC_PUB_SUB_QUEUE))
                .build();
        }
    }


    @Builder
    @Value
    static class GcpWebhookCollectorModeConfig {

        /**
         * the URL of the service, e.g. "https://my-service-12345.a.run.app"
         */
        String serviceUrl;

         /**
         * subscription name from which to pull webhooks
         *
         * eg, "projects/my-project/subscriptions/my-webhook-collector-subscription"
         */
        String batchMergeSubscription;

        /**
         * number of webhooks to process in a batch
         *
         * default: 100
         */
         int batchSize;

        /**
         * timeout for a batch invocation, in seconds
         *
         * default: 60
         */
        int batchInvocationTimeoutSeconds;

        private enum GcpWebhookCollectorModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {

            BATCH_MERGE_SUBSCRIPTION,

            BATCH_SIZE,

            BATCH_INVOCATION_TIMEOUT_SECONDS,

            SERVICE_URL,
        }

        static GcpWebhookCollectorModeConfig fromConfigService(ConfigService configService) {
            return GcpWebhookCollectorModeConfig.builder()
                .serviceUrl(configService.getConfigPropertyOrError(GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.SERVICE_URL))
                .batchMergeSubscription(configService.getConfigPropertyOrError(GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.BATCH_MERGE_SUBSCRIPTION))
                .batchSize(configService.getConfigPropertyAsOptional(GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.BATCH_SIZE)
                        .map(value -> ConfigService.parseIntValue(
                                GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.BATCH_SIZE, value))
                        .orElse(100))
                .batchInvocationTimeoutSeconds(configService.getConfigPropertyAsOptional(
                                GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.BATCH_INVOCATION_TIMEOUT_SECONDS)
                        .map(value -> ConfigService.parseIntValue(
                                GcpWebhookCollectorModeConfig.GcpWebhookCollectorModeConfigProperty.BATCH_INVOCATION_TIMEOUT_SECONDS,
                                value))
                        .orElse(60))
                .build();
        }
    }

    /**
     * Which {@code X-Forwarded-For} hop the optional IP allowlist reads.
     * Shared by API connectors and webhook collectors.
     */
    @Builder
    @Value
    static class ClientIpConfig {

        /** Last hop. Used when {@link ClientIpConfigProperty#CLIENT_IP_X_FORWARDED_FOR_OFFSET} is unset. */
        static final int DEFAULT_X_FORWARDED_FOR_OFFSET = -1;

        /**
         * Negative offset from the end of {@code X-Forwarded-For}. {@code -1} is the last address.
         */
        @Builder.Default
        int xForwardedForOffset = DEFAULT_X_FORWARDED_FOR_OFFSET;

        @VisibleForTesting
        enum ClientIpConfigProperty implements ConfigService.ConfigProperty {

            CLIENT_IP_X_FORWARDED_FOR_OFFSET,
        }

        static ClientIpConfig fromConfigService(ConfigService configService) {
            int offset = configService.getConfigPropertyAsOptional(ClientIpConfigProperty.CLIENT_IP_X_FORWARDED_FOR_OFFSET)
                .map(ClientIpConfig::parseOffset)
                .orElse(DEFAULT_X_FORWARDED_FOR_OFFSET);
            return ClientIpConfig.builder()
                .xForwardedForOffset(offset)
                .build();
        }

        private static int parseOffset(String value) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return DEFAULT_X_FORWARDED_FOR_OFFSET;
            }
            int offset = ConfigService.parseIntValue(ClientIpConfigProperty.CLIENT_IP_X_FORWARDED_FOR_OFFSET, trimmed);
            if (offset >= 0) {
                throw new IllegalArgumentException(
                    ClientIpConfigProperty.CLIENT_IP_X_FORWARDED_FOR_OFFSET.name()
                        + " must be a negative offset from the end of X-Forwarded-For (for example -1), not '"
                        + value + "'.");
            }
            return offset;
        }
    }

    @Override
    public String getInstanceId() {
        return System.getenv(GcpEnvironment.RuntimeEnvironmentVariables.K_SERVICE.name());
    }


    @Override
    public Set<String> getSupportedOutputKinds() {
        return Set.of(
            "gs",
            "pubsub"
           // "bq"
        );
    }

    @Getter
    final String internalServiceAuthIssuer = "https://accounts.google.com";

    @Getter
    final String pubSubUserAgent = "Google-Cloud-PubSub";

    @Getter
    final String googleApisUserAgent = "APIs-Google";

    @Getter
    final String cloudSchedulerUserAgent = "Google-Cloud-Scheduler";

    public static final String PUBSUB_DELIVERY_ATTEMPT_HEADER = "X-Goog-Delivery-Attempt";
}
