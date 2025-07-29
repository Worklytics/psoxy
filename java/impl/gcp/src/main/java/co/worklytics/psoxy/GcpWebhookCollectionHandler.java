package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import co.worklytics.psoxy.gateway.impl.JwksDecorator;
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
import org.apache.hc.core5.http.HttpStatus;
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
    JwksDecorator jwksResource;

    // TODO: arg should be configurable via env vars; could expose generally, bc although not used in AWS, may be useful logging (eg, how often is AWS invoked with 'full' batch)
    static final Duration BATCH_TIMEOUT = Duration.ofSeconds(30);
    static final Integer BATCH_SIZE = 100;

    @Inject
    public GcpWebhookCollectionHandler(InboundWebhookHandler inboundWebhookHandler,
                                       GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory,
                                       GcpEnvironment gcpEnvironment,
                                       BatchMergeHandler batchMergeHandler,
                                       JwksDecorator.Factory jwksDecoratorFactory,
                                       ConfigService configService) {
        this.gcpEnvironment = gcpEnvironment;
        this.inboundWebhookHandler = inboundWebhookHandler;
        this.googleIdTokenVerifierFactory = googleIdTokenVerifierFactory;
        this.batchMergeHandler = batchMergeHandler;
        this.jwksResource = jwksDecoratorFactory.create(inboundWebhookHandler);
        this.configService = configService;
    }

    /**
     * route request based on headers ... arg fits better up in Route class. 3 possible routes:
     * 1. inbound webhook request (from 'authorized' client)
     * 2. JWKS request (publicly accessible)
     * 3. batch merge request (from pubsub)
     * @param request
     * @param response
     */
    public void handle(HttpRequest request, HttpResponse response) {

        // see: https://cloud.google.com/scheduler/docs/reference/rpc/google.cloud.scheduler.v1#httptarget
        Boolean userAgentIsCloudSchedulerOrPubSub = request.getFirstHeader(HttpHeaders.USER_AGENT)
            .map(s -> s.contains(gcpEnvironment.getCloudSchedulerUserAgent()))
            .orElse(false);
        Boolean cloudSchedulerHeaderTrue = request.getFirstHeader("x-cloudscheduler")
        .map(Boolean::parseBoolean)
        .orElse(false);


        if (userAgentIsCloudSchedulerOrPubSub || cloudSchedulerHeaderTrue) {
            // purporting to be a cloud scheduler request, so assume it's a request to process a batch
            // request SHOULD Contain Authorization header, which handleBatch should check
            handleBatch(request, response);
        } else {
            CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);
            HttpEventResponse genericResponse;
            if (request.getPath().startsWith("/" + JwksDecorator.PATH_TO_RESOURCE)) {
                // JWKS resources are *publically* accessible
                genericResponse = jwksResource.handle(cloudFunctionRequest);
            } else {
                // inboundWebhookHandler *generically* checks 'Authorization' header
                // assume it's a direct inbound webhook request
                genericResponse = inboundWebhookHandler.handle(cloudFunctionRequest);
            }
            
            try {
                HttpRequestHandler.fillGcpResponseFromGenericResponse(response, genericResponse);
            } catch (IOException e) {
                log.log(Level.WARNING, "IOException while filling GCP response from generic response", e);
                response.setStatusCode(500, "Internal Server Error");
            }
        }
    }

    void handleBatch(HttpRequest request, HttpResponse response) {
   
        Optional<String> jwt = request.getFirstHeader("Authorization");

        if (!jwt.isPresent()) {
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED, "Unauthorized : no authorization header included");
            return;
        }

        String endpointUrl = endpointUrl(request);
        GoogleIdTokenVerifier internalServiceVerifier = googleIdTokenVerifierFactory.getVerifierForAudience(endpointUrl);


        GoogleIdToken idToken = null;

        try {
            idToken = internalServiceVerifier.verify(jwt.get());
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.log(Level.WARNING, "Failed to verify JWT: " + jwt.get(), e);
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED, "Unauthorized : invalid JWT");
            return;
        }

        if (idToken == null) {
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED, "Unauthorized - no Authorization jwt sent");
        } else if (idToken.getPayload().getIssuer().equals(gcpEnvironment.getInternalServiceAuthIssuer())) {
            this.processBatch();
            response.setStatusCode(HttpStatus.SC_OK, "OK - batch processed");
        } else {
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED, "Unauthorized - unacceptable issuer");
        }
    }




    // TODO: this is a bit of a hack, as it's not clear how to test this.
    /**
     * processes a batch of webhooks from Pub/Sub topic, via subscription.
     */
    @SneakyThrows
    void processBatch() {
        ProjectSubscriptionName subscriptionName  =
            ProjectSubscriptionName.parse(configService.getConfigPropertyOrError(GcpEnvironment.WebhookCollectorModeConfigProperty.BATCH_MERGE_SUBSCRIPTION));

        SubscriberStubSettings settings = SubscriberStubSettings.newBuilder().build();

        // TODO: start a stopwatch, process for up to a BATCH_TIMEOUT, and then stop.
        try (SubscriberStub subscriber = settings.createStub()) {

            PullRequest pullRequest = PullRequest.newBuilder()
                .setMaxMessages(BATCH_SIZE)
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
            if (ackIds.size() == BATCH_SIZE) {
                log.log(Level.WARNING, "Processed a full batch; if happens repeatedly, consider increasing BATCH_SIZE or running multiple batches per cron invocation");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String endpointUrl(HttpRequest httpRequest) {
          //going to ASSUME that the request is HTTPS
         return "https://" +
            httpRequest.getFirstHeader("Host").orElseThrow(() -> new IllegalStateException("No Host header found")) +
            httpRequest.getPath();
    }

}


