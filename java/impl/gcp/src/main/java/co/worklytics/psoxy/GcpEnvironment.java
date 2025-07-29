package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import javax.inject.Inject;
import java.util.Set;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpEnvironment implements HostEnvironment {

    // https://cloud.google.com/functions/docs/configuring/env-var#newer_runtimes
    // now: https://cloud.google.com/run/docs/container-contract#env-vars  ??
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
        ;
    }

    @Builder
    @Value
    static class WebhookCollectorModeConfig {
         /**
         * subscription name from which to pull webhooks
         *
         * eg, "projects/my-project/subscriptions/my-webhook-collector-subscription"
         */
        private String batchMergeSubscription;

        /**
         * number of webhooks to process in a batch
         * 
         * default: 100
         */
        private int batchSize;

        /**
         * timeout for a batch invocation, in seconds
         * 
         * default: 60
         */
        private int batchInvocationTimeoutSeconds;

        static WebhookCollectorModeConfig fromConfigService(ConfigService configService) {
            return WebhookCollectorModeConfig.builder()
                .batchMergeSubscription(configService.getConfigPropertyOrError(WebhookCollectorModeConfigProperty.BATCH_MERGE_SUBSCRIPTION))
                .batchSize(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.BATCH_SIZE).map(Integer::parseInt).orElse(100))
                .batchInvocationTimeoutSeconds(configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.BATCH_INVOCATION_TIMEOUT_SECONDS).map(Integer::parseInt).orElse(60))
                .build();
        }
    }

    enum WebhookCollectorModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {
        /**
         * subscription name from which to pull webhooks
         *
         * eg, "projects/my-project/subscriptions/my-webhook-collector-subscription"
         */
        BATCH_MERGE_SUBSCRIPTION,

        /**
         * number of webhooks to process in a batch
         * 
         * default: 100
         */
        BATCH_SIZE,

        /**
         * timeout for a batch invocation, in seconds
         * 
         * default: 60
         */
        BATCH_INVOCATION_TIMEOUT_SECONDS,
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
