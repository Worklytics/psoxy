package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;

import javax.inject.Inject;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.*;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.hc.core5.http.HttpHeaders;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

@Log
public class GcpWebhookCollectionHandler {

    InboundWebhookHandler inboundWebhookHandler;
    GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory;
    GcpEnvironment gcpEnvironment;
    BatchMergeHandler batchMergeHandler;
    ConfigService configService;

    // TODO: arg should be configurable via env vars; could expose generally, bc although not used in AWS, may be useful logging (eg, how often is AWS invoked with 'full' batch)
    static final Duration BATCH_TIMEOUT = Duration.ofSeconds(30);
    static final Integer BATCH_SIZE = 100;

    @Inject
    public GcpWebhookCollectionHandler(InboundWebhookHandler inboundWebhookHandler,
                                       GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory,
                                       GcpEnvironment gcpEnvironment,
                                       BatchMergeHandler batchMergeHandler,
                                       ConfigService configService) {
        this.gcpEnvironment = gcpEnvironment;
        this.inboundWebhookHandler = inboundWebhookHandler;
        this.googleIdTokenVerifierFactory = googleIdTokenVerifierFactory;
        this.batchMergeHandler = batchMergeHandler;
        this.configService = configService;
    }

    /**
     * route request based on headers ... arg fits better up in Route class
     * @param request
     * @param response
     */
    public void handle(HttpRequest request, HttpResponse response) {
        if (request.getFirstHeader(HttpHeaders.USER_AGENT).map(s -> s.contains(gcpEnvironment.getCloudSchedulerUserAgent())).orElse(false)) {
            // purporting to be a cloud scheduler request, so assume it's a request to process a batch
            handleBatch(request, response);
        } else {
            // assume it's a direct inbound webhook request
            CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);

            // inboundWebhookHandler *generically* checks 'Authorization' header
            HttpEventResponse genericResponse = inboundWebhookHandler.handle(cloudFunctionRequest);
            try {
                HttpRequestHandler.fillGcpResponseFromGenericResponse(response, genericResponse);
            } catch (IOException e) {
                log.log(Level.WARNING, "IOException while filling GCP response from generic response", e);
                response.setStatusCode(500, "Internal Server Error");
            }
        }
    }

    void handleBatch(HttpRequest request, HttpResponse response) {
        // Authorization header should either come from inbound webhook OR via pubsub.
        // eg
        //gcloud pubsub subscriptions create my-sub \
        //  --topic=my-topic \
        //  --push-endpoint=https://<cloud-run-url>/pubsub \
        //  --push-auth-service-account=your-service-account@project.iam.gserviceaccount.com \
        //  --push-auth-token-audience=https://<cloud-run-url>
        Optional<String> jwt = request.getFirstHeader("Authorization");

        if (jwt.isPresent()) {
            response.setStatusCode(401);
            response.setStatusCode(401, "Unauthorized : no authorization header included");
            return;
        }

        String endpointUrl = endpointUrl(request);
        GoogleIdTokenVerifier internalServiceVerifier = googleIdTokenVerifierFactory.getVerifierForAudience(endpointUrl);


        GoogleIdToken idToken = null;

        try {
            idToken = internalServiceVerifier.verify(jwt.get());
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.log(Level.WARNING, "Failed to verify JWT: " + jwt.get(), e);
            response.setStatusCode(401, "Unauthorized : invalid JWT");
            return;
        }

        if (idToken == null) {
            response.setStatusCode(401, "Unauthorized - no Authorization jwt sent");
        } else if (idToken.getPayload().getIssuer().equals(gcpEnvironment.getInternalServiceAuthIssuer())) {
            this.handleBatch(request);
            response.setStatusCode(200, "OK - batch processed");
        } else {
            response.setStatusCode(401, "Unauthorized - unacceptable issuer");
        }
    }




    @SneakyThrows
    void handleBatch(HttpRequest request) {
        ProjectSubscriptionName subscriptionName  =
            ProjectSubscriptionName.parse(configService.getConfigPropertyOrError(GcpEnvironment.WebhookCollectorModeConfigProperty.BATCH_MERGE_SUBSCRIPTION));

        SubscriberStubSettings settings = SubscriberStubSettings.newBuilder().build();

        try (
            SubscriberStub subscriber = settings.createStub()) {
            PullRequest pullRequest = PullRequest.newBuilder()
                .setMaxMessages(100)
                .setSubscription(subscriptionName.toString())
                .build();

            PullResponse response = subscriber.pullCallable().call(pullRequest);

            Stream<ProcessedContent> processedContentStream =
            response.getReceivedMessagesList().stream().map(m -> ProcessedContent.builder()
                .contentType(m.getMessage().getAttributesMap().getOrDefault("Content-Type", "application/json"))
                .contentEncoding(m.getMessage().getAttributesMap().getOrDefault("Content-Encoding", "gzip"))
                .content(m.getMessage().getData().toByteArray())
                .build());

            batchMergeHandler.handleBatch(processedContentStream);

            List<String> ackIds = response.getReceivedMessagesList().stream()
                .map(ReceivedMessage::getAckId).toList();

            if (!ackIds.isEmpty()) {
                AcknowledgeRequest ackRequest = AcknowledgeRequest.newBuilder()
                    .setSubscription(subscriptionName.toString())
                    .addAllAckIds(ackIds)
                    .build();
                subscriber.acknowledgeCallable().call(ackRequest);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //TODO: if found 100, loop again??
    }

    private String endpointUrl(HttpRequest httpRequest) {
          //going to ASSUME that the request is HTTPS
         return "https://" +
            httpRequest.getFirstHeader("Host").orElseThrow(() -> new IllegalStateException("No Host header found")) +
            httpRequest.getPath();
    }

}


