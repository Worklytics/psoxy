package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


/**
 * represents content that has been processed by a proxy instance
 *
 *  (possibly an intermediate step in the pipeline)
 *
 */
@With
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
     * the content encoding of the content, if any
     * eg, gzip, deflate, ...
     */
    String contentEncoding;

    /**
     * charset of the content, if any
     * eg, UTF-8, ISO-8859-1, ...
     */
    @Builder.Default
    Charset contentCharset = StandardCharsets.UTF_8;

    /**
     * metadata about the processing or the content
     */
    @Builder.Default
    Map<String, String> metadata = new HashMap<>();

    /**
     * the processed content itself
     */
    byte[] content;

    public String getContentString() {
        return new String(content, contentCharset);
    }
}
