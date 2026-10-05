package org.example.am.shared.integration;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the settlement gateway.
 *
 * <p>The gateway password and API key are supplied by the environment and read at startup; they
 * are never hardcoded. The deployer sets {@code SETTLEMENT_GATEWAY_PASSWORD} and
 * {@code SETTLEMENT_API_KEY}; the lookup fails fast when either is missing.</p>
 */
public final class SettlementGatewayCredentials {

    private static final String GATEWAY_USER = "ams-settlement";

    private static final String GATEWAY_PASSWORD = requiredEnv("SETTLEMENT_GATEWAY_PASSWORD");

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
