package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import javax.inject.Inject;

public class GcpAsyncApiDataRequestHandler implements AsyncApiDataRequestHandler {


    // why lombok @NoArgsConstructor(onConstructor = @Inject) doesn't work here?
    @Inject
    GcpAsyncApiDataRequestHandler() {

    }

    @Override
    public void handle(HttpEventRequest request, ApiDataRequestHandler.ProcessingContext processingContext) {

        // believe GCP *can* have background threads, so can do this in process


        throw new UnsupportedOperationException("Not implemented yet");
    }
}
