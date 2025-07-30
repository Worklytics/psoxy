package co.worklytics.psoxy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;
import javax.annotation.Nullable;
import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface RESTApiSanitizer {
    /**
     * @param httpMethod to test
     * @param url to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     *
     *         q: some scope question about whether this is beyond Sanitizer responsibility or not
     *         args for:
     *         - it's an interpretation of rules
     *         - correct if 'santizer' is sanitizing an "API" rather than "API response content"
     *         - sanitization can't really be decoupled from the semantics of the endpoints in
     *         question
     *         args against:
     *         - could split this into two classes, with one that deals with endpoint level stuff,
     *         such
     *         as 1) what endpoints allowed, and 2) what pseudonymizations and redactions to apply
     *         to response given the endpoint; and the other(s) which just do the
     *         pseudonymization/redaction (eg, pseudonymize(jsonPAths, content); redact(jsonPaths,
     *         content))
     *         - just invariably that's quite coupled, per above
     *
     *
     * TODO: migrate to isAllowed(String httpMethod, URL url, String contentType, String requestBody)
     */
    @Deprecated // use isAllowed(String httpMethod, URL url, String contentType, String requestBody) instead, as more general; this version assumes GET/HEAD request method
    boolean isAllowed(String httpMethod, URL url);

    /**
     * @param httpMethod to test
     * @param url to test
     * @param contentType to test
     * @param requestBody to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     */
    boolean isAllowed(String httpMethod, URL url, String contentType, String requestBody);

    /**
     * Headers to include in the request
     *
     * @param httpMethod The method to test
     * @param url The url to test
     * @return
     */
    Optional<Collection<String>> getAllowedHeadersToForward(String httpMethod, URL url);

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(String httpMethod, URL url, String jsonResponse);


    Pseudonymizer getPseudonymizer();

    RESTRules getRules();

    /**
     * sanitize response stream received from url, according any options set on Sanitizer
     * <p>
     * bc of streaming interface, this is preferred when expect large responses
     * <p>
     * q: compression; do we return gzipped stream out of here, or have consumer choose that??
     *
     * for the InputStream on the response it seems.
     *
     * @param httpMethod
     * @param url
     * @param response
     * @return
     * @throws IOException
     */
    RESTApiSanitizerImpl.ProcessedStream sanitize(String httpMethod, URL url, InputStream response)
            throws IOException;

    /***
     * q: why this instead of returning a Future<InputStream>?
     * a: hard to reason about, but you actually have to consume the whole input stream before forcing the Future to complete; if you just
     * expose Future<InputStream>, you don't have a separate handle to InputStream to readAllBytes()
     * calling Future::get just deadlocks waiting for Future<> to complete, but future doesn't complete until InputStream fully read
     * h
     *
     * */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    class ProcessedStream implements AutoCloseable {

        @Getter
        private final InputStream stream;
        private final Future<?> future;
        @Nullable
        private final ExecutorService executor;

        public void complete() throws ExecutionException, InterruptedException {
            future.get();
            if (executor != null) {
                executor.shutdown();
            }
        }

        /**
         * a stream that's already completed
         * @param stream
         */
        public static ProcessedStream completed(InputStream stream) {
            return new ProcessedStream(stream,  CompletableFuture.completedFuture(null), null);
        }

        public static ProcessedStream createRunning(InputStream stream, Runnable runnable ) {
            // possibly would be better to let callers pass in their own ExecutorService?
            // and/or maybe should be something we use DI for to inject based on context?

            // atm, we just use a single-thread executor created here bc limits scope of ExecutrorService to this single clas
            ExecutorService executor = Executors.newSingleThreadExecutor();

            Future<?> future = executor.submit(runnable);
            return new ProcessedStream(stream, future, executor);
        }

        @Override
        public void close() throws Exception {
            future.get();
            stream.close();
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }
}
