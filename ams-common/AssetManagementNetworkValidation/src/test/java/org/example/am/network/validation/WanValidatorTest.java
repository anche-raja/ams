package org.example.am.network.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WanValidatorTest {

    private WanValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new WanValidator();
    }

    @Test
    public void publicAddressingIsAllowed() {
        assertTrue(validator.isValid("203.0.113.1", "255.255.255.252", "203.0.113.2"));
    }

    @Test
    public void privateAddressingIsAllowed() {
        assertTrue(validator.isValid("10.0.0.1", "255.255.255.252", "10.0.0.2"));
    }

    @Test
    public void slashThirtyIsTheNarrowestAcceptedSubnet() {
        assertTrue(validator.isValid("203.0.113.1", "255.255.255.252", "203.0.113.2"));
        final List<String> messages = validator.validate("203.0.113.1", "255.255.255.254", "203.0.113.2");
        assertTrue(messages.toString().contains("no smaller than /30"));
    }

    @Test
    public void aGatewayOutsideThePairIsRejected() {
        final List<String> messages = validator.validate("203.0.113.1", "255.255.255.252", "203.0.113.5");
        assertTrue(messages.toString().contains("not inside the LAN subnet"));
    }

    @Test
    public void lanTypeIsReported() {
        assertEquals("WAN", validator.getLanType());
    }
}
