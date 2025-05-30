package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
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

    private final WebhookSanitizer webhookSanitizer;

    @Inject
    public InboundWebhookHandler(WebhookSanitizer webhookSanitizer) {
        this.webhookSanitizer = webhookSanitizer;
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



        String sanitizedBody = webhookSanitizer.sanitize(request);

        // TODO: write sanitizedBody to output
        // inject an Output implementation, by the platform.
        // for this use-case, we potentially want to stream lines into the output; how do we do so?


        return HttpEventResponse.builder()
            .statusCode(HttpStatus.SC_OK)
            .build();
    }
}
