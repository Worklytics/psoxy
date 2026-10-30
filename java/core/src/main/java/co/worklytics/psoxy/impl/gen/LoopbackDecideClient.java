package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * {@code POST http://127.0.0.1:&lt;port&gt;/v1/systemone} only. Any other host fails closed.
 *
 * <p>Thread-safety: one shared {@link HttpClient} and a fair semaphore of
 * {@link #MAX_IN_FLIGHT} permits. The semaphore belongs to this client. It is not the chat
 * backend's permit pool. Concurrent classify calls block on the semaphore until
 * {@code CLASSIFY_TIMEOUT_SECONDS} and then return no body so the caller can retry.
 */
@Log
@Singleton
public class LoopbackDecideClient {

    public static final URI ENDPOINT = URI.create("http://127.0.0.1:8000/v1/systemone");

    /** Max in-flight embedded decisions. Independent of the chat backend semaphore. */
    static final int MAX_IN_FLIGHT = 4;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    /**
     * @return response body, or {@code null} on timeout or a non-200 status
     * @throws IOException when the connection fails before a status line
     */
    @FunctionalInterface
    interface Poster {
        String post(URI uri, String json, Duration timeout) throws IOException;
    }

    private final Poster poster;
    private final Semaphore inFlight = new Semaphore(MAX_IN_FLIGHT, true);

    @Inject
    public LoopbackDecideClient() {
        this(LoopbackDecideClient::postHttp);
    }

    LoopbackDecideClient(Poster poster) {
        this.poster = poster;
    }

    /**
     * Fail closed unless {@code uri} is {@code http://127.0.0.1:&lt;port&gt;/v1/systemone}.
     */
    public static void requireLoopback(URI uri) {
        if (uri == null
            || !"http".equalsIgnoreCase(uri.getScheme())
            || uri.getUserInfo() != null
            || !"127.0.0.1".equals(uri.getHost())
            || uri.getPort() < 0
            || !"/v1/systemone".equals(uri.getPath())
            || uri.getQuery() != null
            || uri.getFragment() != null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "embedded-decide accepts only http://127.0.0.1:<port>/v1/systemone");
        }
    }

    String post(String json, Duration timeout) {
        return post(ENDPOINT, json, timeout);
    }

    String post(URI uri, String json, Duration timeout) {
        requireLoopback(uri);
        boolean acquired = false;
        try {
            if (timeout == null || timeout.isZero() || timeout.isNegative()
                || !inFlight.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                log.warning("embedded-decide timed out waiting for an inference slot");
                return null;
            }
            acquired = true;
            return poster.post(uri, json, timeout);
        } catch (HttpTimeoutException e) {
            log.warning("embedded-decide timed out");
            return null;
        } catch (ConnectException e) {
            throw down(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "embedded-decide interrupted", e);
        } catch (IOException e) {
            if (isDown(e)) {
                throw down(e);
            }
            log.log(Level.WARNING, "embedded-decide call failed", e);
            return null;
        } finally {
            if (acquired) {
                inFlight.release();
            }
        }
    }

    private static GenMetadataAugmentException down(IOException cause) {
        return new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
            "embedded-decide loopback is down", cause);
    }

    private static boolean isDown(IOException e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof ConnectException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("connection refused")) {
                return true;
            }
        }
        return false;
    }

    private static String postHttp(URI uri, String json, Duration timeout) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warning("embedded-decide loopback returned HTTP " + response.statusCode());
                return null;
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("embedded-decide interrupted", e);
        }
    }
}
