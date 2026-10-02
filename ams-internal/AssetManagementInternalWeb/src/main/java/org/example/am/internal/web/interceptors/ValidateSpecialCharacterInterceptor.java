package org.example.am.internal.web.interceptors;

import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.example.am.internal.utils.InternalConstants;

import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

/**
 * Rejects any request parameter containing a character outside the accepted set.
 *
 * <p>A whitelist rather than a blacklist: the set of characters the application's own screens ever
 * need is small and known, whereas the set of characters an attacker might use is not. Anything
 * outside it is a request this application did not generate.</p>
 *
 * <p>Returning {@code input} rather than throwing keeps the user on the form with an error, which
 * is the right outcome for the common case - somebody pasting a curly quote out of a document.</p>
 */
public class ValidateSpecialCharacterInterceptor extends AbstractInterceptor {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            LogManager.getLogger(ValidateSpecialCharacterInterceptor.class);

    private static final Pattern WHITELIST =
            Pattern.compile(InternalConstants.SPECIAL_CHARACTER_WHITELIST);

    /** Names are checked against their own, slightly wider, list; see {@link #isAcceptedName}. */
    private static final Pattern NAME_WHITELIST =
            Pattern.compile(InternalConstants.PARAMETER_NAME_WHITELIST);

    /** Everything inside a pair of brackets in a parameter name. */
    private static final Pattern BRACKETED = Pattern.compile("\\[[^\\]]*\\]");

    private static final Pattern INDEX = Pattern.compile(InternalConstants.PARAMETER_INDEX);

    @Override
    public String intercept(final ActionInvocation invocation) throws Exception {
        final Map<String, String[]> parameters =
                ServletActionContext.getRequest().getParameterMap();

        for (final Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!isAcceptedName(entry.getKey())) {
                return reject(invocation, entry.getKey());
            }
            final String[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            for (final String value : values) {
                if (!isAccepted(value)) {
                    return reject(invocation, entry.getKey());
                }
            }
        }
        return invocation.invoke();
    }

    private static boolean isAccepted(final String value) {
        return value == null || WHITELIST.matcher(value).matches();
    }

    /**
     * A name may contain square brackets, because a repeating form posts indexed properties.
     *
     * <p>Every bracketed section then has to be a plain integer index. Struts hands the name to
     * OGNL, which evaluates whatever is between the brackets, so accepting brackets without
     * constraining their contents would turn a parameter name into an expression.</p>
     */
    private static boolean isAcceptedName(final String name) {
        if (name == null) {
            return true;
        }
        if (!NAME_WHITELIST.matcher(name).matches()) {
            return false;
        }
        final java.util.regex.Matcher bracketed = BRACKETED.matcher(name);
        while (bracketed.find()) {
            if (!INDEX.matcher(bracketed.group()).matches()) {
                return false;
            }
        }
        // An unbalanced bracket never appears in a name our own forms generate.
        return countOf(name, '[') == countOf(name, ']');
    }

    private static int countOf(final String value, final char character) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == character) {
                count++;
            }
        }
        return count;
    }

    /**
     * The rejected value is deliberately not put into the message, so it cannot be reflected back
     * into the rendered page.
     */
    private static String reject(final ActionInvocation invocation, final String parameterName) {
        LOGGER.warn("Rejected request to {}: parameter '{}' contains unaccepted characters",
                invocation.getProxy().getActionName(), parameterName);
        final Object action = invocation.getAction();
        if (action instanceof ActionSupport) {
            ((ActionSupport) action).addActionError(
                    "The value supplied for '" + parameterName + "' contains characters that are"
                    + " not accepted. Please remove them and try again.");
        }
        return Action.INPUT;
    }
}
