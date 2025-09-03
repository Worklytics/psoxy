package co.worklytics.psoxy.gateway.output;

import java.io.IOException;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;

/**
 * a side output for API data retrieved by proxy instance
 *
 *
 * alternatives: - @Named("original") SideOutput sideOutputOriginal; @Named("sanitized") SideOutput
 * sideOutputSanitized; let CommonRequestHandler decide which to use - concern with this is
 * 1) @Named injection is tedious to deal with, dependent on magic strings, hard to trace usage -
 * SideOutputForOriginal, SideOutputForSanitized, interfaces; inject those? almost equivalent/better
 * than Named, just bc no magic strings to hunt for
 */
public interface ApiDataSideOutput {

    /**
     * writes raw content, retrieved in response to the request, to this side output, if enabled
     *
     * @param content to write to side output (maybe modified form of the response)
     */
    void writeRaw(ProcessedContent content,
            ApiDataRequestHandler.ProcessingContext processingContext) throws IOException;

    boolean hasRawOutput();

}
