package org.example.am.shared.integration;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the settlement gateway.
 *
 * <p><b>Presentation demo - FORGE secret gate.</b> The credentials below are deliberately hardcoded
 * and fake. FORGE's local secret scan must stop this file in its pre-flight step, before any
 * Bedrock call - not even the guardrail sees it. The migration report lists the file as BLOCKED with
 * the kind and line of each finding, never the value. Leave it as it is: it is the control being
 * demonstrated.</p>
 */
public final class SettlementGatewayCredentials {

    private static final String GATEWAY_USER = "ams-settlement";

    private static final String GATEWAY_PASSWORD = "Qx7!vR2#mLp9sT4w";

    private static final String SETTLEMENT_API_KEY = "9f3cE7kLq2Vx8RtY5bNw1ZsP4hJd6MgA";

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
