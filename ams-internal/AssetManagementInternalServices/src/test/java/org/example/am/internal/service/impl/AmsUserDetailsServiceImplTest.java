package org.example.am.internal.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.example.am.internal.security.AmsUser;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.security.WebSealPrincipal;
import org.example.am.internal.service.AbstractInternalTest;
import org.example.am.internal.service.AmsUserDetailsService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class AmsUserDetailsServiceImplTest extends AbstractInternalTest {

    @Autowired
    private AmsUserDetailsService amsUserDetailsService;

    private static PreAuthenticatedAuthenticationToken token(final String username,
            final String... groups) {
        final WebSealPrincipal principal = new WebSealPrincipal(username);
        principal.setLdapGroups(Arrays.asList(groups));
        return new PreAuthenticatedAuthenticationToken(principal, "N/A");
    }

    @Test
    public void groupsBecomeAuthorities() {
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(
                token("opsuser", "AMS_INTERNAL_OPERATIONS"));

        assertEquals("opsuser", user.getUsername());
        assertTrue(user.hasRole(SecurityRoleType.INT_SEARCH_ASSETS));
        assertTrue(user.hasRole(SecurityRoleType.INT_VIEW_ASSET));
        assertFalse(user.hasRole(SecurityRoleType.INT_ADMIN_UTILITIES));
    }

    /**
     * Everyone authenticated holds ROLE_USER, because the catch-all URL rule is written against it.
     * Without it a user whose groups map to nothing would be refused by the entry point instead of
     * being shown the "not permitted" page.
     */
    @Test
    public void everyAuthenticatedUserHoldsRoleUser() {
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(
                token("nobody", "UNMAPPED_GROUP"));

        assertEquals(1, user.getAuthorities().size());
        assertEquals(SecurityRoleType.ROLE_USER,
                user.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    public void authoritiesCarryTheSpringSecurityPrefix() {
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(
                token("opsuser", "AMS_INTERNAL_ADMIN"));
        assertTrue(user.getAuthorities().toString().contains("ROLE_INT_ADMIN_UTILITIES"));
    }

    @Test
    public void theEmailAddressFallsBackToTheDirectoryLookup() {
        final AmsUser fromHeader;
        final WebSealPrincipal principal = new WebSealPrincipal("opsuser");
        principal.setEmailAddress("header@example.test");
        principal.setLdapGroups(Collections.singletonList("AMS_INTERNAL_OPERATIONS"));
        fromHeader = (AmsUser) amsUserDetailsService.loadUserDetails(
                new PreAuthenticatedAuthenticationToken(principal, "N/A"));
        assertEquals("header@example.test", fromHeader.getEmailAddress());

        final AmsUser fromDatabase =
                (AmsUser) amsUserDetailsService.loadUserDetails(token("opsuser"));
        assertEquals("ops.user@example.test", fromDatabase.getEmailAddress());
    }

    @Test
    public void theDisplayNameComesFromThePrincipal() {
        final WebSealPrincipal principal = new WebSealPrincipal("opsuser");
        principal.setFirstName("Ops");
        principal.setLastName("User");
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(
                new PreAuthenticatedAuthenticationToken(principal, "N/A"));
        assertEquals("Ops User", user.getDisplayName());
    }

    /**
     * A token whose principal is only a name still authenticates, with no roles. That is what a
     * request carrying iv-user but no iv-groups looks like.
     */
    @Test
    public void aBarePrincipalAuthenticatesWithNoRoles() {
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(
                new PreAuthenticatedAuthenticationToken("someone", "N/A"));
        assertEquals("someone", user.getUsername());
        assertEquals(1, user.getAuthorities().size());
    }

    @Test(expected = UsernameNotFoundException.class)
    public void aTokenWithNoIdentityIsRejected() {
        amsUserDetailsService.loadUserDetails(
                new PreAuthenticatedAuthenticationToken(new WebSealPrincipal("   "), "N/A"));
    }

    @Test
    public void thePasswordIsBlankAndTheAccountIsAlwaysUsable() {
        final AmsUser user = (AmsUser) amsUserDetailsService.loadUserDetails(token("opsuser"));
        // Authentication happened at the proxy; there is no credential for this application to
        // hold, expire or lock.
        assertEquals("", user.getPassword());
        assertTrue(user.isEnabled());
        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
    }
}
