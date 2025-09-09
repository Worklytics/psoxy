package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;

import java.io.IOException;

public interface ApiSanitizedDataOutput {

    /**
     * writes sanitized content, retrieved in response to the request, to this side output, if enabled
     *
     * @param content to write to side output (maybe modified form of the response)
     */
    void writeSanitized(ProcessedContent content, ApiDataRequestHandler.ProcessingContext processingContext) throws IOException;

    boolean hasSanitizedOutput();
}
