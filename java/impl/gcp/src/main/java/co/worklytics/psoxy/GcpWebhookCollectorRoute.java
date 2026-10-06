package co.worklytics.psoxy;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
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
            GcpConfigStoreResponses.write(request, response, startup.failure());
            return;
        }
        try {
            container.gcpWebhookCollectionHandler().handle(request, response);
        } catch (Throwable e) {
            if (!GcpClientConnectivity.isTransportFailure(e)) {
                throw e;
            }
            GcpConfigStoreResponses.write(request, response, e);
        }
    }
}
