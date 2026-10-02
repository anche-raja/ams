package org.example.am.internal.service.dao.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.service.AbstractInternalTest;
import org.example.am.internal.service.dao.AmsUserDetailsDAO;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AmsUserDetailsDAOImplTest extends AbstractInternalTest {

    @Autowired
    private AmsUserDetailsDAO amsUserDetailsDAO;

    @Test
    public void groupsResolveToTheRolesTheyGrant() {
        final List<SecurityRoleType> roles = amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.singletonList("AMS_INTERNAL_OPERATIONS"));
        assertEquals(2, roles.size());
        assertTrue(roles.contains(SecurityRoleType.INT_SEARCH_ASSETS));
        assertTrue(roles.contains(SecurityRoleType.INT_VIEW_ASSET));
    }

    @Test
    public void groupMatchingIsCaseInsensitiveAndTrims() {
        assertEquals(2, amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.singletonList("  ams_internal_operations  ")).size());
    }

    /** A role granted by two different groups must be granted once, not twice. */
    @Test
    public void rolesAreDeduplicatedAcrossGroups() {
        final List<SecurityRoleType> roles = amsUserDetailsDAO.getRolesForLdapGroups(
                Arrays.asList("AMS_INTERNAL_OPERATIONS", "AMS_INTERNAL_ADMIN"));
        assertEquals(3, roles.size());
        assertTrue(roles.contains(SecurityRoleType.INT_ADMIN_UTILITIES));
    }

    /**
     * A mapping naming a role this release no longer defines is ignored rather than failing the
     * sign-in, so removing a role from the code does not lock people out.
     */
    @Test
    public void anUnknownRoleCodeIsIgnored() {
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.singletonList("AMS_INTERNAL_LEGACY")).isEmpty());
    }

    @Test
    public void deactivatedMappingsGrantNothing() {
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.singletonList("AMS_INTERNAL_SUSPENDED")).isEmpty());
    }

    /**
     * An empty IN clause is not valid SQL, so the DAO short circuits. The proxy has been seen to
     * send a header that parses to nothing at all.
     */
    @Test
    public void noGroupsIsHandledWithoutBuildingAnEmptyInClause() {
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(null).isEmpty());
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.<String>emptyList()).isEmpty());
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(
                Arrays.asList("", "   ", null)).isEmpty());
    }

    @Test
    public void unknownGroupsGrantNothing() {
        assertTrue(amsUserDetailsDAO.getRolesForLdapGroups(
                Collections.singletonList("SOMETHING_ELSE")).isEmpty());
    }

    @Test
    public void emailAddressIsLookedUpForTheUser() {
        assertEquals("ops.user@example.test", amsUserDetailsDAO.getEmailAddressForUser("OPSUSER"));
        assertNull(amsUserDetailsDAO.getEmailAddressForUser("nobody"));
        assertNull(amsUserDetailsDAO.getEmailAddressForUser(null));
        assertNull(amsUserDetailsDAO.getEmailAddressForUser("  "));
    }

    @Test
    public void allMappingsAreListedIncludingInactiveOnes() {
        assertEquals(6, amsUserDetailsDAO.getAllMappings().size());
    }
}
