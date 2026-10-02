package org.example.am.internal.web.config;

import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.RestService;
import org.example.am.shared.service.impl.RestSharedTemplateFactory;
import org.example.am.shared.service.impl.StubRestService;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.RestLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbound REST wiring.
 *
 * <p>The template, mapper and logger all come from the shared factory so that the internal and
 * external applications call the platform services with identical timeouts and identical leniency
 * about unknown response fields.</p>
 */
@Configuration
public class RestConfig {

    private static final Logger LOGGER = LogManager.getLogger(RestConfig.class);

    @Autowired
    private Environment environment;

    /**
     * Supplies the canned address validation service for environments with no real one to call.
     *
     * <p>Registered as a <em>fallback collaborator</em> of {@code RestServiceImpl} rather than as
     * a {@code @Primary} replacement for it. A replacement does not work here: Struts builds its
     * interceptors through the Spring object factory with {@code autoWire="name"}, so
     * {@code AddressValidationInterceptor}'s {@code restService} field is satisfied by the bean
     * <em>named</em> {@code restService} - the component-scanned real one - and a primary bean
     * under any other name is simply never seen. Handing the stub to the real service instead
     * means it is used on whichever path reaches it.</p>
     *
     * <p>Returns {@code null} in production, which leaves {@code RestServiceImpl} with no fallback
     * and its original "endpoint not configured" behaviour.</p>
     */
    @Bean(name = "addressValidationFallback")
    public RestService addressValidationFallback() {
        if (!isNonProductionProfile()) {
            return null;
        }
        LOGGER.warn("Registering the canned address validation stub as a fallback;"
                + " it is used only when no endpoint is configured. Active profiles are {}",
                Arrays.toString(environment.getActiveProfiles()));
        return new StubRestService();
    }

    private boolean isNonProductionProfile() {
        for (final String profile : environment.getActiveProfiles()) {
            if (CommonConstants.PROFILE_LOCAL.equalsIgnoreCase(profile)
                    || CommonConstants.PROFILE_DEV.equalsIgnoreCase(profile)
                    || CommonConstants.PROFILE_FIT.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public RestLogger restLogger() {
        return new RestLogger();
    }

    @Bean
    public RestSharedTemplateFactory restSharedTemplateFactory(final ConfigService configService,
            final RestLogger restLogger) {
        return new RestSharedTemplateFactory(configService, restLogger);
    }

    @Bean
    public RestTemplate restTemplate(final RestSharedTemplateFactory factory) {
        return factory.createRestTemplate();
    }

    @Bean
    public ObjectMapper objectMapper(final RestSharedTemplateFactory factory) {
        return factory.createObjectMapper();
    }
}
