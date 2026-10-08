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
        GcpContainer container = startup.getOrCreate(DaggerGcpContainer::create);
        if (startup.failed()) {
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
            GcpConfigStoreResponses.write(request, response, container.connectivityFailureResponses(),
                    failure.get(), e, false);
        }
    }
}
