package co.worklytics.psoxy.gateway;

import javax.inject.Inject;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes inbound HTTP paths for API-mode rule matching and outbound URL construction.
 *
 * <p>Strips an optional configured path prefix ({@link ApiModeConfig.ApiModeConfigProperty#REQUEST_PATH_PREFIX_TO_TRIM}),
 * then removes the deployed function name segment ({@link HostEnvironment#getInstanceId()}).
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class InboundRequestPathNormalizer {

    @Inject
    ApiModeConfig apiModeConfig;
    @Inject
    HostEnvironment hostEnvironment;

    public String normalize(String rawPath) {
        String path = apiModeConfig.getRequestPathPrefixToTrim()
            .map(prefix -> stripLeadingPathPrefix(rawPath, prefix))
            .orElse(rawPath);
        String functionName = hostEnvironment.getInstanceId();
        if (StringUtils.isNotBlank(functionName)) {
            path = stripLeadingFunctionName(path, functionName);
        }
        return path;
    }

    String stripLeadingFunctionName(String path, String functionName) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(functionName)) {
            return path;
        }

        String functionSegment = "/" + functionName;
        if (path.equals(functionSegment)) {
            return "/";
        }
        if (path.startsWith(functionSegment + "/")) {
            return path.substring(functionSegment.length());
        }
        return path;
    }

    String stripLeadingPathPrefix(String path, String prefix) {
        if (StringUtils.isBlank(path) || StringUtils.isBlank(prefix)) {
            return path;
        }

        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String normalizedPrefix = prefix.startsWith("/") ? prefix : "/" + prefix;

        if (normalizedPrefix.endsWith("/")) {
            if (normalizedPath.startsWith(normalizedPrefix)) {
                String remainder = normalizedPath.substring(normalizedPrefix.length());
                return remainder.isEmpty() ? "/" : "/" + StringUtils.stripStart(remainder, "/");
            }
        } else if (normalizedPath.equals(normalizedPrefix)) {
            return "/";
        } else if (normalizedPath.startsWith(normalizedPrefix + "/")) {
            return normalizedPath.substring(normalizedPrefix.length());
        }

        return path;
    }
}
