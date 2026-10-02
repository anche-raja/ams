package org.example.am.network.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class LanTypeCValidatorTest {

    private LanTypeCValidator validator;

    @Before
    public void setUp() {
        validator = new LanTypeCValidator();
    }

    @Test
    public void thePointToPointPairIsValidInEitherOrder() {
        assertTrue(validator.isValid("64.12.30.1", "255.255.255.252", "64.12.30.2"));
        assertTrue(validator.isValid("64.12.30.2", "255.255.255.252", "64.12.30.1"));
    }

    @Test
    public void publicAddressingIsAllowed() {
        assertTrue(validator.validate("64.12.30.1", "255.255.255.252", "64.12.30.2").isEmpty());
    }

    @Test
    public void anythingWiderThanSlashThirtyIsRejected() {
        final List<String> messages = validator.validate("64.12.30.1", "255.255.255.0", "64.12.30.2");
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("requires a /30 subnet"));
    }

    @Test
    public void aGatewayOutsideThePairIsRejected() {
        final List<String> messages = validator.validate("64.12.30.1", "255.255.255.252", "64.12.30.5");
        assertTrue(messages.toString().contains("not inside the LAN subnet"));
    }

    @Test
    public void theNetworkAddressOfTheSlashThirtyIsNotAssignable() {
        final List<String> messages = validator.validate("64.12.30.0", "255.255.255.252", "64.12.30.1");
        assertTrue(messages.toString().contains("network or broadcast address"));
    }

    @Test
    public void lanTypeIsReported() {
        assertEquals("C", validator.getLanType());
    }
}
