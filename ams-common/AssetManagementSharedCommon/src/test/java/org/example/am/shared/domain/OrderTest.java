package org.example.am.shared.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

public class OrderTest {

    private Order order;

    @Before
    public void setUp() {
        order = new Order();
        order.setOrderType(OrderType.NEW_INSTALL);
        order.setOrderStatusType(OrderStatusType.SUBMITTED);
    }

    private static Address domestic() {
        final Address address = new Address();
        address.setCountry(CountryType.US);
        return address;
    }

    private static Address international() {
        final Address address = new Address();
        address.setCountry(CountryType.CA);
        return address;
    }

    @Test
    public void leadTimeVariesByOrderType() {
        order.setShippingAddress(domestic());
        assertEquals(10, order.getInstallLeadTimeDays());

        order.setOrderType(OrderType.MIGRATION);
        assertEquals(15, order.getInstallLeadTimeDays());

        order.setOrderType(OrderType.EMERGENCY_REPLACEMENT);
        assertEquals(2, order.getInstallLeadTimeDays());
    }

    @Test
    public void shippingAbroadAddsTheCustomsAllowance() {
        order.setShippingAddress(international());
        assertEquals(20, order.getInstallLeadTimeDays());

        order.setOrderType(OrderType.EMERGENCY_REPLACEMENT);
        assertEquals(12, order.getInstallLeadTimeDays());
    }

    @Test
    public void anOrderWithNoAddressYetUsesTheDomesticLeadTime() {
        assertEquals(10, order.getInstallLeadTimeDays());
    }

    @Test
    public void openMeansNotCompletedAndNotCancelled() {
        assertTrue(order.isOpen());
        order.setOrderStatusType(OrderStatusType.COMPLETED);
        assertFalse(order.isOpen());
        order.setOrderStatusType(OrderStatusType.CANCELLED);
        assertFalse(order.isOpen());
        order.setOrderStatusType(null);
        assertFalse(order.isOpen());
    }

    @Test
    public void migrationProblemIsOnlyReportedForMigrationOrders() {
        final Asset blocked = new Asset();
        blocked.setAssetStatusType(AssetStatusType.ACTIVE);
        blocked.setMigratable(false);
        order.setAssetBeingReplaced(blocked);

        assertNull(order.getAssetProblemForMigratingOrder());

        order.setOrderType(OrderType.MIGRATION);
        assertEquals(AssetProblemType.NOT_MIGRATABLE, order.getAssetProblemForMigratingOrder());
    }

    @Test
    public void aMigrationOrderWithNoSourceAssetIsItselfAProblem() {
        order.setOrderType(OrderType.MIGRATION);
        assertEquals(AssetProblemType.NOT_MIGRATABLE, order.getAssetProblemForMigratingOrder());
    }

    @Test
    public void aCleanMigrationSourceReportsNoProblem() {
        final Asset clean = new Asset();
        clean.setAssetStatusType(AssetStatusType.ACTIVE);
        clean.setMigratable(true);
        order.setOrderType(OrderType.MIGRATION);
        order.setAssetBeingReplaced(clean);
        assertNull(order.getAssetProblemForMigratingOrder());
    }

    private static Date hoursFromNow(final int hours) {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR_OF_DAY, hours);
        return calendar.getTime();
    }

    @Test
    public void thePenaltyWindowIsMeasuredAgainstTheScheduledInstallation() {
        order.setRequestedInstallationDate(hoursFromNow(72));
        assertFalse(order.isCancellationPenaltyIncurred(48));

        order.setRequestedInstallationDate(hoursFromNow(24));
        assertTrue(order.isCancellationPenaltyIncurred(48));
    }

    @Test
    public void withNoInstallationDateThereIsNoPenalty() {
        assertFalse(order.isCancellationPenaltyIncurred(48));
    }

    @Test
    public void theScheduledInstallationOnTheAssetWinsOverTheRequestedDate() {
        final Installation installation = new Installation();
        installation.setScheduledDate(hoursFromNow(2));
        final Asset asset = new Asset();
        asset.setInstallation(installation);
        order.setAsset(asset);
        order.setRequestedInstallationDate(hoursFromNow(400));

        assertTrue(order.isCancellationPenaltyIncurred(48));
    }

    @Test
    public void anInstalledOrderIsNoLongerCancellable() {
        assertTrue(order.isCancellable());
        order.setOrderStatusType(OrderStatusType.INSTALLED);
        assertFalse(order.isCancellable());
    }
}
