package co.worklytics.psoxy;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import lombok.extern.java.Log;
import java.io.IOException;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * simple wrapper over HttpRequestHandler; handles spinning up the application, as needed; then
 * routing requests to the handler
 */
@Log
public class Route implements HttpFunction {

    volatile GcpContainer container;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) {
        injectDependenciesIfNeeded();

        if (request.getMethod() == null) {
            log.warning("HTTP method of  com.google.cloud.functions.HttpRequest is null !???!");
        }

        container.httpRequestHandler().service(request, response);

        ExecutorService executorService = container.executorService();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered");
            executorService.shutdown();
        }));
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
