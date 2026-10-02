package org.example.am.network.validation;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Type A: the customer supplies a routed private subnet and the managed device takes the first
 * usable address in it. Used at sites with their own internal routing.
 */
public class LanTypeAValidator extends LanValidator {

    private static final int MAXIMUM_PREFIX_LENGTH = 29;

    @Override
    public String getLanType() {
        return "A";
    }

    @Override
    protected int getMaximumPrefixLength() {
        return MAXIMUM_PREFIX_LENGTH;
    }

    @Override
    protected boolean isPrivateAddressingRequired() {
        return true;
    }

    @Override
    protected List<String> validateLanType(final String ipAddress, final String subnetMask,
            final String gateway) {
        final List<String> messages = new ArrayList<String>();
        final String firstUsable = NetworkUtils.getFirstUsableAddress(ipAddress, subnetMask);
        if (!firstUsable.equals(ipAddress)) {
            messages.add("LAN type A requires the device to take the first usable address in the "
                    + "subnet (" + firstUsable + ").");
        }
        if (StringUtils.isNotBlank(gateway)
                && gateway.equals(NetworkUtils.getLastUsableAddress(ipAddress, subnetMask))) {
            return messages;
        }
        if (StringUtils.isNotBlank(gateway)) {
            messages.add("LAN type A expects the customer router at the last usable address in the "
                    + "subnet (" + NetworkUtils.getLastUsableAddress(ipAddress, subnetMask) + ").");
        }
        return messages;
    }
}
