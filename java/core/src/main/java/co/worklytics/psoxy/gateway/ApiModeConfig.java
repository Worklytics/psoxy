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
 * Configuration for REST API data connector mode: settings loaded once for the API proxy runtime.
 */
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiModeConfig {

    @Builder.Default
    private final List<String> allowedDataAccessIpBlocks = Collections.emptyList();

    public static ApiModeConfig fromConfigService(ConfigService configService) {
        String csv = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ALLOWED_DATA_ACCESS_IP_BLOCKS)
                .orElse(null);

        List<String> list = StringUtils.isNotBlank(csv)
                ? Splitter.on(',').trimResults().omitEmptyStrings().splitToList(csv)
                : Collections.emptyList();

        return ApiModeConfig.builder()
                .allowedDataAccessIpBlocks(list)
                .build();
    }
}
