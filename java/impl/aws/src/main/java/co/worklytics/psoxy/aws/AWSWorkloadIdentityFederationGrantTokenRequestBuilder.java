package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.WorkloadIdentityFederationGrantTokenRequestBuilder;
import com.google.common.collect.Streams;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentity.model.GetOpenIdTokenForDeveloperIdentityRequest;
import software.amazon.awssdk.services.cognitoidentity.model.GetOpenIdTokenForDeveloperIdentityResponse;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Federation for AWS using a Cognito instance to have an IDENTITY_ID previously created
 * identity to obtain its security JWT.
 * The identity created should match login link DEVELOPER_NAME_ID=CLIENT_ID value created
 */
@Log
public class AWSWorkloadIdentityFederationGrantTokenRequestBuilder extends WorkloadIdentityFederationGrantTokenRequestBuilder {

    @Inject
    public AWSWorkloadIdentityFederationGrantTokenRequestBuilder(ConfigService configService,
                                                                 CognitoIdentityClient cognitoIdentityClient) {
        super(configService);
        this.cognitoIdentityClient = cognitoIdentityClient;
    }

    enum ConfigProperty implements ConfigService.ConfigProperty {
        IDENTITY_POOL_ID,
        IDENTITY_ID,
        DEVELOPER_NAME_ID,
        ;

        @Getter
        private boolean envVarOnly = true;
    }

    final CognitoIdentityClient cognitoIdentityClient;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Streams.concat(super.getRequiredConfigProperties().stream(),
                        Arrays.stream(ConfigProperty.values()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Streams.concat(super.getAllConfigProperties().stream(),
                        Stream.of(ConfigProperty.values()))
                .collect(Collectors.toSet());
    }

    @Override
    @SneakyThrows
    protected String getClientAssertion() {
        GetOpenIdTokenForDeveloperIdentityResponse response = cognitoIdentityClient
                .getOpenIdTokenForDeveloperIdentity(GetOpenIdTokenForDeveloperIdentityRequest.builder()
                        .identityId(getConfig().getConfigPropertyOrError(ConfigProperty.IDENTITY_ID))
                        .identityPoolId(getConfig().getConfigPropertyOrError(ConfigProperty.IDENTITY_POOL_ID))
                        .logins(Collections.singletonMap(getConfig().getConfigPropertyOrError(ConfigProperty.DEVELOPER_NAME_ID),
                                getConfig().getConfigPropertyOrError(WorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)))
                        .build());

        return response.token();
    }
}
