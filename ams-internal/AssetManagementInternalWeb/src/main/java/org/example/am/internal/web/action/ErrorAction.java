package org.example.am.internal.web.action;

import org.example.am.shared.logging.LoggingConstants;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * The Struts side's global error page.
 *
 * <p>Reached through the global exception mapping in {@code struts.xml}, so any exception escaping
 * any action lands here. The page shows a reference the user can quote and nothing else: a stack
 * trace helps nobody who is looking at it and tells an attacker a great deal.</p>
 */
@Component("ErrorAction")
@Scope("prototype")
public class ErrorAction extends BaseAction {

    private static final long serialVersionUID = 1L;

    private static final org.apache.logging.log4j.Logger ALERT_LOGGER =
            org.apache.logging.log4j.LogManager.getLogger(LoggingConstants.ALERT_LOGGER);

    private String reference;

    public String customError() throws Exception {
        // A short reference the user can quote on the phone; the same value is in the log line.
        reference = Long.toHexString(System.currentTimeMillis()).toUpperCase();
        ALERT_LOGGER.error("Rendering the error page for user {} with reference {}",
                getUserId(), reference);
        return Action.SUCCESS;
    }

    public String unauthorized() throws Exception {
        logger.warn("User {} was refused access", getUserId());
        return Action.SUCCESS;
    }

    @Override
    public String execute() throws Exception {
        return customError();
    }

    public String getReference() {
        return reference;
    }
}
