package org.example.am.network.domain;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.example.am.network.validation.NetworkUtils;

/**
 * An inclusive range of IPv4 addresses, held as its numeric bounds so containment and overlap are
 * simple comparisons.
 */
public class IPRange implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String startAddress;
    private final String endAddress;
    private final long start;
    private final long end;
    private final String description;

    public IPRange(final String startAddress, final String endAddress) {
        this(startAddress, endAddress, null);
    }

    public IPRange(final String startAddress, final String endAddress, final String description) {
        if (!NetworkUtils.isValidIpAddress(startAddress)) {
            throw new IllegalArgumentException("Invalid range start: " + startAddress);
        }
        if (!NetworkUtils.isValidIpAddress(endAddress)) {
            throw new IllegalArgumentException("Invalid range end: " + endAddress);
        }
        final long low = NetworkUtils.toLong(startAddress);
        final long high = NetworkUtils.toLong(endAddress);
        if (low > high) {
            throw new IllegalArgumentException("Range start is after range end: "
                    + startAddress + " > " + endAddress);
        }
        this.startAddress = startAddress;
        this.endAddress = endAddress;
        this.start = low;
        this.end = high;
        this.description = description;
    }

    /**
     * @param cidr a range in {@code 10.0.0.0/8} form
     */
    public static IPRange fromCidr(final String cidr, final String description) {
        if (StringUtils.isBlank(cidr) || cidr.indexOf('/') < 0) {
            throw new IllegalArgumentException("Not a CIDR block: " + cidr);
        }
        final String[] parts = cidr.trim().split("/", -1);
        if (parts.length != 2 || !NetworkUtils.isValidIpAddress(parts[0])) {
            throw new IllegalArgumentException("Not a CIDR block: " + cidr);
        }
        final int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (final NumberFormatException notANumber) {
            throw new IllegalArgumentException("Not a CIDR block: " + cidr);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("Prefix length out of range: " + cidr);
        }
        final long mask = prefix == 0 ? 0L : (NetworkUtils.BROADCAST << (32 - prefix)) & NetworkUtils.BROADCAST;
        final long network = NetworkUtils.toLong(parts[0]) & mask;
        final long broadcast = network | ((~mask) & NetworkUtils.BROADCAST);
        return new IPRange(NetworkUtils.toDottedQuad(network),
                NetworkUtils.toDottedQuad(broadcast), description);
    }

    public String getStartAddress() {
        return startAddress;
    }

    public String getEndAddress() {
        return endAddress;
    }

    public String getDescription() {
        return description;
    }

    public long size() {
        return end - start + 1L;
    }

    public boolean contains(final String address) {
        if (!NetworkUtils.isValidIpAddress(address)) {
            return false;
        }
        final long value = NetworkUtils.toLong(address);
        return value >= start && value <= end;
    }

    public boolean overlaps(final IPRange other) {
        return other != null && start <= other.end && other.start <= end;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IPRange)) {
            return false;
        }
        final IPRange that = (IPRange) other;
        return start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return (int) (start ^ (start >>> 32) ^ end ^ (end >>> 32));
    }

    @Override
    public String toString() {
        return startAddress + " - " + endAddress
                + (description == null ? "" : " (" + description + ")");
    }
}
