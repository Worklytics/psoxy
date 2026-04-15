package co.worklytics.psoxy.gateway;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.java.Log;
import org.apache.commons.net.util.SubnetUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.common.base.Splitter;
import lombok.Getter;

/**
 * Configuration for instance-level security features like IP lockdown.
 */
@Log
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

    private final List<String> allowedDataAccessIpBlocks;

    public static InstanceSecurityConfiguration fromConfigService(ConfigService configService) {
        String csv = configService.getConfigPropertyAsOptional(Properties.ALLOWED_DATA_ACCESS_IP_BLOCKS)
                .orElse(null);

        List<String> list = (csv != null && !csv.isBlank()) ? 
                Splitter.on(',').trimResults().omitEmptyStrings().splitToList(csv) : 
                Collections.emptyList();

        return InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(list)
                .build();
    }
}
