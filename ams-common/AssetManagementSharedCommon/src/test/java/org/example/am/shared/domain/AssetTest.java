package org.example.am.shared.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class AssetTest {

    private Asset asset;

    @Before
    public void setUp() {
        asset = new Asset();
        asset.setAssetId(Long.valueOf(1L));
        asset.setAssetTag("AMS-000501");
        asset.setAssetStatusType(AssetStatusType.ACTIVE);
        asset.setMigratable(true);
    }

    private static Order openOrder() {
        final Order order = new Order();
        order.setOrderStatusType(OrderStatusType.SUBMITTED);
        return order;
    }

    private static NetworkChangeRequest openRequest() {
        final NetworkChangeRequest request = new NetworkChangeRequest();
        request.setNetworkChangeRequestStatusType(NetworkChangeRequestStatusType.SUBMITTED);
        return request;
    }

    private static Decommission scheduledDecommission() {
        final Decommission decommission = new Decommission();
        decommission.setDecommissionStatusType(DecommissionStatusType.SCHEDULED);
        return decommission;
    }

    @Test
    public void anAssetInServiceWithNothingInFlightCanDoEverything() {
        assertTrue(asset.isInService());
        assertTrue(asset.isCanDecommission());
        assertTrue(asset.isCanMove());
        assertTrue(asset.isCanModifyConfig());
    }

    @Test
    public void anAssetNotYetInstalledCanDoNothing() {
        asset.setAssetStatusType(AssetStatusType.ORDERED);
        assertFalse(asset.isInService());
        assertFalse(asset.isCanDecommission());
        assertFalse(asset.isCanMove());
        assertFalse(asset.isCanModifyConfig());
    }

    @Test
    public void anOpenOrderBlocksEverythingButConfigurationEdits() {
        asset.setOrder(openOrder());
        assertFalse(asset.isCanDecommission());
        assertFalse(asset.isCanMove());
        assertTrue(asset.isCanModifyConfig());
    }

    @Test
    public void aClosedOrderBlocksNothing() {
        final Order order = new Order();
        order.setOrderStatusType(OrderStatusType.COMPLETED);
        asset.setOrder(order);
        assertTrue(asset.isCanDecommission());
        assertTrue(asset.isCanMove());
    }

    @Test
    public void anOpenChangeRequestBlocksAllThree() {
        asset.setNetworkChangeRequest(openRequest());
        assertFalse(asset.isCanDecommission());
        assertFalse(asset.isCanMove());
        assertFalse(asset.isCanModifyConfig());
    }

    @Test
    public void aScheduledDecommissionBlocksAllThree() {
        asset.setDecommission(scheduledDecommission());
        assertFalse(asset.isCanDecommission());
        assertFalse(asset.isCanMove());
        assertFalse(asset.isCanModifyConfig());
    }

    @Test
    public void aCancelledDecommissionDoesNotBlockANewOne() {
        final Decommission decommission = new Decommission();
        decommission.setDecommissionStatusType(DecommissionStatusType.CANCELLED);
        asset.setDecommission(decommission);
        assertTrue(asset.isCanDecommission());
    }

    /**
     * A mismatched configuration stops a move, because the device is not in the state AMS thinks it
     * is - but resolving that mismatch is itself a configuration edit, so that stays open.
     */
    @Test
    public void aMismatchedConfigurationBlocksAMoveButNotAConfigurationEdit() {
        final AssetConfiguration configuration = new AssetConfiguration();
        configuration.setAssetConfigurationStatusType(AssetConfigurationStatusType.MISMATCH);
        asset.setAssetConfiguration(configuration);

        assertFalse(asset.isCanMove());
        assertTrue(asset.isCanModifyConfig());
        assertTrue(asset.isCanDecommission());
    }

    @Test
    public void migrationProblemsListEveryBlockingReason() {
        asset.setMigratable(false);
        asset.setOrder(openOrder());
        asset.setNetworkChangeRequest(openRequest());
        asset.setDecommission(scheduledDecommission());

        final List<AssetProblemType> problems = asset.getMigrationProblems();
        assertEquals(4, problems.size());
        assertTrue(problems.contains(AssetProblemType.NOT_MIGRATABLE));
        assertTrue(problems.contains(AssetProblemType.PENDING_ORDER));
        assertTrue(problems.contains(AssetProblemType.PENDING_NCR));
        assertTrue(problems.contains(AssetProblemType.DECOMMISSION_SCHEDULED));
    }

    @Test
    public void aCustomerWithNoActiveServiceIsAMigrationProblem() {
        asset.setCustomer(new Customer());
        assertTrue(asset.getMigrationProblems().contains(AssetProblemType.NO_ACTIVE_SERVICE));
    }

    @Test
    public void aCleanAssetHasNoMigrationProblems() {
        assertTrue(asset.getMigrationProblems().isEmpty());
    }

    @Test
    public void inactivePortsAreDroppedBackToAutoNegotiate() {
        final PortConfiguration active = new PortConfiguration();
        active.setActive(true);
        active.setPortConfigurationType(PortConfigurationType.FULL_1000);
        final PortConfiguration inactive = new PortConfiguration();
        inactive.setActive(false);
        inactive.setPortConfigurationType(PortConfigurationType.FULL_100);

        final List<PortConfiguration> ports = new ArrayList<PortConfiguration>();
        ports.add(active);
        ports.add(inactive);
        final AssetConfiguration configuration = new AssetConfiguration();
        configuration.setPortConfigurations(ports);
        asset.setAssetConfiguration(configuration);

        asset.setInactivePortConfigurationTypesToAuto();

        assertEquals(PortConfigurationType.FULL_1000, active.getPortConfigurationType());
        assertEquals(PortConfigurationType.AUTO, inactive.getPortConfigurationType());
    }

    @Test
    public void clearingMacAddressesToleratesAMissingConfiguration() {
        asset.clearAllMacAddresses();
        asset.setInactivePortConfigurationTypesToAuto();

        final PortConfiguration port = new PortConfiguration();
        port.setMacAddress("00:11:22:33:44:55");
        final List<PortConfiguration> ports = new ArrayList<PortConfiguration>();
        ports.add(port);
        final AssetConfiguration configuration = new AssetConfiguration();
        configuration.setPortConfigurations(ports);
        asset.setAssetConfiguration(configuration);

        asset.clearAllMacAddresses();
        assertNull(port.getMacAddress());
    }

    @Test
    public void displayTagFallsBackToTheLegacyTag() {
        assertEquals("AMS-000501", asset.getDisplayTag());
        asset.setAssetTag(null);
        asset.setLegacyAssetTag("NW-9");
        assertEquals("NW-9", asset.getDisplayTag());
        asset.setAssetTag("   ");
        assertEquals("NW-9", asset.getDisplayTag());
    }
}
