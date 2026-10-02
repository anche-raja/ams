package org.example.am.internal.web.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.security.SecurityRoleType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.security.access.vote.RoleVoter;

/**
 * Clears the {@code ROLE_} prefix that {@link RoleVoter} otherwise prepends.
 *
 * <p>The application's role codes are stored, granted and checked without a prefix - both the
 * database mapping table and {@code request.isUserInRole(role.getCode())} use the bare code. The
 * authorities themselves keep the prefix, because Spring Security's own expression support expects
 * it; this only stops the voter from adding a second one when a rule names a role directly.</p>
 */
public class RemoveRolesPrefixPostProcessor implements BeanPostProcessor {

    private static final Logger LOGGER = LogManager.getLogger(RemoveRolesPrefixPostProcessor.class);

    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName)
            throws BeansException {
        if (bean instanceof RoleVoter) {
            ((RoleVoter) bean).setRolePrefix("");
            LOGGER.debug("Cleared the role prefix on {}; roles are voted on as bare codes such as {}",
                    beanName, SecurityRoleType.INT_SEARCH_ASSETS.getCode());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName)
            throws BeansException {
        return bean;
    }
}
