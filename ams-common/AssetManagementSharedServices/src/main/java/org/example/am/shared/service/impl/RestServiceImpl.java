package org.example.am.shared.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.model.address.AddressValidationRequest;
import org.example.am.shared.model.address.AddressValidationResponse;
import org.example.am.shared.model.address.Error;
import org.example.am.shared.model.address.ValidatableAddress;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.RestService;
import org.example.am.shared.utils.RestLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Calls the address validation service.
 *
 * <p>A failure here is logged and converted into an error-carrying response rather than propagated:
 * the review screen treats that as "could not validate" and lets the user proceed with the address
 * as keyed, which is the required behaviour when the validation service is down.</p>
 */
@Service("restService")
public class RestServiceImpl implements RestService {

    private static final Logger LOGGER = LogManager.getLogger(RestServiceImpl.class);

    private static final String SOURCE_SYSTEM = "AMS-INT";
    private static final String ERROR_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    private static final String ERROR_NOT_CONFIGURED = "NOT_CONFIGURED";

    // TODO(migration): RestTemplate is retained deliberately. It still exists and is not deprecated
    // in Spring 6. The externally-provided 'restTemplate' bean may carry custom interceptors and an
    // error handler; a mechanical rewrite to RestClient is riskier than leaving it. See manual_flag.
    private RestTemplate restTemplate;

    private final ConfigService configService;

    private final RestLogger restLogger;

    /**
     * Used when no endpoint is configured, if one has been supplied.
     *
     * <p>Optional on purpose: production configures a URL and never has one of these. The wiring
     * that decides whether to provide it is in the web module, because that is where the profile
     * is known - see {@code RestConfig}.</p>
     *
     * <p>Resolved lazily through an {@link ObjectProvider} so that an absent bean does not fail
     * fast at startup under Spring 6's tightened optional-injection resolution.</p>
     */
    private final ObjectProvider<RestService> addressValidationFallbackProvider;

    public RestServiceImpl(final RestTemplate restTemplate, final ConfigService configService,
            final RestLogger restLogger,
            final ObjectProvider<RestService> addressValidationFallbackProvider) {
        this.restTemplate = restTemplate;
        this.configService = configService;
        this.restLogger = restLogger;
        this.addressValidationFallbackProvider = addressValidationFallbackProvider;
    }

    @Override
    public AddressValidationResponse postAddressValidation(final Address address,
            final String requestId) {
        final String url = configService.getString(PropertyType.ADDRESS_VALIDATION_URL, null);
        if (url == null) {
            final RestService fallback = addressValidationFallbackProvider.getIfAvailable();
            if (fallback != null) {
                LOGGER.debug("No address validation endpoint configured; using the fallback");
                return fallback.postAddressValidation(address, requestId);
            }
            LOGGER.warn("Address validation URL is not configured; skipping validation");
            return errorResponse(requestId, ERROR_NOT_CONFIGURED,
                    "Address validation endpoint is not configured");
        }

        final AddressValidationRequest request = toRequest(address, requestId);
        final long startedAt = System.currentTimeMillis();
        restLogger.logRequest("POST", url, requestId);
        try {
            final AddressValidationResponse response =
                    restTemplate.postForObject(url, request, AddressValidationResponse.class);
            restLogger.logResponse("POST", url, 200, System.currentTimeMillis() - startedAt, null);
            return response == null
                    ? errorResponse(requestId, ERROR_UNAVAILABLE, "Empty response") : response;
        } catch (final RestClientException failure) {
            restLogger.logFailure("POST", url, System.currentTimeMillis() - startedAt, failure);
            return errorResponse(requestId, ERROR_UNAVAILABLE, failure.getMessage());
        }
    }

    private static AddressValidationRequest toRequest(final Address address, final String requestId) {
        final ValidatableAddress validatable = new ValidatableAddress();
        validatable.setId(requestId);
        validatable.setAddressLine1(address.getAddressLine1());
        validatable.setAddressLine2(address.getAddressLine2());
        validatable.setCity(address.getCity());
        validatable.setStateCode(address.getState() == null ? null : address.getState().getCode());
        validatable.setPostalCode(address.getZipCode());
        validatable.setCountryCode(
                address.getCountry() == null ? null : address.getCountry().getCode());

        final AddressValidationRequest request = new AddressValidationRequest();
        request.setRequestId(requestId);
        request.setSourceSystem(SOURCE_SYSTEM);
        request.addAddress(validatable);
        return request;
    }

    private static AddressValidationResponse errorResponse(final String requestId, final String code,
            final String message) {
        final Error error = new Error();
        error.setCode(code);
        error.setMessage(message);
        final AddressValidationResponse response = new AddressValidationResponse();
        response.setRequestId(requestId);
        response.setError(error);
        return response;
    }

    public void setRestTemplate(final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
}
