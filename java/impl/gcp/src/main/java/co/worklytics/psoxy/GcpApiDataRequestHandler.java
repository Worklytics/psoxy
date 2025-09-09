package co.worklytics.psoxy;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import javax.inject.Inject;
import org.apache.commons.lang3.RandomUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventRequestDto;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Lazy;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;


/**
 * NOTE: as of 0.5, Route is the actual java entrypoint for GCP Cloud Functions, referred to in configurations.
 *
 */
@Log
//@RequiredArgsConstructor(onConstructor_ = {@Inject})  //why doesn't this work
public class GcpApiDataRequestHandler {

    final GcpEnvironment gcpEnvironment;
    final Lazy<GcpEnvironment.ApiModeConfig> apiModeConfig; // lazy to avoid circular dependency issues
    final ApiDataRequestHandler requestHandler;
    final EnvVarsConfigService envVarsConfigService;
    final GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory;
    final ObjectMapper objectMapper;

    // standard Bearer token prefix on Authorization header
    static final String BEARER_PREFIX = "Bearer ";

    final Integer MAX_ASYNC_ATTEMPTS = 3;

    @Inject
    public GcpApiDataRequestHandler(
        GcpEnvironment gcpEnvironment,
        Lazy<GcpEnvironment.ApiModeConfig> apiModeConfig,
        ApiDataRequestHandler requestHandler,
        EnvVarsConfigService envVarsConfigService,
        GoogleIdTokenVerifierFactory googleIdTokenVerifierFactory,
        ObjectMapper objectMapper) {
        this.requestHandler = requestHandler;
        this.gcpEnvironment = gcpEnvironment;
        this.apiModeConfig = apiModeConfig;
        this.envVarsConfigService = envVarsConfigService;
        this.googleIdTokenVerifierFactory = googleIdTokenVerifierFactory;
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    public void service(HttpRequest request, HttpResponse response) {

        boolean pubsubTriggerConfigured = apiModeConfig.get().getAsyncPubSubQueue().isPresent();

        // if a pubsub trigger is configured, check if request is invocation via PubSub
        // as of 2025-08-19, User-Agent appears as 'APIs-Google; (+https://developers.google.com/webmasters/APIs-Google.html)'
        Boolean isPubSubInvocation =
            pubsubTriggerConfigured &&
            request.getFirstHeader(HttpHeaders.USER_AGENT)
                .map(userAgent ->
                    userAgent.contains(gcpEnvironment.getGoogleApisUserAgent()) || // in practice, this is how invocation for Cloud Run v2 via PubSub is coming
                    userAgent.contains(gcpEnvironment.getPubSubUserAgent()))
                .orElse(false);

        if (isPubSubInvocation) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "PubSub push invocation detected");
            }

            // potentially async case - PubSub push invocation
            if (request.getFirstHeader(GcpEnvironment.PUBSUB_DELIVERY_ATTEMPT_HEADER).map(Integer::parseInt).orElse(-1) > MAX_ASYNC_ATTEMPTS) {
                log.log(Level.SEVERE, "Max PubSub delivery attempts exceeded, dropping message");
                response.setStatusCode(org.apache.hc.core5.http.HttpStatus.SC_NO_CONTENT, "Max delivery attempts exceeded");
                return;
            }

            // verify auth to ENSURE is legit PubSub push callback
            verifyAuthentication(request);

