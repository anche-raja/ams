package org.example.am.internal.web.action;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.network.validation.LanTypeAValidator;
import org.example.am.network.validation.LanTypeBValidator;
import org.example.am.network.validation.LanTypeCValidator;
import org.example.am.network.validation.LanValidator;
import org.example.am.network.validation.WanValidator;
import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.AssetConfigurationStatusType;
import org.example.am.shared.domain.AssetConfigurationType;
import org.example.am.shared.domain.NetworkConfigurationType;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * Step 2 of the install order: what the device is called and how it is addressed.
 *
 * <p>Both sides of the network are keyed now rather than by the engineer on the day, because the
 * device is staged in the warehouse from this configuration and arrives already addressed. That is
 * also why the validators run here: a transposed digit found on site is a wasted visit.</p>
 *
 * <p>DHCP and PPPoE are the exception on the WAN side: the carrier assigns the address at
 * connection time, so there is nothing to key and nothing to validate.</p>
 */
@Component("InstallDeviceAction")
@Scope("prototype")
public class InstallDeviceAction extends InstallOrderBaseAction {

    private static final long serialVersionUID = 1L;

    private static final int MAX_NICKNAME_LENGTH = 60;

    private static final WanValidator WAN_VALIDATOR = new WanValidator();

    /** Which LAN validator applies is decided by the LAN type the user picks. */
    private static final LanValidator LAN_TYPE_A = new LanTypeAValidator();
    private static final LanValidator LAN_TYPE_B = new LanTypeBValidator();
    private static final LanValidator LAN_TYPE_C = new LanTypeCValidator();

    /** Only the addressing modes a new single-WAN install is offered. */
    private static final List<NetworkConfigurationType> WAN_OPTIONS = Arrays.asList(
            NetworkConfigurationType.STATIC, NetworkConfigurationType.DHCP,
            NetworkConfigurationType.PPPOE);

    /** Only the LAN types that have a validator; the others describe multi-WAN sites. */
    private static final List<AssetConfigurationType> LAN_OPTIONS = Arrays.asList(
            AssetConfigurationType.LAN_TYPE_A, AssetConfigurationType.LAN_TYPE_B,
            AssetConfigurationType.LAN_TYPE_C);

    public String initDevice() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkStepReached(model, InstallOrderModel.STEP_DEVICE);
        if (stop != null) {
            return stop;
        }
        final AssetConfiguration configuration = model.getAssetConfiguration();
        if (configuration.getNetworkConfigurationType() == null) {
            configuration.setNetworkConfigurationType(NetworkConfigurationType.STATIC);
        }
        if (configuration.getAssetConfigurationType() == null) {
            configuration.setAssetConfigurationType(AssetConfigurationType.LAN_TYPE_A);
        }
        storeModel(model);
        return Action.SUCCESS;
    }

    public String saveDevice() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkStepReached(model, InstallOrderModel.STEP_DEVICE);
        if (stop != null) {
            return stop;
        }
        // Cleared first so that a failed save cannot leave an earlier pass marked as good.
        model.setDeviceConfirmed(false);
        final AssetConfiguration configuration = model.getAssetConfiguration();
        boolean valid = validateNickname(model);
        valid &= validateWan(configuration);
        valid &= validateLan(configuration);
        if (!valid) {
            storeModel(model);
            return Action.INPUT;
        }
        // The revision the warehouse stages from has not been applied to anything yet, and will
        // not be until the engineer confirms it on site.
        configuration.setAssetConfigurationStatusType(AssetConfigurationStatusType.PENDING);
        model.setDeviceConfirmed(true);
        model.reachStep(InstallOrderModel.STEP_APPOINTMENT);
        storeModel(model);
        return Action.SUCCESS;
    }

    public Collection<NetworkConfigurationType> getNetworkConfigurationOptions() {
        return WAN_OPTIONS;
    }

    public Collection<AssetConfigurationType> getConfigurationTypeOptions() {
        return LAN_OPTIONS;
    }

    private boolean validateNickname(final InstallOrderModel model) {
        final String nickname = StringUtils.trimToNull(model.getDeviceNickname());
        if (nickname == null) {
            addFieldErrorAndLog("deviceNickname", "Give the device a nickname.");
            return false;
        }
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            addFieldErrorAndLog("deviceNickname",
                    "The nickname must be " + MAX_NICKNAME_LENGTH + " characters or fewer.");
            return false;
        }
        model.setDeviceNickname(nickname);
        return true;
    }

    private boolean validateWan(final AssetConfiguration configuration) {
        final NetworkConfigurationType type = configuration.getNetworkConfigurationType();
        if (!WAN_OPTIONS.contains(type)) {
            addFieldErrorAndLog("assetConfiguration.wanIpAddress", "Choose how the WAN is addressed.");
            return false;
        }
        if (!NetworkConfigurationType.STATIC.equals(type)) {
            // Clear anything left from a previous pass, so a switch from static to DHCP does not
            // ship a device configured with both.
            configuration.setWanIpAddress(null);
            configuration.setWanSubnetMask(null);
            configuration.setDefaultGateway(null);
            return true;
        }
        return report(WAN_VALIDATOR.validate(configuration.getWanIpAddress(),
                configuration.getWanSubnetMask(), configuration.getDefaultGateway(),
                configuration.getPrimaryDnsAddress(), configuration.getSecondaryDnsAddress()),
                "assetConfiguration.wanIpAddress");
    }

    /**
     * The LAN gateway is the customer's own router inside the site, not the device being ordered;
     * the validators reject the two being the same address.
     */
    private boolean validateLan(final AssetConfiguration configuration) {
        final LanValidator validator = lanValidatorFor(configuration.getAssetConfigurationType());
        if (validator == null) {
            addFieldErrorAndLog("assetConfiguration.lanIpAddress", "Choose a LAN type.");
            return false;
        }
        return report(validator.validate(configuration.getLanIpAddress(),
                configuration.getLanSubnetMask(), configuration.getLanGateway()),
                "assetConfiguration.lanIpAddress");
    }

    private static LanValidator lanValidatorFor(final AssetConfigurationType type) {
        if (AssetConfigurationType.LAN_TYPE_A.equals(type)) {
            return LAN_TYPE_A;
        }
        if (AssetConfigurationType.LAN_TYPE_B.equals(type)) {
            return LAN_TYPE_B;
        }
        if (AssetConfigurationType.LAN_TYPE_C.equals(type)) {
            return LAN_TYPE_C;
        }
        return null;
    }

    /** The validators return every problem rather than the first, so all are shown at once. */
    private boolean report(final List<String> messages, final String field) {
        if (messages == null || messages.isEmpty()) {
            return true;
        }
        for (final String message : messages) {
            addFieldErrorAndLog(field, message);
        }
        return false;
    }
}
