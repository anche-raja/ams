package org.example.am.internal.web.listener;

import java.security.SecureRandom;
import java.util.UUID;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.utils.InternalConstants;

/**
 * Puts a per-session token into the session as soon as it is created.
 *
 * <p>This is a second, application-owned CSRF token for the Struts AJAX endpoints. Spring
 * Security's own token protects form posts; the Dojo grids post through their own transport and
 * carry this one instead, checked by {@code AjaxTokenInterceptor}. Generating it on session
 * creation means it is available before the first page renders, so no request can race ahead of
 * it.</p>
 */
public class AjaxTokenListener implements HttpSessionListener {

    private static final Logger LOGGER = LogManager.getLogger(AjaxTokenListener.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        event.getSession().setAttribute(InternalConstants.SESSION_AJAX_TOKEN, generateToken());
        LOGGER.debug("Issued an AJAX token for session {}", event.getSession().getId());
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        LOGGER.debug("Session {} destroyed", event.getSession().getId());
    }

    /**
     * @return a random token. Seeded from {@link SecureRandom} rather than from
     *         {@code UUID.randomUUID} alone so the value cannot be guessed from the session id or
     *         from the time the session started.
     */
    private static String generateToken() {
        final byte[] entropy = new byte[16];
        RANDOM.nextBytes(entropy);
        return UUID.nameUUIDFromBytes(entropy).toString();
    }
}
