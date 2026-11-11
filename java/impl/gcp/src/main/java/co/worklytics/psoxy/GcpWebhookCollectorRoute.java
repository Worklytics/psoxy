package co.worklytics.psoxy;

import java.util.Optional;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import lombok.extern.java.Log;

/**
 * simple wrapper over GcpWebhookCollectionHandler; handles spinning up the application, as needed; then
 * routing requests to the handler
 */
@Log
public class GcpWebhookCollectorRoute implements HttpFunction {

    final GcpContainerStartup startup = new GcpContainerStartup();

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        GcpContainer container = startup.getOrCreate(() -> {
            GcpContainer created = DaggerGcpContainer.create();
            created.gcpWebhookCollectionHandler();
            return created;
        });
        if (container == null) {
            startup.write(request, response);
            return;
        }
        try {
            container.gcpWebhookCollectionHandler().handle(request, response);
        } catch (Throwable e) {
            Optional<DependencyConnectivityFailure> failure = container.connectivityFailures().match(e);
            if (failure.isEmpty()) {
                throw e;
            }
            container.configStoreResponses().write(request, response, failure.get(), e, false,
                    GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET);
        }
    }
}
