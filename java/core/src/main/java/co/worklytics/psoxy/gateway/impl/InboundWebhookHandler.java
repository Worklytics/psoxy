package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import dagger.Lazy;
import lombok.SneakyThrows;
import org.apache.http.HttpStatus;

import javax.inject.Inject;
import java.util.Optional;

/**
 * general handler for inbound webhooks (Webhook Collector mode of proxy)
 * to be wrapped by host-platform specific handlers (e.g. GCP, AWS, etc.), which adapt the
 * incoming webhook requests to the HttpEventRequest interface,
 * and resulting HttpEventResponse to the host platform's response format.
 *
 */
public class InboundWebhookHandler {

    private final Output output;
    private final WebhookSanitizer webhookSanitizer;

    @Inject
    public InboundWebhookHandler(Lazy<WebhookSanitizer> webhookSanitizerProvider,
                                 Output output) {
        this.webhookSanitizer = webhookSanitizerProvider.get(); // avoids trying to instantiate WebhookSanitizerImpl when we don't need one
        this.output = output;
    }

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {


        Optional<String> authHeader = request.getHeader("Authorization");
        if (authHeader.isEmpty()) {
            // validate that configuration doesn't require verification of Authorization headers
        }

        // TODO: verify signature of Authorization header
        // if fails, return 401 Unauthorized


        // TODO: if Authorization header is present, evaluate claims
        if (!webhookSanitizer.verifyClaims(request, null)) {
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_UNAUTHORIZED)
                .build();
        }

        if (!webhookSanitizer.canAccept(request)) {
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .build();
        }

        ProcessedContent sanitized = webhookSanitizer.sanitize(request);

        output.write(sanitized);

        return HttpEventResponse.builder()
            .statusCode(HttpStatus.SC_OK)
            .build();
    }
}
