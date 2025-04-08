package co.worklytics.psoxy;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import lombok.extern.java.Log;
import java.io.IOException;

/**
 * simple wrapper over HttpRequestHandler; handles spinning up the application, as needed; then
 * routing requests to the handler
 */
@Log
public class Route implements HttpFunction {

    volatile GcpContainer container;


    @Override
    public void service(HttpRequest request, HttpResponse response) {
        injectDependenciesIfNeeded();

        container.httpRequestHandler().service(request, response);
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
