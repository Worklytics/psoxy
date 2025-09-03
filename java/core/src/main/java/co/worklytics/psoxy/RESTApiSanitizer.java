package co.worklytics.psoxy;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.*;
import javax.annotation.Nullable;
import co.worklytics.psoxy.impl.RESTApiSanitizerImpl;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

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
     * q: compression; do we return gzipped stream out of here, or have consumer choose that??
     * <p>
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

    /**
     * a processed stream
     *
     * q: why this instead of returning a Future<InputStream>?
     * a: hard to reason about, but you actually have to consume the whole input stream before forcing the Future to complete; if you just
     * expose Future<InputStream>, you don't have a separate handle to InputStream to readAllBytes()
     * calling Future::get just deadlocks waiting for Future<> to complete, but future doesn't complete until InputStream fully read
     *
     **/
    @RequiredArgsConstructor(access = AccessLevel.PUBLIC, staticName = "create")
    class ProcessedStream {

        private final InputStream stream;
        private final Runnable runnable;

        /**
         * a stream that's already completed
         * @param stream
         */
        public static ProcessedStream completed(InputStream stream) {
            return new ProcessedStream(stream,  null);
        }

        /**
         * read all bytes from stream, subject to a max timeout
         *
         * TODO: pass in the executorService here
         *
         * TODO: this is a transitional method; intention is to read to an outputstream later (entire point is to be able to
         * read/sanitize/write in a streaming manner, so can accommodate very large data loads)
         *
         * eg, an implementation of transferTo(OutputStream)
         *
         * @param timeout
         * @return
         * @throws IOException
         * @throws InterruptedException
         */
        @SneakyThrows  // deals with wrapped exceptions from Future::get
        public byte[] readAllBytes(Duration timeout) throws IOException, InterruptedException{
            // q: even needed? instead of createRunning, do we just start it here??
            if (runnable == null) {
                return stream.readAllBytes();
            } else {
                ExecutorService executorService = Executors.newFixedThreadPool(2);
                Future<?> future = executorService.submit(runnable);
                byte[] result;
                try {
                    Future<byte[]> resultFuture = executorService.submit(stream::readAllBytes);
                    result = resultFuture.get(timeout.getSeconds(), TimeUnit.SECONDS);
                    return result;
                } catch (ExecutionException e) {
                    throw e.getCause();
                } catch (TimeoutException e) {
                    if (future.isDone()) {
                        // attempt to force the execution exception that resulted in the dead-lock that caused the timeout,
                        // and unwrap it
                        // TODO: in java 19+ can use future.exceptionNow() probably
                        try {
                            future.get();
                        } catch (ExecutionException executionException) {
                            throw executionException.getCause();
                        }
                    }
                    throw e;
                } finally {
                    executorService.shutdown();
                }
            }
        }
    }
}
