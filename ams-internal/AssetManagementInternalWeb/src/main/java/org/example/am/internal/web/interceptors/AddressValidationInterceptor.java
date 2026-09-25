package org.example.am.internal.web.interceptors;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.CountryType;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.domain.StateType;
import org.example.am.shared.model.address.AddressValidationResponse;
import org.example.am.shared.model.address.ValidatedAddress;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.RestService;
import org.springframework.beans.factory.annotation.Autowired;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ModelDriven;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;
import com.opensymphony.xwork2.interceptor.PreResultListener;

/**
 * Validates the site address as the install order's first step is saved.
 *
 * <p>Only fires for complete domestic addresses: the validation service only knows domestic postal
 * data, and asking it about anything else wastes a call and produces a confusing "could not
 * verify" for the user.</p>
 *
 * <p>The three result names let the site step distinguish the outcomes: the address was good, the
 * service has a correction to offer, or the check could not be made. A failed check is
 * {@code av.error} and the flow continues - a validation outage must not stop an order.</p>
 *
 * <p><strong>The work happens in a {@link PreResultListener}, not after
 * {@code invocation.invoke()}.</strong> That call runs the action <em>and executes its result</em>,
 * so by the time it returns the response is already committed and a different result name returned
 * from here changes nothing: the user is redirected onward and never sees the suggestion. A
 * pre-result listener runs after the action but before the result is chosen, which is the only
 * point at which the outcome can still be swapped.</p>
 */
public class AddressValidationInterceptor extends AbstractInterceptor {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LogManager.getLogger(AddressValidationInterceptor.class);

    @Autowired
    private transient RestService restService;

    @Autowired
    private transient ConfigService configService;

    @Override
    public String intercept(final ActionInvocation invocation) throws Exception {
        invocation.addPreResultListener(new PreResultListener() {

            @Override
            public void beforeResult(final ActionInvocation inv, final String resultCode) {
                final String replacement = validate(inv, resultCode);
                if (replacement != null) {
                    inv.setResultCode(replacement);
                }
            }
        });
        return invocation.invoke();
    }

    /**
     * @return the result name to use instead, or {@code null} to leave the action's own alone
     */
    private String validate(final ActionInvocation invocation, final String resultCode) {
        // Only a successful action is second-guessed. If it returned INPUT the user has a form
        // error to fix, and replacing that with an address suggestion would hide it.
        if (!Action.SUCCESS.equals(resultCode)) {
            return null;
        }
        final InstallOrderModel model = getModel(invocation);
        if (model == null || !isEligible(model)) {
            return null;
        }
        if (!configService.getBoolean(PropertyType.ADDRESS_VALIDATION_ENABLED, false)) {
            LOGGER.debug("Address validation is switched off; accepting the address as keyed");
            return null;
        }

        final AddressValidationResponse response =
                restService.postAddressValidation(model.getSiteAddress(),
                        UUID.randomUUID().toString());

        if (!response.isSuccessful()) {
            LOGGER.info("Address validation was unavailable ({}); continuing with the keyed address",
                    response.getError());
            model.setAddressCheckUnavailable(true);
            return InternalConstants.RESULT_AV_ERROR;
        }
        if (response.hasSuggestion()) {
            model.setSuggestedAddress(toAddress(response.getBestMatch(), model.getSiteAddress()));
            return InternalConstants.RESULT_AV_SUGGESTION;
        }

        model.getSiteAddress().setValidated(true);
        return InternalConstants.RESULT_AV_SUCCESS;
    }

    /** Validation is worth doing only for a complete domestic address not already checked. */
    private static boolean isEligible(final InstallOrderModel model) {
        final Address address = model.getSiteAddress();
        if (address == null || !address.isComplete() || !address.isDomestic()) {
            return false;
        }
        // Already checked, or the user has explicitly taken the suggestion; do not ask again.
        return !address.isValidated() && !model.isAddressSuggestionAccepted();
    }

    @SuppressWarnings("unchecked")
    private static InstallOrderModel getModel(final ActionInvocation invocation) {
        final Object action = invocation.getAction();
        if (!(action instanceof ModelDriven)) {
            return null;
        }
        final Object model = ((ModelDriven<Object>) action).getModel();
        return model instanceof InstallOrderModel ? (InstallOrderModel) model : null;
    }

    /**
     * @return the suggestion as an {@link Address}, keeping the attention line and address type
     *         from what the user keyed, since the validation service does not return those
     */
    private static Address toAddress(final ValidatedAddress validated, final Address keyed) {
        final Address suggestion = new Address();
        suggestion.setAddressLine1(validated.getAddressLine1());
        suggestion.setAddressLine2(validated.getAddressLine2());
        suggestion.setCity(validated.getCity());
        suggestion.setZipCode(validated.getPostalCode());
        suggestion.setCounty(validated.getCountyName());
        suggestion.setState(StateType.lookup(validated.getStateCode()));
        suggestion.setCountry(validated.getCountryCode() == null
                ? CountryType.US : CountryType.lookup(validated.getCountryCode()));
        suggestion.setValidated(true);
        if (keyed != null) {
            suggestion.setAttentionTo(keyed.getAttentionTo());
            suggestion.setAddressType(keyed.getAddressType());
        }
        return suggestion;
    }

    public void setRestService(final RestService restService) {
        this.restService = restService;
    }

    public void setConfigService(final ConfigService configService) {
        this.configService = configService;
    }
}
