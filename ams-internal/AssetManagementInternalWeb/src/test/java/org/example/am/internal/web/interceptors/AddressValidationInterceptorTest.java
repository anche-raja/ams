package org.example.am.internal.web.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.am.internal.utils.InternalConstants;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.domain.Address;
import org.example.am.shared.domain.CountryType;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.domain.StateType;
import org.example.am.shared.model.address.AddressValidationResponse;
import org.example.am.shared.model.address.ValidatedAddress;
import org.example.am.shared.model.address.ValidatedQualityType;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.RestService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ModelDriven;
import com.opensymphony.xwork2.interceptor.PreResultListener;

/**
 * Covers <em>when</em> the address check runs, which is the part that was wrong.
 *
 * <p>The interceptor used to do its work after {@code invocation.invoke()} returned. That call
 * executes the action <em>and its result</em>, so the response was already committed and the
 * result name returned from the interceptor was ignored: a user whose address had a correction
 * waiting was redirected straight past it, and the address was stored unvalidated. Nothing failed
 * and nothing was logged.</p>
 *
 * <p>These tests therefore assert the mechanism, not just the verdict: that the work is handed to
 * a {@link PreResultListener}, and that the listener replaces the result code.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class AddressValidationInterceptorTest {

    /** Minimal model-driven action; the interceptor only needs {@code getModel}. */
    private static final class StubAction implements ModelDriven<Object> {

        private final InstallOrderModel model;

        private StubAction(final InstallOrderModel model) {
            this.model = model;
        }

        @Override
        public Object getModel() {
            return model;
        }
    }

    @Mock
    private ActionInvocation invocation;

    @Mock
    private RestService restService;

    @Mock
    private ConfigService configService;

    private AddressValidationInterceptor interceptor;
    private InstallOrderModel model;

    @Before
    public void setUp() throws Exception {
        interceptor = new AddressValidationInterceptor();
        interceptor.setRestService(restService);
        interceptor.setConfigService(configService);

        model = new InstallOrderModel();
        model.setSiteAddress(completeAddress());

        when(invocation.getAction()).thenReturn(new StubAction(model));
        when(invocation.invoke()).thenReturn(Action.SUCCESS);
        when(configService.getBoolean(eq(PropertyType.ADDRESS_VALIDATION_ENABLED),
                org.mockito.Matchers.anyBoolean())).thenReturn(Boolean.TRUE);
    }

    private static Address completeAddress() {
        final Address address = new Address();
        address.setAddressLine1("100 Main St");
        address.setCity("springfield");
        address.setState(StateType.lookup("IL"));
        address.setZipCode("62704");
        address.setCountry(CountryType.US);
        return address;
    }

    private static AddressValidationResponse corrected() {
        final ValidatedAddress validated = new ValidatedAddress();
        validated.setQuality(ValidatedQualityType.CORRECTED);
        validated.setAddressLine1("100 Main Street");
        validated.setCity("Springfield");
        validated.setStateCode("IL");
        validated.setPostalCode("62704");
        validated.setCountryCode("US");
        final AddressValidationResponse response = new AddressValidationResponse();
        response.getAddresses().add(validated);
        return response;
    }

    private static AddressValidationResponse exact() {
        final ValidatedAddress validated = new ValidatedAddress();
        validated.setQuality(ValidatedQualityType.EXACT);
        validated.setAddressLine1("100 Main St");
        validated.setCity("Springfield");
        validated.setStateCode("IL");
        validated.setPostalCode("62704");
        validated.setCountryCode("US");
        final AddressValidationResponse response = new AddressValidationResponse();
        response.getAddresses().add(validated);
        return response;
    }

    private static AddressValidationResponse unavailable() {
        final org.example.am.shared.model.address.Error error =
                new org.example.am.shared.model.address.Error();
        error.setCode("SERVICE_UNAVAILABLE");
        final AddressValidationResponse response = new AddressValidationResponse();
        response.setError(error);
        return response;
    }

    /** Runs the interceptor and returns the listener it registered. */
    private PreResultListener runAndCaptureListener() throws Exception {
        interceptor.intercept(invocation);
        final ArgumentCaptor<PreResultListener> captor =
                ArgumentCaptor.forClass(PreResultListener.class);
        verify(invocation).addPreResultListener(captor.capture());
        return captor.getValue();
    }

    /**
     * The regression this class exists for. Doing the work after {@code invoke()} is too late:
     * the result has already run.
     */
    @Test
    public void theCheckIsRegisteredAsAPreResultListener() throws Exception {
        assertNotNull(runAndCaptureListener());
        verify(invocation).invoke();
    }

    @Test
    public void aCorrectionReplacesTheResultWithTheSuggestionPage() throws Exception {
        when(restService.postAddressValidation(any(Address.class), anyString()))
                .thenReturn(corrected());

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(invocation).setResultCode(InternalConstants.RESULT_AV_SUGGESTION);
        assertNotNull("the suggestion has to reach the model or the page has nothing to show",
                model.getSuggestedAddress());
        assertEquals("100 Main Street", model.getSuggestedAddress().getAddressLine1());
    }

    @Test
    public void anExactMatchMarksTheAddressValidatedAndCarriesOn() throws Exception {
        when(restService.postAddressValidation(any(Address.class), anyString())).thenReturn(exact());

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(invocation).setResultCode(InternalConstants.RESULT_AV_SUCCESS);
        assertTrue(model.getSiteAddress().isValidated());
    }

    /** An outage must not stop an order; the site page offers to continue unverified. */
    @Test
    public void anOutageIsNotFatal() throws Exception {
        when(restService.postAddressValidation(any(Address.class), anyString()))
                .thenReturn(unavailable());

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(invocation).setResultCode(InternalConstants.RESULT_AV_ERROR);
        assertFalse(model.getSiteAddress().isValidated());
        assertTrue("the site page needs to know to offer continuing unverified",
                model.isAddressCheckUnavailable());
    }

    /**
     * If the action rejected the input the user has a form error to fix, and replacing that with
     * an address page would hide it.
     */
    @Test
    public void aFailedActionIsLeftAlone() throws Exception {
        runAndCaptureListener().beforeResult(invocation, Action.INPUT);

        verify(invocation, never()).setResultCode(anyString());
        verify(restService, never()).postAddressValidation(any(Address.class), anyString());
    }

    @Test
    public void anIncompleteAddressIsNotSentToTheService() throws Exception {
        model.getSiteAddress().setZipCode(null);

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(invocation, never()).setResultCode(anyString());
        verify(restService, never()).postAddressValidation(any(Address.class), anyString());
    }

    /** A foreign address is outside what the service knows; asking wastes a call. */
    @Test
    public void anInternationalAddressIsNotSentToTheService() throws Exception {
        model.getSiteAddress().setCountry(CountryType.lookup("CA"));

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(restService, never()).postAddressValidation(any(Address.class), anyString());
    }

    /** Once the user has taken the suggestion, asking again would loop on the same page. */
    @Test
    public void anAcceptedSuggestionIsNotRevalidated() throws Exception {
        model.setAddressSuggestionAccepted(true);

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(restService, never()).postAddressValidation(any(Address.class), anyString());
    }

    @Test
    public void theCheckIsSkippedWhenTheFeatureIsSwitchedOff() throws Exception {
        when(configService.getBoolean(eq(PropertyType.ADDRESS_VALIDATION_ENABLED),
                org.mockito.Matchers.anyBoolean())).thenReturn(Boolean.FALSE);

        runAndCaptureListener().beforeResult(invocation, Action.SUCCESS);

        verify(invocation, never()).setResultCode(anyString());
        verify(restService, never()).postAddressValidation(any(Address.class), anyString());
        assertNull(model.getSuggestedAddress());
    }
}
