package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;

import java.io.IOException;

/**
 * a side output for API data retrieved by  proxy instance

 *
 *  alternatives:
 *  -  @Named("original") SideOutput sideOutputOriginal; @Named("sanitized") SideOutput sideOutputSanitized; let CommonRequestHandler decide which to use
 *      - concern with this is 1) @Named injection is tedious to deal with, dependent on magic strings, hard to trace usage
 *  - SideOutputForOriginal, SideOutputForSanitized, interfaces; inject those?  almost equivalent/better than Named, just bc no magic strings to hunt for
 */
public interface ApiDataSideOutput {

    /**
     * writes content, retrieved in response to the request, to this side output, if enabled
     *
     * @param request content is in response to
     * @param content to write to side output (maybe modified form of the response)
     */
    void write(HttpEventRequest request, ProcessedContent content) throws Output.WriteFailure;


    //q: do we need an InputStream version of this,

}
