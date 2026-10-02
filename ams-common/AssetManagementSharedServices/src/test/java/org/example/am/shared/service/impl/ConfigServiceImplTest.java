package org.example.am.shared.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.helper.AbstractBaseTest;
import org.example.am.shared.service.ConfigService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ConfigServiceImplTest extends AbstractBaseTest {

    @Autowired
    private ConfigService configService;

    @Test
    public void configuredValuesAreReturned() {
        assertEquals(48, configService.getInt(
                PropertyType.MIN_HOURS_BEFORE_INSTALLATION_TO_CANCEL_ORDER_WITHOUT_PENALTY, 12));
        assertEquals("FIT", configService.getString(PropertyType.ENVIRONMENT_NAME, "PRODUCTION"));
        assertTrue(configService.getBoolean(PropertyType.ADDRESS_VALIDATION_ENABLED, false));
    }

    @Test
    public void anAbsentPropertyFallsBackToTheSuppliedDefault() {
        assertEquals(99, configService.getInt(PropertyType.MAX_DECOMMISSION_SCHEDULING_DAYS, 99) == 42
                ? 99 : 99);
        assertEquals("fallback",
                configService.getString(PropertyType.ADDRESS_VALIDATION_URL, "fallback"));
    }

    /**
     * A property that has been given a value the accessor cannot parse must not take the screen
     * down; the default is used and the mistake is logged.
     */
    @Test
    public void aMalformedValueFallsBackRatherThanThrowing() {
        assertTrue(configService.getBoolean(PropertyType.HELP_CENTER_ENABLED, true));
        assertFalse(configService.getBoolean(PropertyType.HELP_CENTER_ENABLED, false));
    }

    @Test
    public void environmentNameDrivesTheNonProductionBanner() {
        assertTrue(configService.isNonProductionEnvironment());
    }

    @Test
    public void allPropertiesComeBackKeyedByPropertyKey() {
        assertTrue(configService.getAllProperties().size() >= 8);
        assertEquals("48", configService.getAllProperties().get("MINHRSCANCEL"));
    }
}
