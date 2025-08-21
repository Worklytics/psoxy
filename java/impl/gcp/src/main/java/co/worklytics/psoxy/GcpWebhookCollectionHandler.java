package co.worklytics.psoxy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfigProperty;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.InboundWebhookHandler;
import co.worklytics.psoxy.gateway.impl.JwksDecorator;
import dagger.Lazy;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
public class GcpWebhookCollectionHandler {

    InboundWebhookHandler inboundWebhookHandler;
    GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory;
    GcpEnvironment gcpEnvironment;
    BatchMergeHandler batchMergeHandler;
    ConfigService configService;
    JwksDecorator jwksResource;
    EnvVarsConfigService envVarsConfigService;
    Lazy<GcpEnvironment.WebhookCollectorModeConfig> webhookCollectorModeConfig;

    // standard Bearer token prefix on Authorization header
    static final String BEARER_PREFIX = "Bearer ";

    @Inject
    public GcpWebhookCollectionHandler(InboundWebhookHandler inboundWebhookHandler,
                                       GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory,
                                       GcpEnvironment gcpEnvironment,
                                       BatchMergeHandler batchMergeHandler,
                                       JwksDecorator.Factory jwksDecoratorFactory,
                                       ConfigService configService,
                                       EnvVarsConfigService envVarsConfigService,
                                       Lazy<GcpEnvironment.WebhookCollectorModeConfig> webhookCollectorModeConfig) {
        this.gcpEnvironment = gcpEnvironment;
        this.inboundWebhookHandler = inboundWebhookHandler;
        this.googleIdTokenVerifierFactory = googleIdTokenVerifierFactory;
        this.batchMergeHandler = batchMergeHandler;
        this.jwksResource = jwksDecoratorFactory.create(inboundWebhookHandler);
        this.configService = configService;
        this.envVarsConfigService = envVarsConfigService;
        this.webhookCollectorModeConfig = webhookCollectorModeConfig;
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
        Boolean userAgentIsCloudScheduler = request.getFirstHeader(HttpHeaders.USER_AGENT)
            .map(s -> s.contains(gcpEnvironment.getCloudSchedulerUserAgent()))
            .orElse(false);
        Boolean cloudSchedulerHeaderTrue = request.getFirstHeader("x-cloudscheduler")
            .map(Boolean::parseBoolean)
            .orElse(false);


        if (userAgentIsCloudScheduler || cloudSchedulerHeaderTrue) {
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
                GcpApiDataRequestHandler.fillGcpResponseFromGenericResponse(response, genericResponse);
            } catch (IOException e) {
                log.log(Level.WARNING, "IOException while filling GCP response from generic response", e);
                response.setStatusCode(500, "Internal Server Error");
            }
        }
    }

    void handleBatch(HttpRequest request, HttpResponse response) {
        try {
            verifyAuthorization(request);
            this.processBatch();
            response.setStatusCode(HttpStatus.SC_OK, "batch processed");
        } catch (AuthorizationException e) {
            log.log(Level.WARNING, "Unauthorized : " + e.getMessage());
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception while processing batch", e);
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
    }

    /**
     * authorization verification extracted to facilitate testing
     * @throws AuthorizationException if authorization fails; potentially wrapping IOException or IllegalArgumentException if getting public key fails, etc.
     */
    void verifyAuthorization(HttpRequest request) throws AuthorizationException {
        Optional<String> jwt = request.getFirstHeader(HttpHeaders.AUTHORIZATION)
            .map(s -> s.replace(BEARER_PREFIX, ""));

        if (envVarsConfigService.isDevelopment()) {
            log.log(Level.INFO, "Authorization header: " + jwt.get());
        }

        if (!jwt.isPresent()) {
            log.log(Level.WARNING, "Unauthorized : no authorization header included");
            throw new AuthorizationException("Unauthorized : no authorization header included");
        }

        // TODO: hack; exploits that audience happens to be the same as the issuer in the inbound webhook context
        // would perhaps be *better* to hack this in the reverse; put the endpoint URL into config, and then
        // use that as Issuer in the inbound webhook context, Audience in the internal service context

        //TODO: fill
        String endpointUrl = configService.getConfigPropertyOrError(WebhookCollectorModeConfigProperty.AUTH_ISSUER);

        GoogleIdTokenVerifier internalServiceVerifier = googleIdTokenVerifierFactory.getVerifierForAudience(endpointUrl);

        GoogleIdToken idToken = null;
        try {
            idToken = GoogleIdToken.parse(internalServiceVerifier.getJsonFactory(), jwt.get());
            if (idToken == null) {
                throw new AuthorizationException("Unauthorized - failed to parse JWT");
            }

            if (!idToken.getPayload().getIssuer().equals(gcpEnvironment.getInternalServiceAuthIssuer())) {
                log.log(Level.WARNING, "Unauthorized - unacceptable issuer: " + idToken.getPayload().getIssuer());
                throw new AuthorizationException("Unauthorized - unacceptable issuer");
            }

            internalServiceVerifier.verifyOrThrow(idToken);
            return; // success
        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.WARNING, "Failed to verify JWT: " + jwt.get(), e);
            throw new AuthorizationException("Unauthorized : invalid JWT", e);
        }
    }


    ProcessedContent mapMessageToProcessedContent(ReceivedMessage message) {
        ProcessedContent.ProcessedContentBuilder builder = ProcessedContent.builder()
            .contentType(PubSubOutput.MessageAttributes.CONTENT_TYPE.getValue(message).orElse( ContentType.APPLICATION_JSON.getMimeType()))
            .content(message.getMessage().getData().toByteArray());

        PubSubOutput.MessageAttributes.CONTENT_ENCODING.getValue(message)
            .ifPresent(builder::contentEncoding);

        return builder.build();
    }

    /**
     * processes a batch of webhooks from Pub/Sub topic, via subscription.
     */
    @SneakyThrows
    void processBatch() {
        ProjectSubscriptionName subscriptionName  =
            ProjectSubscriptionName.parse(webhookCollectorModeConfig.get().getBatchMergeSubscription());

        SubscriberStubSettings settings = SubscriberStubSettings.newBuilder().build();

        // stop watch to track how long we've been processing batch(s)
        boolean possibleAdditionalMessagesWaiting = false;
        StopWatch stopWatch = StopWatch.createStarted();
        try (SubscriberStub subscriber = settings.createStub()) {
            do {
                PullRequest pullRequest = PullRequest.newBuilder()
                    .setMaxMessages(webhookCollectorModeConfig.get().getBatchSize())
                    .setSubscription(subscriptionName.toString())
                    .build();

                PullResponse response = subscriber.pullCallable().call(pullRequest);

                if (response.getReceivedMessagesCount() == 0) {
                    log.log(Level.INFO, "No messages to process");
                    return;
                }

                Stream<ProcessedContent> processedContentStream = response.getReceivedMessagesList().stream()
                    .map(this::mapMessageToProcessedContent);

                batchMergeHandler.handleBatch(processedContentStream);

                List<String> ackIds = response.getReceivedMessagesList().stream()
                    .map(ReceivedMessage::getAckId).toList();

                AcknowledgeRequest ackRequest = AcknowledgeRequest.newBuilder()
                    .setSubscription(subscriptionName.toString())
                    .addAllAckIds(ackIds)
                    .build();
                subscriber.acknowledgeCallable().call(ackRequest);
                log.log(Level.INFO, "Processed " + ackIds.size() + " messages");

                if (ackIds.size() == webhookCollectorModeConfig.get().getBatchSize()) {
                    possibleAdditionalMessagesWaiting = true;
                    log.log(Level.INFO, "Processed a full batch; if timeout NOT reached, will attempt to process another batch");
                } else {
                    possibleAdditionalMessagesWaiting = false;
                }
            } while (possibleAdditionalMessagesWaiting
                && stopWatch.getTime(TimeUnit.SECONDS) < webhookCollectorModeConfig.get().getBatchInvocationTimeoutSeconds());

            if (possibleAdditionalMessagesWaiting) {
                log.log(Level.WARNING, "Batch processed stopped due to timeout; consider increasing BATCH_SIZE, cron frequency and concurrency, or batch timeout if this happens repeatedly");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }


    /**
     * exception thrown when authorization fails, to force handling outside verifyAuthorization()
     */
    static class AuthorizationException extends RuntimeException {
        public AuthorizationException(String message) {
            super(message);
        }
        public AuthorizationException(String message, Exception e) {
            super(message, e);
        }
    }

    private String endpointUrl(HttpRequest httpRequest) {
          //going to ASSUME that the request is HTTPS
         return "https://" +
            httpRequest.getFirstHeader("Host").orElseThrow(() -> new IllegalStateException("No Host header found")) +
            httpRequest.getPath();
    }

}