            handleAsyncCase(request, response);
        } else {
            handleSyncCase(request, response);
        }
    }

    void handleSyncCase(HttpRequest request, HttpResponse response) {
        // sync case
        CloudFunctionRequest cloudFunctionRequest = CloudFunctionRequest.of(request);

        try {
            if (envVarsConfigService.isDevelopment()) {
                cloudFunctionRequest.getWarnings().forEach(log::warning);
            }
        } catch (Throwable e) {
            //suppress anything that went wrong here
            log.log(Level.WARNING, "Throwable while computing warnings that is suppressed", e);
        }


        try {
            HttpEventResponse abstractResponse =
                requestHandler.handle(cloudFunctionRequest, ApiDataRequestHandler.ProcessingContext.builder()
                    .async(false)
                    .requestId(UUID.randomUUID().toString())
                    .requestReceivedAt(Instant.now())
                    .build());

            fillGcpResponseFromGenericResponse(response, abstractResponse);

            // sample 1% of requests, warning if compression not requested
            if (RandomUtils.nextInt(0, 99) == 0 && !cloudFunctionRequest.getWarnings().isEmpty()) {
                response.appendHeader(ProcessedDataMetadataFields.WARNING.getHttpHeader(),
                    Warning.COMPRESSION_NOT_REQUESTED.asHttpHeaderCode());
            }
        } catch (Throwable e) {
            // unhandled exception while handling request
            log.log(Level.SEVERE, "Error while handling request", e);
            try {
                response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                response.appendHeader(ProcessedDataMetadataFields.ERROR.getHttpHeader(), ErrorCauses.UNKNOWN.name());
                response.getWriter().write("Unknown internal proxy error; review logs");
            } catch (IOException ioException) {
                log.log(Level.SEVERE, "Error writing error response", ioException);
            }
        }
    }


    void verifyAuthentication(HttpRequest request) {
        Optional<String> jwt = request.getFirstHeader(HttpHeaders.AUTHORIZATION)
            .map(s -> s.replace(BEARER_PREFIX, ""));

        if (envVarsConfigService.isDevelopment()) {
            log.log(Level.INFO, "Authorization header: " + jwt.get());
        }

        if (!jwt.isPresent()) {
            log.log(Level.WARNING, "Unauthorized : no authorization header included");
            throw new GcpWebhookCollectionHandler.AuthorizationException("Unauthorized : no authorization header included");
        }
        GoogleIdTokenVerifier internalServiceVerifier =
            apiModeConfig.get().getServiceUrl()
                .map(googleIdTokenVerifierFactory::getVerifierForAudience)
                .orElseThrow(() -> new IllegalStateException("Service URL not configured"));

        GoogleIdToken idToken = null;
        try {
            idToken = GoogleIdToken.parse(internalServiceVerifier.getJsonFactory(), jwt.get());
            if (idToken == null) {
                throw new GcpWebhookCollectionHandler.AuthorizationException("Unauthorized - failed to parse JWT");
            }

            if (!idToken.getPayload().getIssuer().equals(gcpEnvironment.getInternalServiceAuthIssuer())) {
                log.log(Level.WARNING, "Unauthorized - unacceptable issuer: " + idToken.getPayload().getIssuer());
                throw new GcpWebhookCollectionHandler.AuthorizationException("Unauthorized - unacceptable issuer");
            }

            internalServiceVerifier.verifyOrThrow(idToken);
            return; // success
        } catch (IOException | IllegalArgumentException e) {
            log.log(Level.WARNING, "Failed to verify JWT: " + jwt.get(), e);
            throw new GcpWebhookCollectionHandler.AuthorizationException("Unauthorized : invalid JWT", e);
        }
    }

        /**
     * async handle an authenticated PubSub push invocation
     * @param request an authenticated PubSub push invocation request
     * @param response to fill with the response from the proxy
     * @throws IOException
     */
    void handleAsyncCase(HttpRequest request, HttpResponse response) throws IOException {

        try (InputStream inputStream = new ByteArrayInputStream(request.getInputStream().readAllBytes())) {
            PubSubPushBody pubSubPushBody = objectMapper.readerFor(PubSubPushBody.class)
                .readValue(inputStream);

            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "PubSub push body: " + pubSubPushBody);
            }

            HttpEventRequest originalRequest = objectMapper.readerFor(HttpEventRequestDto.class)
                .readValue(Base64.getDecoder().decode(pubSubPushBody.getMessage().getData().getBytes(StandardCharsets.UTF_8)));

            ApiDataRequestHandler.ProcessingContext processingContext = objectMapper.readerFor(ApiDataRequestHandler.ProcessingContext.class)
                .readValue(pubSubPushBody.getMessage().getAttributes().get(ApiDataRequestViaPubSub.MessageAttributes.PROCESSING_CONTEXT.getStringEncoding()));

            processingContext = processingContext.toBuilder()
                .async(true)
                .build();

            HttpEventResponse genericResponse = requestHandler.handle(originalRequest, processingContext);

            // NOTE: in async case, genericResponse.getBody() will be `null`
            fillGcpResponseFromGenericResponse(response, genericResponse);
        }
    }


    static void fillGcpResponseFromGenericResponse(HttpResponse response, HttpEventResponse genericResponse) throws IOException {
        response.setStatusCode(genericResponse.getStatusCode());

        genericResponse.getHeaders().forEach(response::appendHeader);
        genericResponse.getMultivaluedHeaders().forEach((key, valueList) -> valueList.forEach(value -> response.appendHeader(key, value)));

        if (genericResponse.getBody() != null) {
            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(genericResponse.getBody());
            } catch (IOException e) {
                log.log(Level.WARNING, "Error writing response body", e);
            }
        }
    }

    @Data
    public static class PubSubPushBody {

        String subscription;

        PubSubMessage message;


        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, Object> unmapped = new HashMap<>();

        @JsonAnySetter
        void setAttribute(String key, Object value) {
            unmapped.put(key, value);
        }

        /**
         * NOTE: json for this seems to have BOTH snake_case and camelCase fields for messageId, publishTime, ...
         */
        @Data
        public static class PubSubMessage {
            String messageId;

            String publishTime;

            Map<String, String> attributes;

            String data;


            @JsonInclude(JsonInclude.Include.NON_EMPTY)
            Map<String, Object> unmapped = new HashMap<>();

            @JsonAnySetter
            void setAttribute(String key, Object value) {
                unmapped.put(key, value);
            }
        }
    }
}
