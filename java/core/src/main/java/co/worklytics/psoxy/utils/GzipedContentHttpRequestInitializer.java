package co.worklytics.psoxy.utils;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Optional;

/**
 * adds headers to request compressed response
 *
 */
@NoArgsConstructor
@AllArgsConstructor
public class GzipedContentHttpRequestInitializer implements HttpRequestInitializer {

    @Nullable
    String defaultUserAgent;

    @Override
    public void initialize(HttpRequest request) throws IOException {
        request.getHeaders().setAcceptEncoding("gzip");

        String userAgent = Optional.ofNullable(request.getHeaders().getUserAgent())
            .orElse(defaultUserAgent);

        request.getHeaders().setUserAgent(userAgent + " (gzip)");
    }
}
