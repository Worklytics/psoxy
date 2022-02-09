package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.utils.URLUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.base.Preconditions;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class CommonRequestHandler {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    private static final int SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS = 30_000;
    private static final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;
    private static final DateFormat FORMATTER = new SimpleDateFormat("yyyy/MM/dd");

    @Inject ConfigService config;
    @Inject RulesUtils rulesUtils;
    @Inject SourceAuthStrategy sourceAuthStrategy;
    @Inject ObjectMapper objectMapper;
    @Inject SanitizerFactory sanitizerFactory;
    @Inject Rules rules;
    @Inject HealthCheckRequestHandler healthCheckRequestHandler;

    private volatile Sanitizer sanitizer;
    private final Object $writeLock = new Object[0];

    private Sanitizer loadSanitizerRules() {
        if (this.sanitizer == null) {
            synchronized ($writeLock) {
                if (this.sanitizer == null) {
                    this.sanitizer = sanitizerFactory.create(sanitizerFactory.buildOptions(config, rules));
                }
            }
        }
        return this.sanitizer;
    }

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {

        logRequestIfAllowed(request);

        Optional<HttpEventResponse> healthCheckResponse = healthCheckRequestHandler.handleIfHealthCheck(request);
        if (healthCheckResponse.isPresent()) {
            return healthCheckResponse.get();
        }

        HttpRequestFactory requestFactory = getRequestFactory(request);

        // re-write host
        URL targetUrl = buildTarget(request);
        String relativeURL = URLUtils.relativeURL(targetUrl);

        boolean skipSanitizer = skipSanitization(request);

        HttpEventResponse.HttpEventResponseBuilder builder = HttpEventResponse.builder();

        this.sanitizer = loadSanitizerRules();

        if (skipSanitization(request)) {
            log.info(String.format("Proxy invoked with target %s. Skipping sanitization.", relativeURL));
        } else if (sanitizer.isAllowed(targetUrl)) {
            log.info(String.format("Proxy invoked with target %s. Rules allowed call.", relativeURL));
        } else {
            builder.statusCode(HttpStatus.SC_FORBIDDEN);
            log.warning(String.format("Proxy invoked with target %s. Blocked call by rules %s", relativeURL, objectMapper.writeValueAsString(rules.getAllowedEndpointRegexes())));
            return builder.build();
        }

        com.google.api.client.http.HttpRequest sourceApiRequest;
        try {
            sourceApiRequest = requestFactory.buildRequest(request.getHttpMethod(), new GenericUrl(targetUrl), null);
        } catch (IOException e) {
            builder.statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            builder.body("Failed to authorize request; review logs");
            log.log(Level.WARNING, e.getMessage(), e);

            //something like "Error getting access token for service account: 401 Unauthorized POST https://oauth2.googleapis.com/token,"
            log.log(Level.WARNING, "Confirm oauth scopes set in config.yaml match those granted via Google Workspace Admin Console");
            return builder.build();
        }

        //TODO: what headers to forward???

        sourceApiRequest.setHeaders(sourceApiRequest.getHeaders()
            //seems like Google API HTTP client has a default 'Accept' header with 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' ??
            .setAccept(ContentType.APPLICATION_JSON.toString())  //MSFT gives weird "{"error":{"code":"InternalServerError","message":"The MIME type 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' requires a '/' character between type and subtype, such as 'text/plain'."}}
        );

        //setup request
        sourceApiRequest
            .setThrowExceptionOnExecuteError(false)
            .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS)
            .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT);

        //q: add exception handlers for IOExceptions / HTTP error responses, so those retries
        // happen in proxy rather than on Worklytics-side?

        com.google.api.client.http.HttpResponse sourceApiResponse = sourceApiRequest.execute();

        // return response
        builder.statusCode(sourceApiResponse.getStatusCode());

        String responseContent = StringUtils.EMPTY;
        // could be empty in HEAD calls
        if (sourceApiResponse.getContent() != null) {
            responseContent = new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());
        }
        if (sourceApiResponse.getContentType() != null) {
            builder.header(HttpHeaders.CONTENT_TYPE, sourceApiResponse.getContentType());
        }

        String proxyResponseContent;
        if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
            if (skipSanitizer) {
                proxyResponseContent = responseContent;
            }  else {
                proxyResponseContent = sanitizer.sanitize(targetUrl, responseContent);
                String rulesSha = rulesUtils.sha(sanitizer.getOptions().getRules());
                builder.header(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha);
                log.info("response sanitized with rule set " + rulesSha);
            }
        } else {
            //write error, which shouldn't contain PII, directly
            log.log(Level.WARNING, "Source API Error " + responseContent);
            //TODO: could run this through DLP to be extra safe
            proxyResponseContent = responseContent;
        }
        builder.body(proxyResponseContent);

        return builder.build();
    }

    @SneakyThrows
    public StorageEventResponse handle(StorageEventRequest request) {
        CSVParser records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(request.getReaderStream());

        Preconditions.checkArgument(records.getHeaderMap() != null, "Failed to parse header from file");

        Map<Integer, String> invertedHeaderMap = records.getHeaderMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a, TreeMap::new));

        Sanitizer.Options options = sanitizer.getOptions();

        Set<String> columnsToRedact = options.getRules()
                .getRedactions()
                .stream()
                .map(Rules.Rule::getColumns)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        Set<String> columnsToPseudonymize = options.getRules()
                .getPseudonymizations()
                .stream()
                .map(Rules.Rule::getColumns)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        String[] header = invertedHeaderMap.values()
                .stream()
                .filter(entry -> !columnsToRedact.contains(entry)).toArray(String[]::new);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        PrintWriter printWriter = new PrintWriter(baos);
        CSVPrinter printer = new CSVPrinter(printWriter, CSVFormat.DEFAULT.withHeader(header));

        columnsToPseudonymize
                .forEach(columnToPseudonymize ->
                        Preconditions.checkArgument(records.getHeaderMap().containsKey(columnToPseudonymize), "Column %s to be pseudonymized not in file", columnToPseudonymize));

        records.forEach(row -> {
            List<Object> sanitized = invertedHeaderMap.entrySet()
                    .stream()
                    .map(column -> {
                        String value = row.get(column.getKey());
                        if (columnsToRedact.contains(column.getValue())) {
                            return null;
                        } else if (StringUtils.isNotBlank(value) && columnsToPseudonymize.contains(column.getValue())) {
                            try {
                                return objectMapper.writeValueAsString(sanitizer.pseudonymize(value));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            return value;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            try {
                printer.printRecord(sanitized);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to write row", e);
            }
        });

        printWriter.flush();

        return StorageEventResponse.builder()
                .destinationBucketName(request.getDestinationBucket())
                .bytes(baos.toByteArray())
                .destinationPath(String.format("%s/%s", FORMATTER.format(Date.from(Instant.now())), request.getSourceBucketPath()))
                .build();
    }

    @SneakyThrows
    HttpRequestFactory getRequestFactory(HttpEventRequest request) {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        Optional<String> accountToImpersonate =
           request.getHeader(ControlHeader.USER_TO_IMPERSONATE.getHttpHeader());

        accountToImpersonate.ifPresent(user -> log.info("Impersonating user"));
        //TODO: warn here for Google Workspace connectors, which expect user??

        Credentials credentials = sourceAuthStrategy.getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        return transport.createRequestFactory(initializer);
    }

    /**
     * Only allowed under development mode
     * @param request
     * @return
     */
    private boolean skipSanitization(HttpEventRequest request) {
        if (config.isDevelopment()) {
            // caller requested to skip
            return request.getHeader(ControlHeader.SKIP_SANITIZER.getHttpHeader())
                .map(Boolean::parseBoolean)
                .orElse(false);
        } else {
            return false;
        }
    }

    private void logRequestIfAllowed(HttpEventRequest request) {
        logIfDevelopmentMode(() -> String.format("Request:\n%s", request.prettyPrint()));
    }

    boolean isSuccessFamily(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @SneakyThrows
    public URL buildTarget(HttpEventRequest request) {
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https");
        uriBuilder.setHost(config.getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST));
        // URL comes encoded, decode it prior to perform call to API origin to avoid double encoding issues
        uriBuilder.setPath(URLDecoder.decode(request.getPath(), StandardCharsets.UTF_8));
        uriBuilder.setCustomQuery(URLDecoder.decode(request.getQuery().orElse(""), StandardCharsets.UTF_8));
        return uriBuilder.build().toURL();
    }

    private void logIfDevelopmentMode(Supplier<String> messageSupplier) {
        if (config.isDevelopment()) {
            log.info(messageSupplier.get());
        }
    }

}
