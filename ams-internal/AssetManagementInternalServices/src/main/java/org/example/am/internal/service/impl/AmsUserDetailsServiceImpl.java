package org.example.am.internal.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.internal.security.AmsRole;
import org.example.am.internal.security.AmsUser;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.security.WebSealPrincipal;
import org.example.am.internal.service.AmsUserDetailsService;
import org.example.am.internal.service.dao.AmsUserDetailsDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("amsUserDetailsService")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class AmsUserDetailsServiceImpl implements AmsUserDetailsService {

    private static final Logger LOGGER = LogManager.getLogger(AmsUserDetailsServiceImpl.class);

    @Autowired
    private AmsUserDetailsDAO amsUserDetailsDAO;

    @Override
    public UserDetails loadUserDetails(final Authentication authentication) {
        final WebSealPrincipal principal = toPrincipal(authentication);
        if (principal == null || principal.getUsername() == null
                || principal.getUsername().trim().length() == 0) {
            throw new UsernameNotFoundException("The request carried no usable identity");
        }

        final List<SecurityRoleType> roles =
                amsUserDetailsDAO.getRolesForLdapGroups(principal.getLdapGroups());

        final Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
        // Every authenticated caller gets ROLE_USER; the catch-all URL rule is written against it,
        // so a user whose groups map to nothing can still reach the unauthorised page rather than
        // being bounced by the entry point with no explanation.
        authorities.add(new SimpleGrantedAuthority(SecurityRoleType.ROLE_USER));
        for (final SecurityRoleType role : roles) {
            authorities.add(new AmsRole(role));
        }

        final AmsUser user = new AmsUser(principal.getUsername().trim(), authorities);
        user.setLdapGroups(principal.getLdapGroups());
        user.setDisplayName(principal.getDisplayName());
        user.setEmailAddress(principal.getEmailAddress() == null
                ? amsUserDetailsDAO.getEmailAddressForUser(principal.getUsername())
                : principal.getEmailAddress());

        if (roles.isEmpty()) {
            LOGGER.warn("User {} authenticated with groups {} but holds no AMS roles",
                    user.getUsername(), principal.getLdapGroups());
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("User {} resolved to {} roles", user.getUsername(),
                    Integer.valueOf(roles.size()));
        }
        return user;
    }

    /**
     * @return the principal as a {@link WebSealPrincipal}; a token whose principal is only a name
     *         is wrapped so that a caller with no group header still authenticates, just with no
     *         roles
     */
    private static WebSealPrincipal toPrincipal(final Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        final Object principal = authentication.getPrincipal();
        if (principal instanceof WebSealPrincipal) {
            return (WebSealPrincipal) principal;
        }
        if (principal == null) {
            return null;
        }
        return new WebSealPrincipal(String.valueOf(principal));
    }

    public void setAmsUserDetailsDAO(final AmsUserDetailsDAO amsUserDetailsDAO) {
        this.amsUserDetailsDAO = amsUserDetailsDAO;
    }
}
