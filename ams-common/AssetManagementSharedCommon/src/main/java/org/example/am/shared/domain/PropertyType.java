package org.example.am.shared.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keys into the AMS_PROPERTIES configuration table.
 *
 * <p>Legacy typesafe-enum: instances are {@code public static final} singletons registered in
 * insertion order so that {@link #values()} can drive a drop-down without any extra sorting.</p>
 */
public final class PropertyType extends LoadableType {

    private static final long serialVersionUID = 1L;

    private static final Map<String, PropertyType> VALUES = new LinkedHashMap<String, PropertyType>();

    public static final PropertyType MIN_HOURS_BEFORE_INSTALLATION_TO_CANCEL_ORDER_WITHOUT_PENALTY = register("MINHRSCANCEL", "Minimum hours before installation to cancel without penalty", Long.valueOf(1L));
    public static final PropertyType MIN_TECHLINE_LEAD_TIME_DAYS = register("MINTECHLEAD", "Minimum techline lead time in days", Long.valueOf(2L));
    public static final PropertyType MIN_INSTALL_LEAD_TIME_DAYS = register("MININSTLEAD", "Minimum installation lead time in days", Long.valueOf(3L));
    public static final PropertyType MAX_DECOMMISSION_SCHEDULING_DAYS = register("MAXDECOMDAYS", "Maximum decommission scheduling window in days", Long.valueOf(4L));
    public static final PropertyType ENABLE_USER_IMPERSONATION = register("ENABLEIMPERS", "Enable the non-production user impersonation screen", Long.valueOf(5L));
    public static final PropertyType NON_PROD_BANNER_TEXT = register("NONPRODBANNER", "Banner text shown when pointing at a non-production environment", Long.valueOf(6L));
    public static final PropertyType ENVIRONMENT_NAME = register("ENVNAME", "Logical environment name", Long.valueOf(7L));
    public static final PropertyType ADDRESS_VALIDATION_ENABLED = register("ADDRVALON", "Enable outbound address validation", Long.valueOf(8L));
    public static final PropertyType ADDRESS_VALIDATION_URL = register("ADDRVALURL", "Address validation service endpoint", Long.valueOf(9L));
    public static final PropertyType HELP_CENTER_ENABLED = register("HELPCTRON", "Enable the help centre", Long.valueOf(10L));
    public static final PropertyType MIN_SHIPPING_LEAD_TIME_DAYS = register("MINSHIPLEAD", "Minimum despatch lead time in business days", Long.valueOf(11L));
    public static final PropertyType SHIPPING_WINDOW_HORIZON_DAYS = register("SHIPHORIZON", "How many days ahead despatch windows are offered", Long.valueOf(12L));
    public static final PropertyType DEFAULT_PRIMARY_DNS = register("DEFPRIDNS", "Primary DNS resolver suggested on a new configuration", Long.valueOf(13L));
    public static final PropertyType DEFAULT_SECONDARY_DNS = register("DEFSECDNS", "Secondary DNS resolver suggested on a new configuration", Long.valueOf(14L));
    public static final PropertyType DEFAULT_BANDWIDTH_KBPS = register("DEFBANDWIDTH", "Bandwidth in Kbps suggested when the service does not imply one", Long.valueOf(15L));
    public static final PropertyType LAN_SUGGESTION_BLOCK = register("LANSUGBLOCK", "Private /16 the suggested site LAN is carved out of", Long.valueOf(16L));
    public static final PropertyType SUBSCRIBER_STATIC_START = register("SUBSTATSTART", "First host number handed to a static subscriber machine", Long.valueOf(17L));
    public static final PropertyType DEFAULT_WAN_SUBNET = register("DEFWANSUBNET", "Network address of the WAN pool a first site is numbered from", Long.valueOf(18L));
    public static final PropertyType DEFAULT_WAN_MASK = register("DEFWANMASK", "Subnet mask of the WAN pool a first site is numbered from", Long.valueOf(19L));
    public static final PropertyType EMAIL_DEDUP_MINUTES = register("EMAILDEDUPMIN", "Minutes within which a repeated notification is suppressed rather than queued", Long.valueOf(20L));

    private PropertyType(final String code, final String description, final Long databaseId) {
        super(code, description, databaseId);
    }

    private static PropertyType register(final String code, final String description, final Long databaseId) {
        final PropertyType type = new PropertyType(code, description, databaseId);
        VALUES.put(code, type);
        return type;
    }

    /**
     * @param code database code, may be {@code null}
     * @return the matching instance, or {@code null} when the code is unknown
     */
    public static PropertyType lookup(final String code) {
        if (code == null) {
            return null;
        }
        return VALUES.get(code.trim().toUpperCase());
    }

    public static PropertyType lookupByDatabaseId(final Long databaseId) {
        if (databaseId == null) {
            return null;
        }
        for (final PropertyType type : VALUES.values()) {
            if (databaseId.equals(type.getDatabaseId())) {
                return type;
            }
        }
        return null;
    }

    public static Collection<PropertyType> values() {
        return Collections.unmodifiableCollection(VALUES.values());
    }

    public static Collection<String> codes() {
        return Collections.unmodifiableCollection(VALUES.keySet());
    }
}
