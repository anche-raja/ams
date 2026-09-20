package org.example.am.shared.service.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.example.am.shared.dao.AssetConfigDAO;
import org.example.am.shared.dao.AssetDAO;
import org.example.am.shared.dao.InstallationDAO;
import org.example.am.shared.dao.MaintenanceWindowDAO;
import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.dao.RequestDAO;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.Asset;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.AssetStatusType;
import org.example.am.shared.domain.AssetType;
import org.example.am.shared.domain.EmailEntityType;
import org.example.am.shared.domain.EventType;
import org.example.am.shared.domain.Installation;
import org.example.am.shared.domain.MaintenanceWindow;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.service.ProvisioningService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** @see ProvisioningService */
@Service("provisioningService")
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class ProvisioningServiceImpl implements ProvisioningService {

    private static final Logger LOGGER = LogManager.getLogger(ProvisioningServiceImpl.class);

    /** Stamped into the despatch note so operations can read it back off the event. */
    private static final String DESPATCH_DATE_PATTERN = "dd MMM yyyy";

    @Autowired
    private OrderDAO orderDAO;

    @Autowired
    private AssetDAO assetDAO;

    @Autowired
    private InstallationDAO installationDAO;

    @Autowired
    private AssetConfigDAO assetConfigDAO;

    @Autowired
    private MaintenanceWindowDAO maintenanceWindowDAO;

    @Autowired
    private RequestDAO requestDAO;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public Asset despatchOrder(final long customerId, final long orderId,
            final AssetType assetType, final String trackingNumber, final String userId) {
        final Order order = orderDAO.getOrder(customerId, orderId);
        if (order == null) {
            throw new IllegalStateException("Order " + orderId + " does not belong to customer "
                    + customerId);
        }
        if (!OrderStatusType.SUBMITTED.equals(order.getOrderStatusType())) {
            throw new IllegalStateException("Order " + order.getOrderNumber()
                    + " is " + describe(order.getOrderStatusType()) + " and cannot be despatched.");
        }
        if (order.getAsset() != null && order.getAsset().getAssetId() != null) {
            throw new IllegalStateException("Order " + order.getOrderNumber()
                    + " already has an asset against it.");
        }

        final Asset asset = new Asset();
        asset.setAssetType(assetType);
        asset.setAssetStatusType(AssetStatusType.SHIPPED);
        // The id rather than the object: getOrderDetail hydrates the address lazily and the graph
        // may not carry it, but the id is on the order row itself and is always there.
        if (order.getShipAddressId() != null) {
            final Address installationAddress = new Address();
            installationAddress.setAddressId(order.getShipAddressId());
            asset.setInstallationAddress(installationAddress);
        }
        final long assetId = assetDAO.insertAsset(asset, customerId, userId);

        // The configuration and the maintenance window were written against the order because there
        // was no device to hang them on. There is one now.
        final AssetConfiguration configuration = assetConfigDAO.getConfigurationForOrder(orderId);
        if (configuration != null && configuration.getConfigurationId() != null) {
            assetConfigDAO.attachConfigurationToAsset(
                    configuration.getConfigurationId().longValue(), assetId, userId);
        }
        final MaintenanceWindow window = maintenanceWindowDAO.getMaintenanceWindowForOrder(orderId);
        if (window != null && window.getMaintenanceWindowId() != null) {
            maintenanceWindowDAO.attachWindowToAsset(
                    window.getMaintenanceWindowId().longValue(), assetId, userId);
        }

        // Raised unscheduled: the calendar books it separately, which is what moves the order to
        // SCHEDULED through the timeslot scheduling DAO.
        installationDAO.insertInstallation(orderId, assetId,
                order.getShipAddressId(), order.getInstallationContactId(), userId);

        orderDAO.markDespatched(orderId, assetId,
                StringUtils.isBlank(trackingNumber) ? null : trackingNumber.trim(), userId);

        requestDAO.recordEvent(EventType.ASSET_DESPATCHED, EmailEntityType.ORDER, orderId,
                "Order " + order.getOrderNumber() + " despatched on " + formatDespatchDate()
                        + " as asset " + asset.getAssetTag(), userId);
        LOGGER.info("Despatched order {} as asset {} ({})", order.getOrderNumber(),
                Long.valueOf(assetId), asset.getAssetTag());
        return asset;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean completeInstallation(final long customerId, final long orderId,
            final String technicianName, final String notes, final String userId) {
        final Order order = orderDAO.getOrder(customerId, orderId);
        if (order == null || order.getAsset() == null || order.getAsset().getAssetId() == null) {
            throw new IllegalStateException("Order " + orderId
                    + " has not been despatched, so there is no visit to complete.");
        }
        final Installation installation = installationDAO.getInstallationForOrder(orderId);
        if (installation == null || installation.getInstallationId() == null) {
            throw new IllegalStateException("Order " + order.getOrderNumber()
                    + " has no installation record.");
        }

        final int updated = installationDAO.completeInstallation(
                installation.getInstallationId().longValue(), technicianName, notes,
                currentTime(), userId);
        if (updated == 0) {
            LOGGER.info("Installation for order {} was already closed; nothing completed",
                    order.getOrderNumber());
            return false;
        }

        final long assetId = order.getAsset().getAssetId().longValue();
        assetDAO.updateAssetStatus(customerId, assetId, AssetStatusType.ACTIVE, userId);

        final AssetConfiguration configuration = assetConfigDAO.getCurrentConfiguration(assetId);
        if (configuration != null && configuration.getConfigurationId() != null) {
            assetConfigDAO.markApplied(configuration.getConfigurationId().longValue(), userId);
        }
        orderDAO.updateOrderStatus(orderId, OrderStatusType.COMPLETED, userId);

        requestDAO.recordEvent(EventType.ASSET_INSTALLED, EmailEntityType.ORDER, orderId,
                "Installation completed for order " + order.getOrderNumber(), userId);
        LOGGER.info("Completed installation for order {}; asset {} is live",
                order.getOrderNumber(), Long.valueOf(assetId));
        return true;
    }

    /**
     * A new SimpleDateFormat per call. The class is mutable and not thread safe, so a shared static
     * instance would corrupt its own output under concurrent despatches.
     */
    private String formatDespatchDate() {
        return new SimpleDateFormat(DESPATCH_DATE_PATTERN).format(currentTime());
    }

    private Date currentTime() {
        return Calendar.getInstance().getTime();
    }

    private static String describe(final OrderStatusType status) {
        return status == null ? "in an unknown state" : status.getDescription();
    }
}
