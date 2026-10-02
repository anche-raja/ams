package org.example.am.internal.service.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.example.am.internal.security.LdapGroupMapping;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.service.dao.AmsUserDetailsDAO;
import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository("amsUserDetailsDAO")
public class AmsUserDetailsDAOImpl extends BaseDAO implements AmsUserDetailsDAO {

    private static final String SELECT_ROLES_FOR_GROUPS =
            "SELECT DISTINCT M.ROLE_CD "
          + "  FROM AMS_LDAP_GROUP_ROLES M "
          + " WHERE UPPER(M.LDAP_GROUP_NAME) IN (:ldapGroups) "
          + "   AND M.ACTIVE_FL = 'Y' "
          + " ORDER BY M.ROLE_CD ";

    private static final String SELECT_ALL_MAPPINGS =
            "SELECT M.LDAP_GROUP_NAME, M.ROLE_CD, M.ACTIVE_FL "
          + "  FROM AMS_LDAP_GROUP_ROLES M "
          + " ORDER BY M.LDAP_GROUP_NAME, M.ROLE_CD ";

    private static final String SELECT_USER_EMAIL =
            "SELECT U.EMAIL_ADDRESS FROM AMS_USERS U WHERE UPPER(U.USER_ID) = UPPER(:userId) ";

    private static final RowMapper<String> ROLE_CODE_MAPPER = new RowMapper<String>() {

        @Override
        public String mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            return ConversionUtils.getString(rs, "ROLE_CD");
        }
    };

    private static final RowMapper<String> EMAIL_MAPPER = new RowMapper<String>() {

        @Override
        public String mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            return ConversionUtils.getString(rs, "EMAIL_ADDRESS");
        }
    };

    private static final RowMapper<LdapGroupMapping> MAPPING_MAPPER =
            new RowMapper<LdapGroupMapping>() {

                @Override
                public LdapGroupMapping mapRow(final ResultSet rs, final int rowNumber)
                        throws SQLException {
                    final LdapGroupMapping mapping = new LdapGroupMapping();
                    mapping.setLdapGroupName(ConversionUtils.getString(rs, "LDAP_GROUP_NAME"));
                    mapping.setSecurityRole(
                            SecurityRoleType.lookup(ConversionUtils.getString(rs, "ROLE_CD")));
                    mapping.setActive(ConversionUtils.getBoolean(rs, "ACTIVE_FL"));
                    return mapping;
                }
            };

    @Override
    public List<SecurityRoleType> getRolesForLdapGroups(final List<String> ldapGroups) {
        final List<SecurityRoleType> roles = new ArrayList<SecurityRoleType>();
        final Set<String> normalised = normalise(ldapGroups);
        if (normalised.isEmpty()) {
            // An IN clause with no values is not valid SQL, so short circuit rather than build one.
            return roles;
        }
        for (final String code : getNamedParameterJdbcTemplate().query(SELECT_ROLES_FOR_GROUPS,
                ParameterRepository.of("ldapGroups", normalised).build(), ROLE_CODE_MAPPER)) {
            final SecurityRoleType role = SecurityRoleType.lookup(code);
            if (role == null) {
                // A mapping row naming a role this release no longer has is ignored, not fatal.
                logger.warn("Ignoring unknown role code '{}' from AMS_LDAP_GROUP_ROLES", code);
                continue;
            }
            roles.add(role);
        }
        return roles;
    }

    @Override
    public List<LdapGroupMapping> getAllMappings() {
        return getNamedParameterJdbcTemplate().query(SELECT_ALL_MAPPINGS,
                ParameterRepository.create().build(), MAPPING_MAPPER);
    }

    @Override
    public String getEmailAddressForUser(final String userId) {
        if (userId == null || userId.trim().length() == 0) {
            return null;
        }
        final List<String> rows = getNamedParameterJdbcTemplate().query(SELECT_USER_EMAIL,
                ParameterRepository.of(CommonConstants.PARAM_USER_ID, userId.trim()).build(),
                EMAIL_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * @return the supplied groups upper cased and de-duplicated, dropping blanks; the proxy has
     *         been known to send a trailing empty element
     */
    private static Set<String> normalise(final List<String> ldapGroups) {
        final Set<String> normalised = new LinkedHashSet<String>();
        if (ldapGroups == null) {
            return normalised;
        }
        for (final String group : ldapGroups) {
            if (group != null && group.trim().length() > 0) {
                normalised.add(group.trim().toUpperCase());
            }
        }
        return normalised;
    }
}
