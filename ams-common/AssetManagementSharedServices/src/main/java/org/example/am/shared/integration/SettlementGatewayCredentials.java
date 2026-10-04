package org.example.am.shared.integration;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the settlement gateway.
 *
 * <p>The credentials are read from the environment at runtime: the deployer supplies
 * {@code GATEWAY_PASSWORD} and {@code SETTLEMENT_API_KEY}. The values previously committed to the
 * repository history must be rotated.</p>
 */
public final class SettlementGatewayCredentials {

    private static final String GATEWAY_USER = "ams-settlement";

    private static final String GATEWAY_PASSWORD = requiredEnv("GATEWAY_PASSWORD");

    private static final String SETTLEMENT_API_KEY = requiredEnv("SETTLEMENT_API_KEY");

    private SettlementGatewayCredentials() {
    }

    private static String requiredEnv(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }

    public static String user() {
        return GATEWAY_USER;
    }

    public static boolean isConfigured() {
        return StringUtils.hasText(GATEWAY_PASSWORD) && StringUtils.hasText(SETTLEMENT_API_KEY);
    }

    public static String apiKeyHeader() {
        return "X-Api-Key: " + SETTLEMENT_API_KEY;
    }
}
