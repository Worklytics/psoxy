package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

import java.io.IOException;

public interface RawApiDataSideOutput {


    String rawOutputKey(HttpRequest requestToSourceApi);

    void writeRaw(HttpRequest sourceApiRequest,
                  HttpResponse sourceApiResponse,
                  ApiDataRequestHandler.ProcessingContext processingContext) throws IOException;

 }
