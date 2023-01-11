package co.worklytics.psoxy.gateway;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;

public interface ConfigService {

    interface ConfigProperty extends Serializable {

        String name();

        // shared? (across multiple instances?)
        // local? (per-instance secrets)


        //sensitive? (eg, value should not be exposed to 3rd parties)

        //secret? (eg, value should be handled as a secret; obscured/acl even internally; avoid in logs, etc)
    }


    default boolean supportsWriting() {
        return false;
    }

    void putConfigProperty(ConfigProperty property, String value);

    String getConfigPropertyOrError(ConfigProperty property);

    Optional<String> getConfigPropertyAsOptional(ConfigProperty property);

    default Optional<ConfigValueWithMetadata> getConfigPropertyWithMetadata(ConfigProperty configProperty) {
        return getConfigPropertyAsOptional(configProperty)
            .map(value -> ConfigValueWithMetadata.builder().value(value).build());
    }

    default boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }


    @Builder
    @Value
    class ConfigValueWithMetadata implements Serializable {

        String value;


        @Getter(value = AccessLevel.NONE)
        Instant lastModifiedDate;

        /**
         * @return time value last written, if known/available
         */
        public Optional<Instant> getLastModifiedDate() {
            return Optional.ofNullable(lastModifiedDate);
        }
    }
}
