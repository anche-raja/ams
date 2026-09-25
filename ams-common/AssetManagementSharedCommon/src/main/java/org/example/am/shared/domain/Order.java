package org.example.am.shared.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A hardware order. Carries the shipping destination, the three contacts the fulfilment process
 * needs, the compliance sign-off, and everything the device is staged with: its nickname, the
 * weekly maintenance window, the external WAN/LAN configuration and the subscriber machines that
 * will sit behind it.
 *
 * <p>The staging data is carried on the order rather than on the asset because for a new order
 * there is no asset yet - it is created when the device is despatched - and the warehouse needs
 * the configuration before that happens.
 */
public class Order extends BaseDomain {

    private static final long serialVersionUID = 1L;

    /** Business days added on top of the base lead time when the order ships internationally. */
    private static final int INTERNATIONAL_LEAD_TIME_PENALTY_DAYS = 10;

    /** Base lead times, in business days, by order type. */
    private static final int STANDARD_LEAD_TIME_DAYS = 10;
    private static final int MIGRATION_LEAD_TIME_DAYS = 15;
    private static final int EMERGENCY_LEAD_TIME_DAYS = 2;

    private Long orderId;
    private String orderNumber;
    private OrderType orderType;
    private OrderStatusType orderStatusType;

    private Asset asset;
    private Asset assetBeingReplaced;
    private Address shippingAddress;
    private Contact orderingContact;
    private Contact shippingContact;
    private Contact installationContact;
    private ShippingCarrier shippingCarrier;
    private DueDiligence dueDiligence;

    private String trackingNumber;
    private Date submittedDate;
    private Date shippedDate;
    private Date requestedInstallationDate;
    private Date cancelledDate;
    private String cancellationReason;
    private boolean cancelledWithPenalty;
    private Long customerId;
    private String comments;

    /** Customer's own name for the device, printed on the despatch note and shown in search. */
    private String deviceNickname;
    private MaintenanceWindow maintenanceWindow;
    private AssetConfiguration assetConfiguration;
    private List<SubscriberPc> subscriberPcs = new ArrayList<SubscriberPc>();
    private Timeslot shippingWindow;

    /** The on-site visit an install order books. Carries the chosen timeslot until it is placed. */
    private Installation installation;

