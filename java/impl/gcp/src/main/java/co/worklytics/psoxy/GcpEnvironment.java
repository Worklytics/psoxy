package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HostEnvironment;
import lombok.NoArgsConstructor;

import javax.inject.Inject;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpEnvironment implements HostEnvironment {

    // https://cloud.google.com/functions/docs/configuring/env-var#newer_runtimes
    private enum RuntimeEnvironmentVariables {
        K_SERVICE
    }

    @Override
    public String getInstanceId() {
        return System.getenv(GcpEnvironment.RuntimeEnvironmentVariables.K_SERVICE.name());
    }


}
