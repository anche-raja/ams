package org.example.am.shared.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Covers the contract every generated loadable type relies on.
 */
public class LoadableTypeTest {

    @Test
    public void lookupReturnsTheSingletonInstance() {
        assertSame(AssetStatusType.ACTIVE, AssetStatusType.lookup("ACTIVE"));
    }

    @Test
    public void lookupTrimsAndUpperCasesTheCode() {
        assertSame(AssetStatusType.ACTIVE, AssetStatusType.lookup("  active  "));
    }

    @Test
    public void anUnknownOrNullCodeLooksUpToNull() {
        assertNull(AssetStatusType.lookup("NOT_A_STATUS"));
        assertNull(AssetStatusType.lookup(null));
    }

    @Test
    public void valuesKeepDeclarationOrderSoDropDownsNeedNoSorting() {
        final List<String> codes = new ArrayList<String>(AssetStatusType.codes());
        assertEquals("ORDERED", codes.get(0));
        assertEquals("SHIPPED", codes.get(1));
        assertEquals("INSTALLED", codes.get(2));
    }

    @Test
    public void databaseIdsAreAssignedAndReversible() {
        final AssetStatusType byId =
                AssetStatusType.lookupByDatabaseId(AssetStatusType.ACTIVE.getDatabaseId());
        assertSame(AssetStatusType.ACTIVE, byId);
        assertNull(AssetStatusType.lookupByDatabaseId(Long.valueOf(-1L)));
        assertNull(AssetStatusType.lookupByDatabaseId(null));
    }

    @Test
    public void equalityIsByConcreteTypeAndCode() {
        // Both hierarchies define a CANCELLED code; they must not compare equal.
        assertEquals("CANCELLED", AssetStatusType.CANCELLED.getCode());
        assertEquals("CANCELLED", OrderStatusType.CANCELLED.getCode());
        assertTrue(!AssetStatusType.CANCELLED.equals(OrderStatusType.CANCELLED));
    }

    @Test
    public void toStringRendersTheCodeForHiddenFormFields() {
        assertEquals("ACTIVE", AssetStatusType.ACTIVE.toString());
    }

    @Test
    public void everyStateAndHourIsRegistered() {
        assertEquals(54, StateType.values().size());
        assertEquals(24, HourType.values().size());
        assertNotNull(StateType.lookup("IL"));
        assertEquals("12:00 AM", HourType.HOUR_00.getDescription());
        assertEquals("11:00 PM", HourType.HOUR_23.getDescription());
    }
}