    /*
     * Foreign keys, held alongside the objects they point at rather than instead of them.
     *
     * There is no ORM here: the row mapper can only see the id columns, and hydrating six
     * associations on every order read would turn one query into seven. So the mapper fills these
     * in, and the service replaces them with real objects when a caller actually needs them. The
     * object setters below keep the matching id in step so the two can never disagree.
     */
    private Long shippingWindowTimeslotId;
    private Long shipAddressId;
    private Long orderingContactId;
    private Long shippingContactId;
    private Long installationContactId;
    private Long maintenanceWindowId;
    private Long configurationId;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(final Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(final String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(final OrderType orderType) {
        this.orderType = orderType;
    }

    public OrderStatusType getOrderStatusType() {
        return orderStatusType;
    }

    public void setOrderStatusType(final OrderStatusType orderStatusType) {
        this.orderStatusType = orderStatusType;
    }

    public Asset getAsset() {
        return asset;
    }

    public void setAsset(final Asset asset) {
        this.asset = asset;
    }

    public Asset getAssetBeingReplaced() {
        return assetBeingReplaced;
    }

    public void setAssetBeingReplaced(final Asset assetBeingReplaced) {
        this.assetBeingReplaced = assetBeingReplaced;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(final Address shippingAddress) {
        this.shippingAddress = shippingAddress;
        if (shippingAddress != null) {
            this.shipAddressId = shippingAddress.getAddressId();
        }
    }

    public Contact getOrderingContact() {
        return orderingContact;
    }

    public void setOrderingContact(final Contact orderingContact) {
        this.orderingContact = orderingContact;
        if (orderingContact != null) {
            this.orderingContactId = orderingContact.getContactId();
        }
    }

    public Contact getShippingContact() {
        return shippingContact;
    }

    public void setShippingContact(final Contact shippingContact) {
        this.shippingContact = shippingContact;
        if (shippingContact != null) {
            this.shippingContactId = shippingContact.getContactId();
        }
    }

    public Contact getInstallationContact() {
        return installationContact;
    }

    public void setInstallationContact(final Contact installationContact) {
        this.installationContact = installationContact;
        if (installationContact != null) {
            this.installationContactId = installationContact.getContactId();
        }
    }

    public ShippingCarrier getShippingCarrier() {
        return shippingCarrier;
    }

    public void setShippingCarrier(final ShippingCarrier shippingCarrier) {
        this.shippingCarrier = shippingCarrier;
    }

    public DueDiligence getDueDiligence() {
        return dueDiligence;
    }

    public void setDueDiligence(final DueDiligence dueDiligence) {
        this.dueDiligence = dueDiligence;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(final String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Date getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(final Date submittedDate) {
        this.submittedDate = submittedDate;
    }

    public Date getShippedDate() {
        return shippedDate;
    }

    public void setShippedDate(final Date shippedDate) {
        this.shippedDate = shippedDate;
    }

    public Date getRequestedInstallationDate() {
        return requestedInstallationDate;
    }

    public void setRequestedInstallationDate(final Date requestedInstallationDate) {
        this.requestedInstallationDate = requestedInstallationDate;
    }

    public Date getCancelledDate() {
        return cancelledDate;
    }

    public void setCancelledDate(final Date cancelledDate) {
        this.cancelledDate = cancelledDate;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(final String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public boolean isCancelledWithPenalty() {
        return cancelledWithPenalty;
    }

    public void setCancelledWithPenalty(final boolean cancelledWithPenalty) {
        this.cancelledWithPenalty = cancelledWithPenalty;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long customerId) {
        this.customerId = customerId;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    // ------------------------------------------------------------------
    // Business rules
    // ------------------------------------------------------------------

    /**
     * @return {@code true} while the order is still working its way through fulfilment, i.e. it
     *         blocks other actions against the asset.
     */
    public boolean isOpen() {
        if (orderStatusType == null) {
            return false;
        }
        return !OrderStatusType.COMPLETED.equals(orderStatusType)
                && !OrderStatusType.CANCELLED.equals(orderStatusType);
    }

    public boolean isSubmitted() {
        return submittedDate != null && isOpen();
    }

    /**
     * @return the number of business days between submission and the earliest installation date we
     *         will offer. Emergency replacements are expedited; migrations need the extra window
     *         for the legacy circuit to be groomed; anything leaving the country adds a fixed
     *         customs allowance.
     */
    public int getInstallLeadTimeDays() {
        int leadTime = STANDARD_LEAD_TIME_DAYS;
        if (OrderType.EMERGENCY_REPLACEMENT.equals(orderType)) {
            leadTime = EMERGENCY_LEAD_TIME_DAYS;
        } else if (isMigrateOrder()) {
            leadTime = MIGRATION_LEAD_TIME_DAYS;
        }
        if (shippingAddress != null && !shippingAddress.isDomestic()) {
            leadTime += INTERNATIONAL_LEAD_TIME_PENALTY_DAYS;
        }
        return leadTime;
    }

    public boolean isMigrateOrder() {
        return OrderType.MIGRATION.equals(orderType);
    }

    public boolean isEmergencyReplacement() {
        return OrderType.EMERGENCY_REPLACEMENT.equals(orderType);
    }

    /**
     * A migration order is only valid while the asset it is migrating away from stays eligible.
     * The select-asset and review screens both re-check this, because the asset can pick up a
     * pending change between the two steps.
     *
     * @return the first blocking problem, or {@code null} when the migration may proceed.
     */
    public AssetProblemType getAssetProblemForMigratingOrder() {
        if (!isMigrateOrder()) {
            return null;
        }
        final Asset source = assetBeingReplaced == null ? asset : assetBeingReplaced;
        if (source == null) {
            return AssetProblemType.NOT_MIGRATABLE;
        }
        for (final AssetProblemType problem : source.getMigrationProblems()) {
            return problem;
        }
        return null;
    }

    /**
     * Cancellation is free until the penalty window closes ahead of the scheduled installation.
     *
     * @param minimumHoursBeforeInstallation window taken from
     *        {@link PropertyType#MIN_HOURS_BEFORE_INSTALLATION_TO_CANCEL_ORDER_WITHOUT_PENALTY}
     * @return {@code true} when cancelling now would incur the cancellation charge
     */
    public boolean isCancellationPenaltyIncurred(final int minimumHoursBeforeInstallation) {
        final Date installationDate = getScheduledInstallationDate();
        if (installationDate == null) {
            return false;
        }
        final long cutoff = installationDate.getTime()
                - (minimumHoursBeforeInstallation * 60L * 60L * 1000L);
        return getCurrentTime().getTime() > cutoff;
    }

    public Date getScheduledInstallationDate() {
        if (asset != null && asset.getInstallation() != null) {
            return asset.getInstallation().getScheduledDate();
        }
        return requestedInstallationDate;
    }

    public boolean isCancellable() {
        return isOpen() && !OrderStatusType.INSTALLED.equals(orderStatusType);
    }

    public String getDeviceNickname() {
        return deviceNickname;
    }

    public void setDeviceNickname(final String deviceNickname) {
        this.deviceNickname = deviceNickname;
    }

    public MaintenanceWindow getMaintenanceWindow() {
        return maintenanceWindow;
    }

    public void setMaintenanceWindow(final MaintenanceWindow maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
        if (maintenanceWindow != null) {
            this.maintenanceWindowId = maintenanceWindow.getMaintenanceWindowId();
        }
    }

    public AssetConfiguration getAssetConfiguration() {
        return assetConfiguration;
    }

    public void setAssetConfiguration(final AssetConfiguration assetConfiguration) {
        this.assetConfiguration = assetConfiguration;
        if (assetConfiguration != null) {
            this.configurationId = assetConfiguration.getConfigurationId();
        }
    }

    public List<SubscriberPc> getSubscriberPcs() {
        return subscriberPcs;
    }

    public void setSubscriberPcs(final List<SubscriberPc> subscriberPcs) {
        this.subscriberPcs = subscriberPcs == null
                ? new ArrayList<SubscriberPc>() : subscriberPcs;
    }

    public Timeslot getShippingWindow() {
        return shippingWindow;
    }

    /** Keeps the id in step, so callers that only persist the key do not have to unpack the slot. */
    public void setShippingWindow(final Timeslot shippingWindow) {
        this.shippingWindow = shippingWindow;
        if (shippingWindow != null) {
            this.shippingWindowTimeslotId = shippingWindow.getTimeslotId();
        }
    }

    public Long getShipAddressId() {
        return shipAddressId;
    }

    public void setShipAddressId(final Long shipAddressId) {
        this.shipAddressId = shipAddressId;
    }

    public Long getOrderingContactId() {
        return orderingContactId;
    }

    public void setOrderingContactId(final Long orderingContactId) {
        this.orderingContactId = orderingContactId;
    }

    public Long getShippingContactId() {
        return shippingContactId;
    }

    public void setShippingContactId(final Long shippingContactId) {
        this.shippingContactId = shippingContactId;
    }

    public Long getInstallationContactId() {
        return installationContactId;
    }

    public void setInstallationContactId(final Long installationContactId) {
        this.installationContactId = installationContactId;
    }

    public Long getMaintenanceWindowId() {
        return maintenanceWindowId;
    }

    public void setMaintenanceWindowId(final Long maintenanceWindowId) {
        this.maintenanceWindowId = maintenanceWindowId;
    }

    public Long getConfigurationId() {
        return configurationId;
    }

    public void setConfigurationId(final Long configurationId) {
        this.configurationId = configurationId;
    }

    public Long getShippingWindowTimeslotId() {
        return shippingWindowTimeslotId;
    }

    public void setShippingWindowTimeslotId(final Long shippingWindowTimeslotId) {
        this.shippingWindowTimeslotId = shippingWindowTimeslotId;
    }

    /**
     * @return the subscriber machines that were actually keyed
     *
     * <p>The capture form submits a fixed grid of rows, so the collection arrives padded with
     * untouched ones. Everything downstream - the count, the persistence, the despatch note -
     * wants only the real ones.</p>
     */
    public List<SubscriberPc> getPopulatedSubscriberPcs() {
        final List<SubscriberPc> populated = new ArrayList<SubscriberPc>();
        for (final SubscriberPc pc : subscriberPcs) {
            if (pc != null && !pc.isBlank()) {
                populated.add(pc);
            }
        }
        return populated;
    }

    /** @return how many people the site expects to put behind the device */
    public int getTotalSubscriberSeats() {
        int seats = 0;
        for (final SubscriberPc pc : getPopulatedSubscriberPcs()) {
            seats += pc.getUserCount() == null ? 1 : pc.getUserCount().intValue();
        }
        return seats;
    }

    public Installation getInstallation() {
        return installation;
    }

    public void setInstallation(final Installation installation) {
        this.installation = installation;
    }

    /** @return {@code true} when the order holds a booked installation timeslot */
    public boolean isInstallationScheduled() {
        return installation != null && installation.getTimeslot() != null
                && installation.getTimeslot().getTimeslotId() != null;
    }

    @Override
    public String toString() {
        return "Order[" + orderId + ", number=" + orderNumber + ", status=" + orderStatusType + "]";
    }
}
