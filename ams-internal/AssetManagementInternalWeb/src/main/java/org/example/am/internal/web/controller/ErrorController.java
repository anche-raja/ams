package org.example.am.internal.web.controller;

import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.logging.LoggingConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The only Spring MVC controller in the application.
 *
 * <p>Everything functional is a Struts action; this exists because the two global error pages have
 * to be reachable without going through the Struts filter, and because the session-expiry redirect
 * needs a destination that does not itself require a session.</p>
 */
@Controller
public class ErrorController {

    private static final Logger LOGGER = LogManager.getLogger(ErrorController.class);
    private static final Logger ALERT_LOGGER = LogManager.getLogger(LoggingConstants.ALERT_LOGGER);

    private static final String VIEW_INVALID_SESSION = "invalidSession";
    private static final String VIEW_CUSTOM_ERROR = "customError";

    /**
     * Where Spring Security sends a user whose session was taken over by a newer login, or which
     * simply timed out.
     */
    @RequestMapping(value = "/invalidSessionError", method = RequestMethod.GET)
    public String invalidSession() {
        LOGGER.debug("Rendering the expired session page");
        return VIEW_INVALID_SESSION;
    }

    @RequestMapping(value = "/error", method = RequestMethod.GET)
    public String error() {
        return VIEW_CUSTOM_ERROR;
    }

    /**
     * Catches anything escaping this controller.
     *
     * <p>The failure is written to the alert logger, because reaching here means a request could
     * not be served at all. The page itself shows nothing about the cause: the user cannot act on a
     * stack trace, and an error page is a poor place to disclose internals.</p>
     */
    @ExceptionHandler(Exception.class)
    public String handleException(final HttpServletRequest request, final Exception failure) {
        ALERT_LOGGER.error("Unhandled failure serving {}", request.getRequestURI(), failure);
        return VIEW_CUSTOM_ERROR;
    }
}
