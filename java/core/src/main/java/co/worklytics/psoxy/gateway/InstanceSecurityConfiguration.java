package co.worklytics.psoxy.gateway;

import com.google.common.base.Splitter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for instance-level security features like IP lockdown.
 */
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class InstanceSecurityConfiguration {

    public enum Properties implements ConfigService.ConfigProperty {
        /**
         * A CSV of IPs or CIDR blocks allowed to make data access requests.
         */
        ALLOWED_DATA_ACCESS_IP_BLOCKS;

        @Override
        public SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR_OR_REMOTE;
        }
    }

    @Builder.Default
    private final List<String> allowedDataAccessIpBlocks = Collections.emptyList();

    public static InstanceSecurityConfiguration fromConfigService(ConfigService configService) {
        String csv = configService.getConfigPropertyAsOptional(Properties.ALLOWED_DATA_ACCESS_IP_BLOCKS)
                .orElse(null);

        List<String> list = StringUtils.isNotBlank(csv)
                ? Splitter.on(',').trimResults().omitEmptyStrings().splitToList(csv)
                : Collections.emptyList();

        return InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(list)
                .build();
    }
}
