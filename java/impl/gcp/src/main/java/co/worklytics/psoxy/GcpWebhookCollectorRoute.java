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

    volatile GcpContainer container;


//    static {
//        Security.addProvider(new BouncyCastleProvider());
//    }


    @Override
    public void service(HttpRequest request, HttpResponse response) {
        injectDependenciesIfNeeded();
        container.gcpWebhookCollectionHandler().handle(request, response);
    }

    void injectDependenciesIfNeeded() {
        if (container == null) {
            synchronized (this) {
                if (container == null) {
                    container = DaggerGcpContainer.create();
                }
            }
        }
    }
}
