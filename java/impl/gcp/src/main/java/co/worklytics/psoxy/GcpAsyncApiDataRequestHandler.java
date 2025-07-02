package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;

public class GcpAsyncApiDataRequestHandler implements AsyncApiDataRequestHandler {


    @Override
    public void handle(HttpEventRequest request, ApiDataRequestHandler.ProcessingContext processingContext) {

        // believe GCP *can* have background threads, so can do this in process


        throw new UnsupportedOperationException("Not implemented yet");
    }
}
