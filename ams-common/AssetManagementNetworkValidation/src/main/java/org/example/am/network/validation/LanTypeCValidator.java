package org.example.am.network.validation;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

/**
 * Type C: a point to point handoff between the managed device and the customer edge. Exactly two
 * usable addresses, so the subnet must be a /30 and the gateway is the other half of the pair.
 */
public class LanTypeCValidator extends LanValidator {

    private static final int REQUIRED_PREFIX_LENGTH = 30;

    @Override
    public String getLanType() {
        return "C";
    }

    @Override
    protected int getMaximumPrefixLength() {
        return REQUIRED_PREFIX_LENGTH;
    }

    @Override
    protected boolean isPrivateAddressingRequired() {
        return false;
    }

    @Override
    protected List<String> validateLanType(final String ipAddress, final String subnetMask,
            final String gateway) {
        final List<String> messages = new ArrayList<String>();
        final int prefixLength = NetworkUtils.getPrefixLength(subnetMask);
        if (prefixLength != REQUIRED_PREFIX_LENGTH) {
            messages.add("LAN type C is a point to point handoff and requires a /"
                    + REQUIRED_PREFIX_LENGTH + " subnet; /" + prefixLength + " was supplied.");
            return messages;
        }
        if (StringUtils.isBlank(gateway)) {
            return messages;
        }
        final String first = NetworkUtils.getFirstUsableAddress(ipAddress, subnetMask);
        final String last = NetworkUtils.getLastUsableAddress(ipAddress, subnetMask);
        final boolean pairedCorrectly = (ipAddress.equals(first) && gateway.equals(last))
                || (ipAddress.equals(last) && gateway.equals(first));
        if (!pairedCorrectly) {
            messages.add("LAN type C requires the device and the customer edge to occupy the two "
                    + "usable addresses of the /30 (" + first + " and " + last + ").");
        }
        return messages;
    }
}
