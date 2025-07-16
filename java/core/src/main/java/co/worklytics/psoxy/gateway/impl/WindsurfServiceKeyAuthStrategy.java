package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpContent;
import com.google.auth.Credentials;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.hc.core5.http.ContentType;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * kinda dumb, does NOTHING but require config properties.
 *
 * as Windsurf requires adding key into a POST parameter of the request, we DO NOT currently support that.
 */
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class WindsurfServiceKeyAuthStrategy implements SourceAuthStrategy {

    @NonNull ObjectMapper objectMapper;

    @NonNull SecretStore secretStore;

    @Getter
    private final String configIdentifier = "windsurf_service_key";

    /**
     * add service_key to request body
     *
     * @param content an HTTP body as an {@link HttpContent} object (can be null, in which case an empty JSON object is presumed
     * @return a new {@link HttpContent} object with the service_key added to the request body
     */
    @SneakyThrows
    public HttpContent addServiceKeyToRequestBody(@Nullable HttpContent content) {
        if (content == null) {
            // if no content, create empty content with utf-8 encoding
            Map<String, String> serviceKeyOnly = Map.of("service_key", secretStore.getConfigPropertyOrError(ConfigProperty.SERVICE_KEY));
            String jsonString = objectMapper.writeValueAsString(serviceKeyOnly);
            return new ByteArrayContent(ContentType.APPLICATION_JSON.toString(), jsonString.getBytes(StandardCharsets.UTF_8));
        }

        if (!(Objects.equals(ContentType.APPLICATION_JSON, content.getType()))) {
            throw new IllegalArgumentException("WindsurfServiceKeyAuthStrategy only supports application/json content type");
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            content.writeTo(out);
            byte[] bytes = addServiceKeyToRequestBody(out.toByteArray());
            return new com.google.api.client.http.ByteArrayContent(ContentType.APPLICATION_JSON.toString(), bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add service_key to request body", e);
        }
    }

    // implementation of addServiceKeyToRequestBody for byte[] content, avoiding coupling to google http client's HttpContent class
    byte[] addServiceKeyToRequestBody(byte[] content) throws IOException {
            // Parse the original content into a Map
            Map<String, Object> body = objectMapper.readValue(content, Map.class);

            // Add the service_key property
            String serviceKey = secretStore.getConfigPropertyOrError(ConfigProperty.SERVICE_KEY);
            body.put("service_key", serviceKey);

            // Convert the updated Map back to JSON
            return objectMapper.writeValueAsBytes(body);
    }

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        // a 'service_key' created by admin in Windsurf UX
        SERVICE_KEY,
    }


    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        // in effect, no-credentials
        return new Credentials() {
            @Override
            public String getAuthenticationType() {
                return "";
            }

            @Override
            public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
                return Map.of();
            }

            @Override
            public boolean hasRequestMetadata() {
                return false;
            }

            @Override
            public boolean hasRequestMetadataOnly() {
                return false;
            }

            @Override
            public void refresh() throws IOException {
                //no-op
            }
        };
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            ConfigProperty.SERVICE_KEY
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Arrays.stream(ConfigProperty.values()).collect(Collectors.toSet());
    }
}
