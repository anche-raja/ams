package org.example.am.network.validation;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class WanValidatorTest {

    private WanValidator validator;

    @Before
    public void setUp() {
        validator = new WanValidator();
    }

    @Test
    public void aRoutableHandoffIsValid() {
        assertTrue(validator.isValid("64.12.30.6", "255.255.255.252", "64.12.30.5"));
    }

    @Test
    public void privateSpaceIsRejectedOnTheCarrierSide() {
        final List<String> messages = validator.validate("10.1.1.2", "255.255.255.252", "10.1.1.1");
        assertTrue(messages.toString().contains("RFC 1918 private space"));
    }

    @Test
    public void aNextHopOutsideTheDelegatedSubnetIsRejected() {
        final List<String> messages = validator.validate("64.12.30.6", "255.255.255.252", "64.12.31.1");
        assertTrue(messages.toString().contains("not inside the delegated subnet"));
    }

    @Test
    public void carriersDoNotDelegateAnythingWiderThanSlashTwentyFour() {
        final List<String> messages = validator.validate("64.12.30.6", "255.255.0.0", "64.12.30.5");
        assertTrue(messages.toString().contains("wider than the /24"));
    }

    @Test
    public void documentationRangesAreRestricted() {
        final List<String> messages = validator.validate("203.0.113.6", "255.255.255.252", "203.0.113.5");
        assertTrue(messages.toString().contains("restricted range"));
    }

    @Test
    public void dnsMustBeReachableUnicast() {
        final List<String> messages = validator.validate("64.12.30.6", "255.255.255.252",
                "64.12.30.5", "224.0.0.1", "127.0.0.1");
        assertTrue(messages.toString().contains("Primary DNS address"));
        assertTrue(messages.toString().contains("Secondary DNS address"));
    }

    @Test
    public void everyMissingFieldIsReportedAtOnce() {
        final List<String> messages = validator.validate(null, null, null);
        assertTrue(messages.size() == 3);
    }
}
