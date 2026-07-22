package co.worklytics.psoxy.gateway;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import lombok.extern.java.Log;
import org.apache.commons.net.util.SubnetUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        private final Map<String, SubnetUtils> ipv4Cidrs;
        private final List<Ipv6Cidr> ipv6Cidrs;

        static IpAllowlistRules fromBlocks(Collection<String> allowedBlocks) {
            boolean anyConfigured = allowedBlocks != null && !allowedBlocks.isEmpty();
            if (!anyConfigured) {
                return new IpAllowlistRules(false, Set.of(), Map.of(), List.of());
            }

            ImmutableSet.Builder<String> exactBuilder = ImmutableSet.builder();
            ImmutableMap.Builder<String, SubnetUtils> ipv4CidrBuilder = ImmutableMap.builder();
            List<Ipv6Cidr> ipv6CidrList = new ArrayList<>();
            for (String block : allowedBlocks) {
                if (block == null || block.isBlank()) {
                    continue;
                }
                String trimmed = block.trim();
                if (trimmed.contains("/")) {
                    if (trimmed.contains(":")) {
                        Optional<Ipv6Cidr> parsed = Ipv6Cidr.parse(trimmed);
                        if (parsed.isPresent()) {
                            ipv6CidrList.add(parsed.get());
                        } else {
                            log.warning("Invalid CIDR in allowlist, ignoring: " + trimmed);
                        }
                    } else {
                        try {
                            SubnetUtils utils = new SubnetUtils(trimmed);
                            utils.setInclusiveHostCount(true);
                            utils.getInfo().getNetworkAddress();
                            ipv4CidrBuilder.put(trimmed, utils);
                        } catch (RuntimeException e) {
                            log.warning("Invalid CIDR in allowlist, ignoring: " + trimmed);
                        }
                    }
                } else if (InetAddresses.isInetAddress(trimmed)) {
                    exactBuilder.add(trimmed);
                } else {
                    log.warning("Invalid exact IP in allowlist, ignoring: " + trimmed);
                }
            }
            return new IpAllowlistRules(true, exactBuilder.build(), ipv4CidrBuilder.build(), List.copyOf(ipv6CidrList));
        }

        private IpAllowlistRules(boolean lockdownEnabled,
                                 Set<String> exactIps,
                                 Map<String, SubnetUtils> ipv4Cidrs,
                                 List<Ipv6Cidr> ipv6Cidrs) {
            this.lockdownEnabled = lockdownEnabled;
            this.exactIps = exactIps;
            this.ipv4Cidrs = ipv4Cidrs;
            this.ipv6Cidrs = ipv6Cidrs;
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
            for (SubnetUtils utils : ipv4Cidrs.values()) {
                if (utils.getInfo().isInRange(clientIp)) {
                    return true;
                }
            }
            for (Ipv6Cidr cidr : ipv6Cidrs) {
                if (cidr.contains(clientIp)) {
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
                log.warning("Client IP could not be parsed (expected single IP, got comma-separated chain): " + raw);
                return null;
            }
            String clientIp = parts.get(0);
            if (clientIp.contains(":") && clientIp.chars().filter(ch -> ch == ':').count() == 1) {
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

    /**
     * IPv6-CIDR matcher. Apache Commons Net {@link SubnetUtils} is IPv4-only.
     */
    private static final class Ipv6Cidr {

        private final byte[] network;
        private final int prefixLength;

        private Ipv6Cidr(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Optional<Ipv6Cidr> parse(String cidr) {
            int slash = cidr.indexOf('/');
            if (slash < 0) {
                return Optional.empty();
            }
            String addressPart = cidr.substring(0, slash);
            int prefixLength;
            try {
                prefixLength = Integer.parseInt(cidr.substring(slash + 1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefixLength < 0 || prefixLength > 128) {
                return Optional.empty();
            }
            byte[] address;
            try {
                address = InetAddresses.forString(addressPart).getAddress();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            if (address.length != 16) {
                return Optional.empty();
            }
            return Optional.of(new Ipv6Cidr(maskToNetwork(address, prefixLength), prefixLength));
        }

        boolean contains(String clientIp) {
            byte[] address;
            try {
                address = InetAddresses.forString(clientIp).getAddress();
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (address.length != 16) {
                return false;
            }
            return isInSubnet(network, address, prefixLength);
        }

        private static byte[] maskToNetwork(byte[] address, int prefixLength) {
            byte[] network = address.clone();
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = fullBytes; i < network.length; i++) {
                network[i] = 0;
            }
            if (remainingBits > 0 && fullBytes < network.length) {
                int mask = 0xFF << (8 - remainingBits);
                network[fullBytes] = (byte) (network[fullBytes] & mask);
            }
            return network;
        }

        private static boolean isInSubnet(byte[] network, byte[] address, int prefixLength) {
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != address[i]) {
                    return false;
                }
            }
            if (remainingBits > 0 && fullBytes < network.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((network[fullBytes] & mask) != (address[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        }
    }
}
