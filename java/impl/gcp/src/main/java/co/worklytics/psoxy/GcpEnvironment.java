package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HostEnvironment;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    enum WebhookCollectorModeConfigProperty implements co.worklytics.psoxy.gateway.ConfigService.ConfigProperty {
        /**
         * subscription name from which to pull webhooks
         *
         * eg, "projects/my-project/subscriptions/my-webhook-collector-subscription"
         */
        BATCH_MERGE_SUBSCRIPTION,
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
