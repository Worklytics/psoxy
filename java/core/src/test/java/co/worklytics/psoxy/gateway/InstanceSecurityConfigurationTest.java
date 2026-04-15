package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceSecurityConfigurationTest {

    @Test
    void testIsAllowed_EmptyConfig() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Collections.emptyList())
                .build();
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.1.1"), "Should be allowed if list is empty (default open)", config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed(null), "Null IP shouldn't fail if list is empty", config.getAllowedDataAccessIpBlocks());
    }

    @Test
    void testIsAllowed_NullClientIp() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Arrays.asList("192.168.1.1/24"))
                .build();
        assertFalse(NetworkSecurityUtils.isAllowed(null), "Should reject if IP list exists but client IP is null", config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("  "), "Should reject if IP list exists but client IP is blank", config.getAllowedDataAccessIpBlocks());
    }

    @Test
    void testIsAllowed_ExactIpMatch() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Arrays.asList("10.0.0.5", "192.168.0.1"))
                .build();
        assertTrue(NetworkSecurityUtils.isAllowed("10.0.0.5"), config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.0.1"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("10.0.0.6"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("192.168.0.2"), config.getAllowedDataAccessIpBlocks());
    }

    @Test
    void testIsAllowed_CidrMatch() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Arrays.asList("192.168.1.0/24"))
                .build();
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.1.1"), config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.1.255"), config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.1.0"), config.getAllowedDataAccessIpBlocks()); // with inclusiveHostCount=true this is valid
        assertFalse(NetworkSecurityUtils.isAllowed("192.168.2.1"), config.getAllowedDataAccessIpBlocks());
    }

    @Test
    void testIsAllowed_InvalidConfigIgnored() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Arrays.asList("invalid_ip", "10.0.0.0/8"))
                .build();
        assertTrue(NetworkSecurityUtils.isAllowed("10.0.0.5"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("invalid_ip"), config.getAllowedDataAccessIpBlocks()); // exception caught and logged, moving to next blocks, ultimately fails to match
        assertFalse(NetworkSecurityUtils.isAllowed("192.168.1.1"), config.getAllowedDataAccessIpBlocks());
    }

    @Test
    void testIsAllowed_MultipleBlocks() {
        InstanceSecurityConfiguration config = InstanceSecurityConfiguration.builder()
                .allowedDataAccessIpBlocks(Arrays.asList("10.0.0.0/8", "172.16.0.0/12", "192.168.1.100"))
                .build();
        assertTrue(NetworkSecurityUtils.isAllowed("10.5.5.5"), config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed("172.20.0.1"), config.getAllowedDataAccessIpBlocks());
        assertTrue(NetworkSecurityUtils.isAllowed("192.168.1.100"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("192.168.1.101"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("172.32.0.1"), config.getAllowedDataAccessIpBlocks());
        assertFalse(NetworkSecurityUtils.isAllowed("11.0.0.0"), config.getAllowedDataAccessIpBlocks());
    }
}
