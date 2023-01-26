package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.WorkloadIdentityFederationGrantTokenRequestBuilder;
import com.google.common.collect.Streams;
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
 * Based on <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust-gcp?tabs=azure-cli%2Cjava#get-an-id-token-for-your-google-service-account">...</a>
 * <p>
 * Implementation of Workload Identity Federation for AWS, getting an ID token
 * to be exposed as client assertion
 */

// NOTE: For some reason, this annotation is not working in this class,
//       and we need to create a default constructor manually.
//       Otherwise, the compiler will complain with the following message: cannot be provided without an @Inject constructor or an @Provides-annotated method
//       like lombok is not being executed in the right order
//@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class AWSWorkloadIdentityFederationGrantTokenRequestBuilder extends WorkloadIdentityFederationGrantTokenRequestBuilder {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        IDENTITY_POOL_ID,
        IDENTITY_ID,
        DEVELOPER_NAME_ID
    }

    @Inject
    public AWSWorkloadIdentityFederationGrantTokenRequestBuilder(){}

    @Inject
    CognitoIdentityClient cognitoIdentityClient;

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