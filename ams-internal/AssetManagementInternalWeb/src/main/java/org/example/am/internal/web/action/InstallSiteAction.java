package org.example.am.internal.web.action;

import java.util.Collection;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.CountryType;
import org.example.am.shared.domain.StateType;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * Step 1 of the install order: the site - where the device is fitted, and who meets the engineer.
 *
 * <p>The address validation interceptor wraps {@link #saveSite()}, so the result it returns
 * ({@code av.success}, {@code av.suggestion} or {@code av.error}) decides whether the user moves
 * straight on, is shown a correction to confirm, or is told the check could not be made. All three
 * outcomes stay on this one screen; a validation outage never stops the order.</p>
 */
@Component("InstallSiteAction")
@Scope("prototype")
public class InstallSiteAction extends InstallOrderBaseAction {

    private static final long serialVersionUID = 1L;

    /** Digits, spaces and the usual punctuation; length is checked by the pattern too. */
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-. ]{7,30}$");

    public String initSite() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkCanContinue(model);
        if (stop != null) {
            return stop;
        }
        if (model.getSiteAddress().getCountry() == null) {
            model.getSiteAddress().setCountry(CountryType.US);
        }
        storeModel(model);
        return Action.SUCCESS;
    }

    /**
     * Validates what was keyed, then hands over to the address validation interceptor.
     *
     * <p>Everything the interceptor might improve is checked here first: sending an obviously
     * incomplete address to the validation service wastes a call and comes back as "could not
     * verify", which reads as a service problem rather than as a missing ZIP code.</p>
     */
    public String saveSite() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkCanContinue(model);
        if (stop != null) {
            return stop;
        }
        boolean valid = validateContact(model.getSiteContact());
        valid &= validateAddress(model.getSiteAddress());
        if (!valid) {
            return Action.INPUT;
        }
        // Re-keying the address invalidates any earlier verdict on it: a suggestion accepted for
        // the old text says nothing about the new.
        model.getSiteAddress().setValidated(false);
        model.setAddressSuggestionAccepted(false);
        model.setSuggestedAddress(null);
        model.setAddressCheckUnavailable(false);
        model.reachStep(InstallOrderModel.STEP_DEVICE);
        storeModel(model);
        return Action.SUCCESS;
    }

    /** Takes the correction the validation service offered in place of what was keyed. */
    public String acceptSuggestion() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkCanContinue(model);
        if (stop != null) {
            return stop;
        }
        if (model.getSuggestedAddress() == null) {
            addActionError("There is no suggested address to accept.");
            return Action.INPUT;
        }
        model.setSiteAddress(model.getSuggestedAddress());
        model.setSuggestedAddress(null);
        model.setAddressSuggestionAccepted(true);
        storeModel(model);
        return Action.SUCCESS;
    }

    /**
     * Keeps the address exactly as keyed and carries on - offered both against a suggestion and
     * when the check could not be made, because the validation service is wrong often enough on
     * new builds and renumbered streets that "I meant what I typed" has to be a supported answer.
     */
    public String keepAddress() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkCanContinue(model);
        if (stop != null) {
            return stop;
        }
        if (!model.getSiteAddress().isComplete()) {
            addActionError("Enter the site address first.");
            return Action.INPUT;
        }
        // Not marked validated: it was not. The order carries VALIDATED_FL = 'N'.
        model.setSuggestedAddress(null);
        model.setAddressCheckUnavailable(false);
        storeModel(model);
        return Action.SUCCESS;
    }

    public Collection<StateType> getStateOptions() {
        return StateType.values();
    }

    public Collection<CountryType> getCountryOptions() {
        return CountryType.values();
    }

    private boolean validateContact(final Contact contact) {
        boolean valid = true;
        if (StringUtils.isBlank(contact.getFirstName())) {
            addFieldErrorAndLog("siteContact.firstName", "The site contact's first name is required.");
            valid = false;
        }
        if (StringUtils.isBlank(contact.getLastName())) {
            addFieldErrorAndLog("siteContact.lastName", "The site contact's last name is required.");
            valid = false;
        }
        if (StringUtils.isBlank(contact.getEmailAddress())) {
            addFieldErrorAndLog("siteContact.emailAddress", "An email address is required.");
            valid = false;
        } else if (!EmailValidator.getInstance().isValid(contact.getEmailAddress().trim())) {
            addFieldErrorAndLog("siteContact.emailAddress",
                    "That does not look like an email address.");
            valid = false;
        }
        if (StringUtils.isBlank(contact.getPhoneNumber())) {
            addFieldErrorAndLog("siteContact.phoneNumber", "A phone number is required.");
            valid = false;
        } else if (!PHONE.matcher(contact.getPhoneNumber().trim()).matches()) {
            addFieldErrorAndLog("siteContact.phoneNumber",
                    "A phone number may only contain digits, spaces and + ( ) - .");
            valid = false;
        }
        return valid;
    }

    /**
     * The length limits are the carrier's: a longer line is silently truncated by their label
     * printer and the box goes to a subtly wrong address. Here is the only place it can be fixed.
     */
    private boolean validateAddress(final Address address) {
        boolean valid = true;
        if (StringUtils.isBlank(address.getAddressLine1())) {
            addFieldErrorAndLog("siteAddress.addressLine1", "The first address line is required.");
            valid = false;
        } else if (tooLong(address.getAddressLine1(), Address.MAX_ADDRESS_LINE_LENGTH)) {
            addFieldErrorAndLog("siteAddress.addressLine1", "Address lines must be "
                    + Address.MAX_ADDRESS_LINE_LENGTH + " characters or fewer.");
            valid = false;
        }
        if (tooLong(address.getAddressLine2(), Address.MAX_ADDRESS_LINE_LENGTH)) {
            addFieldErrorAndLog("siteAddress.addressLine2", "Address lines must be "
                    + Address.MAX_ADDRESS_LINE_LENGTH + " characters or fewer.");
            valid = false;
        }
        if (StringUtils.isBlank(address.getCity())) {
            addFieldErrorAndLog("siteAddress.city", "The city is required.");
            valid = false;
        } else if (tooLong(address.getCity(), Address.MAX_CITY_LENGTH)) {
            addFieldErrorAndLog("siteAddress.city",
                    "The city must be " + Address.MAX_CITY_LENGTH + " characters or fewer.");
            valid = false;
        }
        if (address.getState() == null) {
            addFieldErrorAndLog("siteAddress.state", "Choose a state.");
            valid = false;
        }
        if (StringUtils.isBlank(address.getZipCode())) {
            addFieldErrorAndLog("siteAddress.zipCode", "The ZIP code is required.");
            valid = false;
        }
        return valid;
    }

    private static boolean tooLong(final String value, final int limit) {
        return value != null && value.trim().length() > limit;
    }
}
