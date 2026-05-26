package co.worklytics.psoxy.gateway;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.java.Log;
import org.apache.commons.net.util.SubnetUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Utility for network security checks, like IP allowlists.
 */
@Log
public class NetworkSecurityUtils {

    // Cache SubnetUtils to avoid re-compiling CIDR blocks on every evaluation
    private static final LoadingCache<String, SubnetUtils> SUBNET_UTILS_CACHE = CacheBuilder.newBuilder()
            .maximumSize(200)
            .build(new CacheLoader<String, SubnetUtils>() {
                @Override
                public SubnetUtils load(String key) {
                    SubnetUtils utils = new SubnetUtils(key);
                    utils.setInclusiveHostCount(true);
                    return utils;
                }
            });

    /**
     * Checks if the given client IP is authorized according to the configured list of allowed block/IPs.
     * If no blocks are configured, it defaults to open.
     *
     * @param clientIp The IP address to check
     * @param allowedBlocks A definitive list of specific allowed IP/CIDR string definitions
     * @return true if authorized, false otherwise
     */
    public static boolean isAllowed(String clientIp, List<String> allowedBlocks) {
        if (allowedBlocks == null || allowedBlocks.isEmpty()) {
            return true;
        }

        if (clientIp == null || clientIp.isBlank()) {
            log.warning("IP lockdown is enabled but client IP could not be determined. Rejecting request.");
            return false;
        }

        for (String block : allowedBlocks) {
            try {
                if (block.contains("/")) {
                    SubnetUtils utils = SUBNET_UTILS_CACHE.get(block);
                    if (utils.getInfo().isInRange(clientIp)) {
                        return true;
                    }
                } else {
                    if (block.equals(clientIp)) {
                        return true;
                    }
                }
            } catch (ExecutionException | com.google.common.util.concurrent.UncheckedExecutionException | IllegalArgumentException e) {
                log.warning("Invalid IP or CIDR block configured in allowlist: " + block);
            }
        }

        return false;
    }
}
