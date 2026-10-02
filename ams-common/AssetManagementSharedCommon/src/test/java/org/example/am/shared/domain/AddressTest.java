package org.example.am.shared.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AddressTest {

    private Address address;

    @Before
    public void setUp() {
        address = new Address();
        address.setAddressLine1("100 Main Street");
        address.setCity("Springfield");
        address.setState(StateType.ILLINOIS);
        address.setZipCode("62704");
        address.setCountry(CountryType.US);
    }

    @Test
    public void aDomesticAddressOmitsTheCountryLine() {
        assertEquals("100 Main Street\nSpringfield, IL 62704", address.toString());
    }

    @Test
    public void anInternationalAddressAppendsTheCountry() {
        address.setCountry(CountryType.CA);
        assertTrue(address.toString().endsWith("\nCanada"));
        assertFalse(address.isDomestic());
    }

    @Test
    public void secondLineAndAttentionAreIncludedWhenPresent() {
        address.setAddressLine2("Suite 400");
        address.setAttentionTo("Jane Doe");
        assertEquals("ATTN: Jane Doe\n100 Main Street\nSuite 400\nSpringfield, IL 62704",
                address.toString());
    }

    @Test
    public void blankPartsAreSkippedRatherThanLeavingEmptyLines() {
        address.setAddressLine2("   ");
        address.setAttentionTo("");
        assertEquals("100 Main Street\nSpringfield, IL 62704", address.toString());
    }

    @Test
    public void anEmptyAddressRendersAsAnEmptyString() {
        assertEquals("", new Address().toString());
    }

    @Test
    public void carrierLabelLimitsAreEnforcedPerLine() {
        assertFalse(address.isOverCarrierLabelLimits());

        address.setAddressLine1("12345678901234567890123456");
        assertFalse(address.isOverCarrierLabelLimits());

        address.setAddressLine1("123456789012345678901234567");
        assertTrue(address.isOverCarrierLabelLimits());
    }

    @Test
    public void theCityHasAShorterLimitThanTheAddressLines() {
        address.setCity("12345678901234567890");
        assertFalse(address.isOverCarrierLabelLimits());
        address.setCity("123456789012345678901");
        assertTrue(address.isOverCarrierLabelLimits());
    }

    @Test
    public void completenessRequiresLineOneCityZipStateAndCountry() {
        assertTrue(address.isComplete());
        address.setZipCode(null);
        assertFalse(address.isComplete());
        address.setZipCode("  ");
        assertFalse(address.isComplete());
    }
}
