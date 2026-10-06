package co.worklytics.psoxy;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import lombok.extern.java.Log;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.concurrent.ExecutorService;

/**
 * simple wrapper over HttpRequestHandler; handles spinning up the application, as needed; then
 * routing requests to the handler
 */
@Log
public class Route implements HttpFunction {

    final GcpContainerStartup startup = new GcpContainerStartup();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        GcpContainer container = startup.getOrCreate(DaggerGcpContainer::create);
        if (startup.failed()) {
            GcpConfigStoreResponses.write(request, response, startup.failure());
            return;
        }

        if (request.getMethod() == null) {
            log.warning("HTTP method of  com.google.cloud.functions.HttpRequest is null !???!");
        }

        try {
            container.httpRequestHandler().service(request, response);
        } catch (Throwable e) {
            if (!GcpClientConnectivity.isTransportFailure(e)) {
                throw e;
            }
            GcpConfigStoreResponses.write(request, response, e);
        }
    }

}
