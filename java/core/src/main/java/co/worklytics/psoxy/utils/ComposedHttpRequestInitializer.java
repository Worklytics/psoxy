package co.worklytics.psoxy.utils;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * util class to compose multiple instances of HttpRequestInitializer into one
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ComposedHttpRequestInitializer implements HttpRequestInitializer {

    List<HttpRequestInitializer> chain = new ArrayList<>();

    public static ComposedHttpRequestInitializer of(HttpRequestInitializer one, HttpRequestInitializer other) {
        ComposedHttpRequestInitializer composite = new ComposedHttpRequestInitializer();
        composite.add(one);
        composite.add(other);
        return composite;
    }

    public static ComposedHttpRequestInitializer of(HttpRequestInitializer initializer) {
        ComposedHttpRequestInitializer composite = new ComposedHttpRequestInitializer();
        composite.add(initializer);
        return composite;
    }

    public ComposedHttpRequestInitializer add(HttpRequestInitializer initializer) {
        chain.add(initializer);
        return this;
    }

    @Override
    public void initialize(HttpRequest request) throws IOException {
        for (HttpRequestInitializer initializer : chain) {
            initializer.initialize(request);
        }
    }
}
