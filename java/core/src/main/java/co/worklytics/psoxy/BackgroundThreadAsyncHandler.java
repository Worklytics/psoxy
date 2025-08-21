package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * Originally created as quick/dirty way to handle async requests in GCP Cloud Functions, but that's not guaranteed to work
 * (e.g. if the function instance is killed before the thread completes).
 *
 * Still might be relevant in containerized deployments, so kept atm.
 */
@Log
public class BackgroundThreadAsyncHandler implements AsyncApiDataRequestHandler {

    ApiDataRequestHandler apiDataRequestHandler;
    ExecutorService executorService;

    // why lombok @NoArgsConstructor(onConstructor = @Inject) doesn't work here?
    @Inject
    BackgroundThreadAsyncHandler(ApiDataRequestHandler apiDataRequestHandler, ExecutorService executorService) {
        this.apiDataRequestHandler = apiDataRequestHandler;
        this.executorService = executorService;
    }

    @Override
    public void handle(HttpEventRequest request, ApiDataRequestHandler.ProcessingContext processingContext) {
        if (request.getHttpMethod() == null) {
            log.warning("HTTP method of com.google.cloud.functions.HttpRequest is null !???!");
        }
        ApiDataRequestHandler.ProcessingContext asyncContext = processingContext.toBuilder()
            .async(true)
            .build();

        executorService.submit(() -> {
            try {
                apiDataRequestHandler.handle(request, asyncContext);
            } catch (Exception e) {
                log.log(Level.SEVERE, "Error handling async request", e);
            }
        });
    }
}
