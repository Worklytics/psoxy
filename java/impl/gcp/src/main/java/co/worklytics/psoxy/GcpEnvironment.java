package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.inject.Inject;
import java.util.Set;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpEnvironment implements HostEnvironment {

    public static Object ApiModeConfigProperty;

    // https://cloud.google.com/functions/docs/configuring/env-var#newer_runtimes
    // now: https://cloud.google.com/run/docs/container-contract#env-vars  ??
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
        ;
    }

    interface HttpInvocationConfig {

        /**
         * the URL of the service, e.g. "https://my-service-12345.a.run.app"
         */
        String getServiceUrl();

        enum HttpInvocationConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {

            SERVICE_URL,
        }

    }

    @Builder
    @Value
    static class ApiModeConfig implements HttpInvocationConfig {

        String serviceUrl;

        /**
         * Pub/Sub topic to which to publish events
         *
         * eg, "projects/my-project/topics/my-topic"
         */
        String pubSubTopic;

        private enum ApiModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {

            PUB_SUB_TOPIC,
        }

        static ApiModeConfig fromConfigService(ConfigService configService) {
            return ApiModeConfig.builder()
                .serviceUrl(configService.getConfigPropertyOrError(HttpInvocationConfig.HttpInvocationConfigProperty.SERVICE_URL))
                .pubSubTopic(configService.getConfigPropertyOrError(ApiModeConfig.ApiModeConfigProperty.PUB_SUB_TOPIC))
                .build();
        }
    }


    @Builder
    @Value
    static class WebhookCollectorModeConfig implements  HttpInvocationConfig {

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

        private enum WebhookCollectorModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {

            BATCH_MERGE_SUBSCRIPTION,

            BATCH_SIZE,

            BATCH_INVOCATION_TIMEOUT_SECONDS,
        }

        static WebhookCollectorModeConfig fromConfigService(ConfigService configService) {
            return WebhookCollectorModeConfig.builder()
                .serviceUrl(configService.getConfigPropertyOrError(HttpInvocationConfig.HttpInvocationConfigProperty.SERVICE_URL))
                .batchMergeSubscription(configService.getConfigPropertyOrError(WebhookCollectorModeConfig.WebhookCollectorModeConfigProperty.BATCH_MERGE_SUBSCRIPTION))
                .batchSize(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfig.WebhookCollectorModeConfigProperty.BATCH_SIZE).map(Integer::parseInt).orElse(100))
                .batchInvocationTimeoutSeconds(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfig.WebhookCollectorModeConfigProperty.BATCH_INVOCATION_TIMEOUT_SECONDS).map(Integer::parseInt).orElse(60))
                .build();
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
    final String cloudSchedulerUserAgent = "Google-Cloud-Scheduler";

    //q: how can we get the REGION in which the function is running/executing?


}
