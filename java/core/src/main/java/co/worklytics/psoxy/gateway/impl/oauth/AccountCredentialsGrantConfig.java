package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AccountCredentialsGrantConfig {

    public static AccountCredentialsGrantConfig fromConfigService(ConfigService configService) {
        AccountCredentialsGrantConfigBuilder builder = AccountCredentialsGrantConfig.builder();
        configService.getConfigPropertyAsOptional(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.ACCOUNT_ID)
            .ifPresent(builder::accountId);
        configService.getConfigPropertyAsOptional(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)
            .ifPresent(builder::clientId);
        return builder.build();
    }

    String accountId;
    String clientId;

    public Optional<String> getAccountId() {
        return Optional.ofNullable(accountId);
    }

    public Optional<String> getClientId() {
        return Optional.ofNullable(clientId);
    }
}
