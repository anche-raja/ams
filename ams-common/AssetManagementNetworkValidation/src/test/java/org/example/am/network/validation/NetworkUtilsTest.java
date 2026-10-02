package org.example.am.network.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkUtilsTest {

    @Test
    public void validDottedQuadsAreAccepted() {
        assertTrue(NetworkUtils.isValidIpAddress("10.1.2.3"));
        assertTrue(NetworkUtils.isValidIpAddress("0.0.0.0"));
        assertTrue(NetworkUtils.isValidIpAddress("255.255.255.255"));
    }

    @Test
    public void malformedAddressesAreRejected() {
        assertFalse(NetworkUtils.isValidIpAddress(null));
        assertFalse(NetworkUtils.isValidIpAddress(""));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2"));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2.3.4"));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2.256"));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2.-1"));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2.a"));
    }

    @Test
    public void leadingZeroesAreRejectedBecauseTheyAreAmbiguouslyOctal() {
        assertFalse(NetworkUtils.isValidIpAddress("010.1.2.3"));
        assertFalse(NetworkUtils.isValidIpAddress("10.1.2.03"));
    }

    @Test
    public void onlyContiguousMasksAreValid() {
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.255.0"));
        assertTrue(NetworkUtils.isValidSubnetMask("255.255.255.252"));
        assertTrue(NetworkUtils.isValidSubnetMask("128.0.0.0"));
        assertFalse(NetworkUtils.isValidSubnetMask("255.255.0.255"));
        assertFalse(NetworkUtils.isValidSubnetMask("0.0.0.0"));
        assertFalse(NetworkUtils.isValidSubnetMask("255.255.255.253"));
    }

    @Test
    public void prefixLengthIsCountedFromTheMask() {
        assertEquals(24, NetworkUtils.getPrefixLength("255.255.255.0"));
        assertEquals(30, NetworkUtils.getPrefixLength("255.255.255.252"));
        assertEquals(32, NetworkUtils.getPrefixLength("255.255.255.255"));
    }

    @Test
    public void networkAndBroadcastAreDerivedFromTheMask() {
        assertEquals("192.168.1.0", NetworkUtils.getNetworkAddress("192.168.1.57", "255.255.255.0"));
        assertEquals("192.168.1.255", NetworkUtils.getBroadcastAddress("192.168.1.57", "255.255.255.0"));
        assertEquals("192.168.1.1", NetworkUtils.getFirstUsableAddress("192.168.1.57", "255.255.255.0"));
        assertEquals("192.168.1.254", NetworkUtils.getLastUsableAddress("192.168.1.57", "255.255.255.0"));
    }

    @Test
    public void networkAndBroadcastAddressesAreNotAssignable() {
        assertTrue(NetworkUtils.isNetworkOrBroadcastAddress("192.168.1.0", "255.255.255.0"));
        assertTrue(NetworkUtils.isNetworkOrBroadcastAddress("192.168.1.255", "255.255.255.0"));
        assertFalse(NetworkUtils.isNetworkOrBroadcastAddress("192.168.1.1", "255.255.255.0"));
    }

    @Test
    public void slashThirtyOneAndSlashThirtyTwoHaveNoUnusableAddresses() {
        assertFalse(NetworkUtils.isNetworkOrBroadcastAddress("10.0.0.0", "255.255.255.254"));
        assertEquals(2L, NetworkUtils.getUsableHostCount("255.255.255.254"));
        assertEquals(1L, NetworkUtils.getUsableHostCount("255.255.255.255"));
    }

    @Test
    public void usableHostCountExcludesNetworkAndBroadcast() {
        assertEquals(254L, NetworkUtils.getUsableHostCount("255.255.255.0"));
        assertEquals(2L, NetworkUtils.getUsableHostCount("255.255.255.252"));
    }

    @Test
    public void subnetMembershipUsesTheMask() {
        assertTrue(NetworkUtils.isSameSubnet("10.0.0.1", "10.0.0.254", "255.255.255.0"));
        assertFalse(NetworkUtils.isSameSubnet("10.0.0.1", "10.0.1.1", "255.255.255.0"));
    }

    @Test
    public void specialUseRangesAreRecognised() {
        assertTrue(NetworkUtils.isPrivateAddress("10.0.0.1"));
        assertTrue(NetworkUtils.isPrivateAddress("172.16.0.1"));
        assertTrue(NetworkUtils.isPrivateAddress("172.31.255.255"));
        assertFalse(NetworkUtils.isPrivateAddress("172.32.0.1"));
        assertTrue(NetworkUtils.isPrivateAddress("192.168.5.5"));
        assertFalse(NetworkUtils.isPrivateAddress("8.8.8.8"));
        assertTrue(NetworkUtils.isLoopbackAddress("127.0.0.1"));
        assertTrue(NetworkUtils.isMulticastAddress("239.1.1.1"));
        assertTrue(NetworkUtils.isLinkLocalAddress("169.254.1.1"));
    }

    @Test
    public void conversionRoundTrips() {
        assertEquals("192.168.1.57", NetworkUtils.toDottedQuad(NetworkUtils.toLong("192.168.1.57")));
        assertEquals(NetworkUtils.BROADCAST, NetworkUtils.toLong("255.255.255.255"));
    }
}
