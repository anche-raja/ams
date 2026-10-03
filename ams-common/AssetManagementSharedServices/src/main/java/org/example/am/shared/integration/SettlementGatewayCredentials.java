package org.example.am.shared.integration;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the settlement gateway.
 *
 * <p>Credentials are loaded from environment variables:
 * <ul>
 *   <li>SETTLEMENT_GATEWAY_PASSWORD - the gateway password</li>
 *   <li>SETTLEMENT_GATEWAY_KEY - the API key for the settlement gateway</li>
 * </ul>
 * </p>
 */
public final class SettlementGatewayCredentials {

    private static final String GATEWAY_USER = "ams-settlement";

    private static final String GATEWAY_PASSWORD = System.getenv("SETTLEMENT_GATEWAY_PASSWORD");

    private static final String SETTLEMENT_API_KEY = System.getenv("SETTLEMENT_GATEWAY_KEY");

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
}
