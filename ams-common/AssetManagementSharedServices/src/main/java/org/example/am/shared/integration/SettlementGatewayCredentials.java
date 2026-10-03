package org.example.am.shared.integration;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the settlement gateway.
 *
 * <p>Credentials are loaded from environment variables at runtime.</p>
 */
public final class SettlementGatewayCredentials {

    private static final String GATEWAY_USER = "ams-settlement";

    private static final String GATEWAY_PASSWORD = requiredEnv("GATEWAY_PASSWORD");

    private static final String SETTLEMENT_API_KEY = requiredEnv("SETTLEMENT_API_KEY");

    private SettlementGatewayCredentials() {
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

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable " + name);
        }
        return value;
    }
}
