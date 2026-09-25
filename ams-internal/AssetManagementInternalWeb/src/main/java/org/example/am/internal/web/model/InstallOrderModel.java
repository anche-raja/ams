package org.example.am.internal.web.model;

import java.io.Serializable;

import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.AssetConfigurationType;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.CountryType;
import org.example.am.shared.domain.Installation;
import org.example.am.shared.domain.NetworkConfigurationType;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderType;
import org.example.am.shared.domain.StateType;
import org.example.am.shared.domain.Timeslot;

/**
 * The form backing the install order flow.
 *
 * <p>Three screens - site, device, appointment - so the partly completed order is carried in the
 * session as this model rather than being persisted at each step: an abandoned order should leave
 * nothing behind. {@link #toOrder()} turns it into the domain object only when it is placed.</p>
 */
public class InstallOrderModel implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The three steps, in order; the step indicator and the skipped-step checks both use these. */
    public static final int STEP_SITE = 1;
    public static final int STEP_DEVICE = 2;
    public static final int STEP_APPOINTMENT = 3;

    private Long customerId;

    /** One address serves as the installation site; the device is delivered where it is fitted. */
    private Address siteAddress = new Address();
    private Contact siteContact = new Contact();

    /** Set by the address validation interceptor when it has a better address to offer. */
    private Address suggestedAddress;
    private boolean addressSuggestionAccepted;

    /** Set by the address validation interceptor when the check could not be made at all. */
    private boolean addressCheckUnavailable;

    /** The customer's own name for the device, e.g. "Front desk router". */
    private String deviceNickname;
    private AssetConfiguration assetConfiguration = new AssetConfiguration();

    /** Set only once the device step has passed the WAN and LAN validators. */
    private boolean deviceConfirmed;

    private Long installationTimeslotId;
    private Timeslot installationSlot;
    private String comments;

    private int furthestStepReached = STEP_SITE;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(final Long customerId) {
        this.customerId = customerId;
    }

    public Address getSiteAddress() {
        return siteAddress;
    }

    public void setSiteAddress(final Address siteAddress) {
        this.siteAddress = siteAddress == null ? new Address() : siteAddress;
    }

    public Contact getSiteContact() {
        return siteContact;
    }

    public void setSiteContact(final Contact siteContact) {
        this.siteContact = siteContact == null ? new Contact() : siteContact;
    }

    public Address getSuggestedAddress() {
        return suggestedAddress;
    }

    public void setSuggestedAddress(final Address suggestedAddress) {
        this.suggestedAddress = suggestedAddress;
    }

    public boolean isAddressSuggestionAccepted() {
        return addressSuggestionAccepted;
    }

    public void setAddressSuggestionAccepted(final boolean addressSuggestionAccepted) {
        this.addressSuggestionAccepted = addressSuggestionAccepted;
    }

    public boolean isAddressCheckUnavailable() {
        return addressCheckUnavailable;
    }

    public void setAddressCheckUnavailable(final boolean addressCheckUnavailable) {
        this.addressCheckUnavailable = addressCheckUnavailable;
    }

    public String getDeviceNickname() {
        return deviceNickname;
    }

    public void setDeviceNickname(final String deviceNickname) {
        this.deviceNickname = deviceNickname;
    }

    public AssetConfiguration getAssetConfiguration() {
        return assetConfiguration;
    }

    public void setAssetConfiguration(final AssetConfiguration assetConfiguration) {
        this.assetConfiguration = assetConfiguration == null
                ? new AssetConfiguration() : assetConfiguration;
    }

    public boolean isDeviceConfirmed() {
        return deviceConfirmed;
    }

    public void setDeviceConfirmed(final boolean deviceConfirmed) {
        this.deviceConfirmed = deviceConfirmed;
    }

    public Long getInstallationTimeslotId() {
        return installationTimeslotId;
    }

    public void setInstallationTimeslotId(final Long installationTimeslotId) {
        this.installationTimeslotId = installationTimeslotId;
    }

    public Timeslot getInstallationSlot() {
        return installationSlot;
    }

    public void setInstallationSlot(final Timeslot installationSlot) {
        this.installationSlot = installationSlot;
        this.installationTimeslotId = installationSlot == null ? null : installationSlot.getTimeslotId();
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public int getFurthestStepReached() {
        return furthestStepReached;
    }

    public void setFurthestStepReached(final int furthestStepReached) {
        this.furthestStepReached = furthestStepReached;
    }

    /** Records that a step has been reached, without ever moving the marker backwards. */
    public void reachStep(final int step) {
        if (step > furthestStepReached) {
            furthestStepReached = step;
        }
    }

    /*
     * Code accessors for the drop-downs.
     *
     * Every one of these types is a legacy typesafe enum with a private constructor, so a form
     * cannot bind to a nested "state.code": OGNL would have to construct one and cannot, and the
     * failure is silent - the field simply stays null. Binding to a plain code and looking the
     * instance up here keeps the lookup in one place rather than in each action.
     */

    public String getStateCode() {
        return siteAddress.getState() == null ? null : siteAddress.getState().getCode();
    }

    public void setStateCode(final String stateCode) {
        siteAddress.setState(StateType.lookup(stateCode));
    }

    public String getCountryCode() {
        return siteAddress.getCountry() == null ? null : siteAddress.getCountry().getCode();
    }

    public void setCountryCode(final String countryCode) {
        siteAddress.setCountry(CountryType.lookup(countryCode));
    }

    public String getNetworkConfigurationCode() {
        return assetConfiguration.getNetworkConfigurationType() == null
                ? null : assetConfiguration.getNetworkConfigurationType().getCode();
    }

    public void setNetworkConfigurationCode(final String networkConfigurationCode) {
        assetConfiguration.setNetworkConfigurationType(
                NetworkConfigurationType.lookup(networkConfigurationCode));
    }

    public String getConfigurationTypeCode() {
        return assetConfiguration.getAssetConfigurationType() == null
                ? null : assetConfiguration.getAssetConfigurationType().getCode();
    }

    public void setConfigurationTypeCode(final String configurationTypeCode) {
        assetConfiguration.setAssetConfigurationType(
                AssetConfigurationType.lookup(configurationTypeCode));
    }

    /**
     * Builds the domain object the service layer persists. Only called when the order is placed:
     * everything before that is form state.
     */
    public Order toOrder() {
        final Order order = new Order();
        order.setCustomerId(customerId);
        order.setOrderType(OrderType.NEW_INSTALL);
        order.setShippingAddress(siteAddress);
        order.setInstallationContact(siteContact);
        order.setDeviceNickname(deviceNickname);
        order.setAssetConfiguration(assetConfiguration);
        order.setComments(comments);

        final Installation installation = new Installation();
        installation.setTimeslot(installationSlot);
        installation.setInstallationAddress(siteAddress);
        installation.setInstallationContact(siteContact);
        order.setInstallation(installation);
        return order;
    }
}
