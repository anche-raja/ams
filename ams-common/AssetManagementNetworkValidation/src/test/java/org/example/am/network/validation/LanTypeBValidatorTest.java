package org.example.am.network.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class LanTypeBValidatorTest {

    private LanTypeBValidator validator;

    @Before
    public void setUp() {
        validator = new LanTypeBValidator();
    }

    @Test
    public void deviceMaySitAnywhereInTheSubnet() {
        assertTrue(validator.isValid("192.168.20.37", "255.255.255.0", null));
    }

    @Test
    public void gatewayIsOptional() {
        assertTrue(validator.validate("192.168.20.37", "255.255.255.0", null).isEmpty());
    }

    @Test
    public void aSuppliedGatewayStillHasToBeInTheSubnet() {
        final List<String> messages = validator.validate("192.168.20.37", "255.255.255.0", "192.168.21.1");
        assertTrue(messages.toString().contains("not inside the LAN subnet"));
    }

    @Test
    public void slashThirtyIsTheNarrowestAcceptedSubnet() {
        assertTrue(validator.isValid("192.168.20.1", "255.255.255.252", null));
        final List<String> messages = validator.validate("192.168.20.1", "255.255.255.254", null);
        assertTrue(messages.toString().contains("no smaller than /30"));
    }

    @Test
    public void publicAddressingIsRejected() {
        final List<String> messages = validator.validate("203.0.113.10", "255.255.255.0", null);
        assertTrue(messages.toString().contains("RFC 1918"));
    }

    @Test
    public void lanTypeIsReported() {
        assertEquals("B", validator.getLanType());
    }
}
