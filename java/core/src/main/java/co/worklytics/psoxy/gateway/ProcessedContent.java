package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Value;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;


/**
 * represents content that has been processed by a proxy instance
 *
 *  (possibly an intermediate step in the pipeline)
 *
 */
@Builder
@Value
public class ProcessedContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * the content type of the content, if any
     * eg, application/json, text/html, image/png, ...
     */
    String contentType;

    /**
     * charset of the content, if any
     * eg, UTF-8, ISO-8859-1, ...
     */
    Charset contentCharset;

    /**
     * metadata about the processing or the content
     */
    Map<String, String> metadata;

    /**
     * the processed content itself
     */
    String content;
}
