package org.example.am.network.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IPRangeTest {

    @Test
    public void containmentIsInclusiveOfBothBounds() {
        final IPRange range = new IPRange("10.0.0.10", "10.0.0.20");
        assertTrue(range.contains("10.0.0.10"));
        assertTrue(range.contains("10.0.0.20"));
        assertTrue(range.contains("10.0.0.15"));
        assertFalse(range.contains("10.0.0.9"));
        assertFalse(range.contains("10.0.0.21"));
    }

    @Test
    public void cidrIsExpandedToItsBounds() {
        final IPRange range = IPRange.fromCidr("192.168.1.0/24", "Test");
        assertEquals("192.168.1.0", range.getStartAddress());
        assertEquals("192.168.1.255", range.getEndAddress());
        assertEquals(256L, range.size());
    }

    @Test
    public void cidrHostBitsAreMaskedOff() {
        final IPRange range = IPRange.fromCidr("192.168.1.57/24", null);
        assertEquals("192.168.1.0", range.getStartAddress());
    }

    @Test
    public void overlapIsSymmetric() {
        final IPRange left = new IPRange("10.0.0.0", "10.0.0.100");
        final IPRange right = new IPRange("10.0.0.100", "10.0.0.200");
        assertTrue(left.overlaps(right));
        assertTrue(right.overlaps(left));
        assertFalse(left.overlaps(new IPRange("10.0.0.101", "10.0.0.200")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void anInvertedRangeIsRejected() {
        new IPRange("10.0.0.20", "10.0.0.10");
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMalformedCidrIsRejected() {
        IPRange.fromCidr("192.168.1.0/33", null);
    }

    @Test
    public void restrictedRangesCoverTheSpecialUseBlocks() {
        assertTrue(RestrictedIPRanges.isRestricted("127.0.0.1"));
        assertTrue(RestrictedIPRanges.isRestricted("169.254.10.1"));
        assertTrue(RestrictedIPRanges.isRestricted("239.1.1.1"));
        assertTrue(RestrictedIPRanges.isRestricted("10.255.1.1"));
        assertFalse(RestrictedIPRanges.isRestricted("10.1.1.1"));
        assertEquals("Loopback", RestrictedIPRanges.getRestrictingRange("127.0.0.1").getDescription());
    }
}
