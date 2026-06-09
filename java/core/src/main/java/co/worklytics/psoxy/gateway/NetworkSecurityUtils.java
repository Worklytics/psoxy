package co.worklytics.psoxy.gateway;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import lombok.extern.java.Log;
import org.apache.commons.net.util.SubnetUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Network security helpers (IP allowlists, etc.) built from injected configuration POJOs.
 */
@Log
@Singleton
public class NetworkSecurityUtils {

    private final IpAllowlistRules dataAccessRules;
    private final IpAllowlistRules webhookRules;

    @Inject
    public NetworkSecurityUtils(ApiModeConfig apiModeConfig,
                                WebhookCollectorModeConfig webhookCollectorModeConfig) {
        this.dataAccessRules = IpAllowlistRules.fromBlocks(apiModeConfig.getAllowedDataAccessIpBlocks());
        this.webhookRules = IpAllowlistRules.fromBlocks(
            webhookCollectorModeConfig.getAllowedWebhookIpBlocks().orElseGet(Set::of));
    }

    public boolean isDataAccessIpAllowed(String rawClientIp) {
        return dataAccessRules.isAllowed(rawClientIp);
    }

    public boolean isWebhookIpAllowed(String rawClientIp) {
        return webhookRules.isAllowed(rawClientIp);
    }

    /**
     * Parsed allowlist rules for one endpoint (data access or webhooks).
     */
    private static final class IpAllowlistRules {

        private final boolean lockdownEnabled;
        private final Set<String> exactIps;
        private final Map<String, SubnetUtils> cidrs;

        static IpAllowlistRules fromBlocks(Collection<String> allowedBlocks) {
            boolean anyConfigured = allowedBlocks != null && !allowedBlocks.isEmpty();
            if (!anyConfigured) {
                return new IpAllowlistRules(false, Set.of(), Map.of());
            }

            ImmutableSet.Builder<String> exactBuilder = ImmutableSet.builder();
            ImmutableMap.Builder<String, SubnetUtils> cidrBuilder = ImmutableMap.builder();
            for (String block : allowedBlocks) {
                if (block == null || block.isBlank()) {
                    continue;
                }
                String trimmed = block.trim();
                if (trimmed.contains("/")) {
                    try {
                        SubnetUtils utils = new SubnetUtils(trimmed);
                        utils.setInclusiveHostCount(true);
                        utils.getInfo().getNetworkAddress();
                        cidrBuilder.put(trimmed, utils);
                    } catch (RuntimeException e) {
                        log.warning("Invalid CIDR in allowlist, ignoring: " + trimmed);
                    }
                } else if (InetAddresses.isInetAddress(trimmed)) {
                    exactBuilder.add(trimmed);
                } else {
                    log.warning("Invalid exact IP in allowlist, ignoring: " + trimmed);
                }
            }
            return new IpAllowlistRules(true, exactBuilder.build(), cidrBuilder.build());
        }

        private IpAllowlistRules(boolean lockdownEnabled, Set<String> exactIps, Map<String, SubnetUtils> cidrs) {
            this.lockdownEnabled = lockdownEnabled;
            this.exactIps = exactIps;
            this.cidrs = cidrs;
        }

        boolean isAllowed(String rawClientIp) {
            if (!lockdownEnabled) {
                return true;
            }

            String clientIp = normalizeClientIp(rawClientIp);
            if (clientIp == null || !InetAddresses.isInetAddress(clientIp)) {
                log.warning("IP lockdown is enabled but client IP could not be determined. Rejecting request.");
                return false;
            }

            if (exactIps.contains(clientIp)) {
                return true;
            }
            for (SubnetUtils utils : cidrs.values()) {
                if (utils.getInfo().isInRange(clientIp)) {
                    return true;
                }
            }
            return false;
        }

        private static String normalizeClientIp(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            List<String> parts = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(raw);
            if (parts.size() != 1) {
                return null;
            }
            String clientIp = parts.get(0);
            if (clientIp.indexOf(':') >= 0 && clientIp.chars().filter(ch -> ch == ':').count() == 1) {
                String beforePort = clientIp.substring(0, clientIp.indexOf(':'));
                if (InetAddresses.isInetAddress(beforePort)) {
                    return beforePort;
                }
            }
            int zone = clientIp.indexOf('%');
            if (zone >= 0) {
                return clientIp.substring(0, zone);
            }
            return clientIp;
        }
    }
}
