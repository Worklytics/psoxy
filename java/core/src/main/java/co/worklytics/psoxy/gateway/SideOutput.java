package co.worklytics.psoxy.gateway;

import java.io.IOException;

/**
 * a side output for the proxy
 *
 * q: InputStream, byte[], or String for the interface??
 *  - left as String for now; byte[] arguably more flexible. InputStream could avoid copies in *some* potential scnearios?
 *     (eg, if just writing original to side output a single time)
 *
 * q: two methods for original and sanitized the right approach?
 *      - drawback is that it leaves config reading to the implementation
 *
 *  alternatives:
 *  -  @Named("original") SideOutput sideOutputOriginal; @Named("sanitized") SideOutput sideOutputSanitized; let CommonRequestHandler decide which to use
 *      - concern with this is 1) @Named injection is tedious to deal with, dependent on magic strings, hard to trace usage
 *  - SideOutputForOriginal, SideOutputForSanitized, interfaces; inject those?  almost equivalent/better than Named, just bc no magic strings to hunt for
 */
public interface SideOutput {

    /**
     * writes content, retrieved in response to the request, to this side output, if enabled
     *
     * @param request content is in response to
     * @param content to write to side output (maybe modified form of the response)
     */
    void write(HttpEventRequest request, ProcessedContent content) throws IOException;


    //q: do we need an InputStream version of this,

}
