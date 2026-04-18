package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSecurityUtilsTest {

    private static NetworkSecurityUtils dataAccessUtils(List<String> blocks) {
        return new NetworkSecurityUtils(
                ApiModeConfig.builder().allowedDataAccessIpBlocks(blocks).build(),
                WebhookCollectorModeConfig.builder().build());
    }

    @Test
    void testIsAllowed_EmptyConfig() {
        NetworkSecurityUtils utils = dataAccessUtils(Collections.emptyList());
        assertTrue(utils.isDataAccessIpAllowed("192.168.1.1"), "Should be allowed if list is empty (default open)");
        assertTrue(utils.isDataAccessIpAllowed(null), "Null IP shouldn't fail if list is empty");
    }

    @Test
    void testIsAllowed_NullClientIp() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("192.168.1.1/24"));
        assertFalse(utils.isDataAccessIpAllowed(null), "Should reject if IP list exists but client IP is null");
        assertFalse(utils.isDataAccessIpAllowed("  "), "Should reject if IP list exists but client IP is blank");
    }

    @Test
    void testIsAllowed_ExactIpMatch() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("10.0.0.5", "192.168.0.1"));
        assertTrue(utils.isDataAccessIpAllowed("10.0.0.5"));
        assertTrue(utils.isDataAccessIpAllowed("192.168.0.1"));
        assertFalse(utils.isDataAccessIpAllowed("10.0.0.6"));
        assertFalse(utils.isDataAccessIpAllowed("192.168.0.2"));
    }

    @Test
    void testIsAllowed_CidrMatch() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("192.168.1.0/24"));
        assertTrue(utils.isDataAccessIpAllowed("192.168.1.1"));
        assertTrue(utils.isDataAccessIpAllowed("192.168.1.255"));
        assertTrue(utils.isDataAccessIpAllowed("192.168.1.0")); // with inclusiveHostCount=true this is valid
        assertFalse(utils.isDataAccessIpAllowed("192.168.2.1"));
    }

    @Test
    void testIsAllowed_InvalidConfigIgnored() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("invalid_ip", "also_not_an_ip", "10.0.0.0/8"));
        assertTrue(utils.isDataAccessIpAllowed("10.0.0.5"));
        assertFalse(utils.isDataAccessIpAllowed("invalid_ip"));
        assertFalse(utils.isDataAccessIpAllowed("also_not_an_ip"));
        assertFalse(utils.isDataAccessIpAllowed("192.168.1.1"));
    }

    @Test
    void testIsAllowed_MultipleBlocks() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("10.0.0.0/8", "172.16.0.0/12", "192.168.1.100"));
        assertTrue(utils.isDataAccessIpAllowed("10.5.5.5"));
        assertTrue(utils.isDataAccessIpAllowed("172.20.0.1"));
        assertTrue(utils.isDataAccessIpAllowed("192.168.1.100"));
        assertFalse(utils.isDataAccessIpAllowed("192.168.1.101"));
        assertFalse(utils.isDataAccessIpAllowed("172.32.0.1"));
        assertFalse(utils.isDataAccessIpAllowed("11.0.0.0"));
    }

    @Test
    void testIsAllowed_XForwardedForUsesFirstIp() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("10.0.0.5"));
        assertTrue(utils.isDataAccessIpAllowed("10.0.0.5, 192.168.1.1"));
    }

    @Test
    void testIsAllowed_Ipv4WithPortStripped() {
        NetworkSecurityUtils utils = dataAccessUtils(Arrays.asList("10.0.0.5"));
        assertTrue(utils.isDataAccessIpAllowed("10.0.0.5:443"));
    }
}
