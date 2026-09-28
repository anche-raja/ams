package org.example.am.shared.service.impl;

import java.util.Collections;

import org.example.am.shared.service.ConfigService;
import org.example.am.shared.utils.RestLogger;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the {@link RestTemplate} used for outbound calls.
 *
 * <p>A factory rather than a plain {@code @Bean} body so that the internal and external
 * applications configure the same timeouts and the same lenient Jackson mapper without copying the
 * setup into two {@code @Configuration} classes.</p>
 */
public class RestSharedTemplateFactory {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 15000;

    private final ConfigService configService;
    private final RestLogger restLogger;

    public RestSharedTemplateFactory(final ConfigService configService, final RestLogger restLogger) {
        this.configService = configService;
        this.restLogger = restLogger;
    }

    public RestTemplate createRestTemplate() {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // TODO(migration): SimpleClientHttpRequestFactory.setConnectTimeout(int)/setReadTimeout(int)
        // are deprecated in Spring 6 in favour of the Duration overloads. Still compile; left as-is
        // to avoid altering behaviour during the framework upgrade.
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);

        final RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setMessageConverters(Collections.singletonList(
                (org.springframework.http.converter.HttpMessageConverter<?>)
                        new MappingJackson2HttpMessageConverter(createObjectMapper())));
        return restTemplate;
    }

    /**
     * Unknown properties are ignored: the shared services add response fields without a version
     * bump, and a new field must not break an in-flight order.
     */
    public ObjectMapper createObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    public ConfigService getConfigService() {
        return configService;
    }

    public RestLogger getRestLogger() {
        return restLogger;
    }
}
