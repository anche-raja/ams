package org.example.am.network.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class LanTypeAValidatorTest {

    private LanTypeAValidator validator;

    @Before
    public void setUp() {
        validator = new LanTypeAValidator();
    }

    @Test
    public void deviceOnFirstUsableWithCustomerRouterOnLastUsableIsValid() {
        assertTrue(validator.isValid("10.10.5.1", "255.255.255.0", "10.10.5.254"));
    }

    @Test
    public void deviceMustTakeTheFirstUsableAddress() {
        final List<String> messages = validator.validate("10.10.5.9", "255.255.255.0", "10.10.5.254");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("first usable address"));
    }

    @Test
    public void publicAddressingIsRejected() {
        final List<String> messages = validator.validate("8.8.8.1", "255.255.255.0", "8.8.8.254");
        assertTrue(messages.toString().contains("RFC 1918"));
    }

    @Test
    public void subnetSmallerThanSlashTwentyNineIsRejected() {
        final List<String> messages = validator.validate("10.10.5.1", "255.255.255.252", "10.10.5.2");
        assertTrue(messages.toString().contains("no smaller than /29"));
    }

    @Test
    public void gatewayOutsideTheSubnetIsRejected() {
        final List<String> messages = validator.validate("10.10.5.1", "255.255.255.0", "10.10.6.254");
        assertTrue(messages.toString().contains("not inside the LAN subnet"));
    }

    @Test
    public void missingValuesAreReportedBeforeAnythingElse() {
        final List<String> messages = validator.validate(null, null, null);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("IP address is required"));
        assertTrue(messages.get(1).contains("subnet mask is required"));
    }

    @Test
    public void networkAddressCannotBeAssignedToTheDevice() {
        final List<String> messages = validator.validate("10.10.5.0", "255.255.255.0", "10.10.5.254");
        assertTrue(messages.toString().contains("network or broadcast address"));
    }

    @Test
    public void restrictedManagementSpaceIsRejected() {
        assertFalse(validator.isValid("10.255.0.1", "255.255.255.0", "10.255.0.254"));
    }

    @Test
    public void lanTypeIsReported() {
        assertEquals("A", validator.getLanType());
    }
}
