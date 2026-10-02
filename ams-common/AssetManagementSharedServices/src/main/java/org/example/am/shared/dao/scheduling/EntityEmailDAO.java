package org.example.am.shared.dao.scheduling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Queues the notifications an event owes its customer.
 *
 * <p>Ported from {@code AMS_EMAIL_PG.add_entity_email}. Read that package body alongside this class
 * if you are changing either; it is kept under {@code db/oracle/06_packages} as the original
 * specification.</p>
 *
 * <p><strong>This is the one operation that never throws.</strong> It is the last call in
 * {@code submitOrder}, {@code cancelOrder} and both network-change-request paths, all of which run
 * inside a read-write transaction that has already done the real work. A failure to queue a
 * courtesy email must not roll back the order it was notifying about, so everything is caught, the
 * savepoint is rolled back, and {@link SchedulingStatus#ERROR} is returned. Do not add
 * {@code @Transactional} to this class - a rollback-only marker set between the throw and the catch
 * would silently defeat that.</p>
 */
@Repository("entityEmailDAO")
public class EntityEmailDAO extends BaseDAO {

    /** Used when {@code EMAILDEDUPMIN} is absent or unreadable, exactly as the PL/SQL did. */
    private static final int DEFAULT_DEDUP_MINUTES = 60;

    private static final Set<String> ENTITY_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("ORDER", "ASSET", "NCR", "RMA", "DECOM")));

    private static final Set<String> TEMPLATES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("ORDCONF", "ORDCANCEL", "INSTSCHED", "NCRCONF",
                    "NCRCANCEL", "DECOMSCHED", "RMAREMIND")));

    /**
     * Which contact roles hear about which kind of event. Two roles per type; where both are the
     * same the DISTINCT in the recipient query collapses them.
     */
    private static final String[][] ROLES_BY_TYPE = {
        {"ORDER", "ORDERING", "INSTALL"},
        {"NCR",   "ORDERING", "TECH"},
        {"ASSET", "TECH",     "TECH"},
        {"DECOM", "ORDERING", "TECH"},
        {"RMA",   "SHIPPING", "TECH"},
    };

    /**
     * The address sanity check is deliberately the cheapest one possible: real validation is the
     * mail poller's job, and this only keeps obvious rubbish out of the queue.
     */
    private static final String SELECT_RECIPIENTS =
            "SELECT DISTINCT LOWER(TRIM(EMAIL_ADDRESS)) AS ADDR "
          + "  FROM AMS_CONTACTS "
          + " WHERE CUSTOMER_ID = :customerId "
          + "   AND ACTIVE_FL = 'Y' "
          + "   AND CONTACT_TYPE_CD IN (:roleOne, :roleTwo) "
          + "   AND EMAIL_ADDRESS IS NOT NULL "
          + "   AND INSTR(EMAIL_ADDRESS, '@') > 1 "
          + " ORDER BY 1 ";

    /**
     * SUPPRESS is excluded from the duplicate test on purpose, so one suppression cannot suppress
     * the next.
     */
    private static final String COUNT_DUPLICATES =
            "SELECT COUNT(*) FROM AMS_EMAIL_QUEUE "
          + " WHERE ENTITY_TYPE_CD = :entityType "
          + "   AND ENTITY_ID = :entityId "
          + "   AND TEMPLATE_CD = :template "
          + "   AND LOWER(TO_ADDRESS) = :address "
          + "   AND EMAIL_STATUS_CD IN ('QUEUED', 'SENDING', 'SENT') "
          + "   AND CREATED_DT > DATEADD('MINUTE', -:dedupMinutes, SYSTIMESTAMP) ";

    private static final String NEXT_EMAIL_ID =
            "SELECT " + CommonConstants.SEQ_EMAIL_QUEUE + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_EMAIL =
            "INSERT INTO AMS_EMAIL_QUEUE "
          + "       ( EMAIL_ID, TEMPLATE_CD, ENTITY_TYPE_CD, ENTITY_ID, TO_ADDRESS,"
          + "         EMAIL_STATUS_CD, FAILURE_REASON, CREATED_DT, CREATED_BY,"
          + "         MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :emailId, :template, :entityType, :entityId, :address,"
          + "         :status, :failureReason, SYSTIMESTAMP, :userId,"
          + "         SYSTIMESTAMP, :userId ) ";

    /** Each of the five entity types reaches a customer by a different route. */
    private static final String[][] CUSTOMER_LOOKUPS = {
        {"ORDER", "SELECT CUSTOMER_ID FROM AMS_ORDERS WHERE ORDER_ID = :entityId"},
        {"NCR",   "SELECT CUSTOMER_ID FROM AMS_NETWORK_CHANGE_REQUESTS WHERE NCR_ID = :entityId"},
        {"ASSET", "SELECT CUSTOMER_ID FROM AMS_ASSETS WHERE ASSET_ID = :entityId"},
        {"DECOM", "SELECT A.CUSTOMER_ID FROM AMS_DECOMMISSIONS D "
                + "  JOIN AMS_ASSETS A ON A.ASSET_ID = D.ASSET_ID WHERE D.DECOMMISSION_ID = :entityId"},
        {"RMA",   "SELECT A.CUSTOMER_ID FROM AMS_RMAS R "
                + "  JOIN AMS_ASSETS A ON A.ASSET_ID = R.ASSET_ID WHERE R.RMA_ID = :entityId"},
    };

    @Autowired
    private ConfigService configService;

    /**
     * Queues one notification per eligible contact.
     *
     * @return the outcome, and the id of the first genuinely queued row - {@code null} when every
     *         recipient was suppressed or there were none
     */
    public SchedulingResult addEntityEmail(final String entityTypeCode, final long entityId,
            final String templateCode, final String userId) {
        try {
            return SavepointScope.run(dataSource(),
                    () -> queue(normalise(entityTypeCode), entityId, normalise(templateCode), userId));
        } catch (final RuntimeException failure) {
            // See the class comment: deliberately swallowed. SavepointScope has already undone
            // anything this attempt wrote, so the caller's transaction is intact.
            logger.warn("Could not queue {} notification for {} {}; the caller's work stands",
                    templateCode, entityTypeCode, Long.valueOf(entityId), failure);
            return new SchedulingResult(SchedulingStatus.ERROR);
        }
    }

    private javax.sql.DataSource dataSource() {
        return ((org.springframework.jdbc.core.JdbcTemplate)
                getNamedParameterJdbcTemplate().getJdbcOperations()).getDataSource();
    }

    private SchedulingResult queue(final String entityType, final long entityId,
            final String template, final String userId) {
        if (entityId == 0L) {
            return new SchedulingResult(SchedulingStatus.INVALID_INPUT);
        }
        if (!ENTITY_TYPES.contains(entityType)) {
            return new SchedulingResult(SchedulingStatus.INVALID_ENTITY_TYPE);
        }
        if (!TEMPLATES.contains(template)) {
            return new SchedulingResult(SchedulingStatus.INVALID_TEMPLATE);
        }

        final Long customerId = resolveCustomer(entityType, entityId);
        if (customerId == null) {
            return new SchedulingResult(SchedulingStatus.NOT_FOUND);
        }

        final String[] roles = rolesFor(entityType);
        final int dedupMinutes = configService.getInt(PropertyType.EMAIL_DEDUP_MINUTES,
                DEFAULT_DEDUP_MINUTES);

        final List<String> recipients = getNamedParameterJdbcTemplate().queryForList(
                SELECT_RECIPIENTS, ParameterRepository.create()
                        .with(CommonConstants.PARAM_CUSTOMER_ID, customerId)
                        .with("roleOne", roles[0])
                        .with("roleTwo", roles[1])
                        .build(), String.class);

        Long firstQueuedId = null;
        int queued = 0;
        int suppressed = 0;

        for (final String address : recipients) {
            final Integer duplicates = getNamedParameterJdbcTemplate().queryForObject(
                    COUNT_DUPLICATES, ParameterRepository.create()
                            .with("entityType", entityType)
                            .with("entityId", Long.valueOf(entityId))
                            .with("template", template)
                            .with("address", address)
                            .with("dedupMinutes", Integer.valueOf(dedupMinutes))
                            .build(), Integer.class);

            final Long emailId = getNamedParameterJdbcTemplate().queryForObject(
                    NEXT_EMAIL_ID, ParameterRepository.create().build(), Long.class);
            final boolean isDuplicate = duplicates != null && duplicates.intValue() > 0;

            getNamedParameterJdbcTemplate().update(INSERT_EMAIL, ParameterRepository.create()
                    .with("emailId", emailId)
                    .with("template", template)
                    .with("entityType", entityType)
                    .with("entityId", Long.valueOf(entityId))
                    .with("address", address)
                    .with("status", isDuplicate ? "SUPPRESS" : "QUEUED")
                    // Written rather than dropped, so "why did the customer not get the mail" is
                    // answerable from the table.
                    .with("failureReason", isDuplicate
                            ? "Duplicate within the " + dedupMinutes + " minute window" : null)
                    .with(CommonConstants.PARAM_USER_ID, userId)
                    .build());

            if (isDuplicate) {
                suppressed++;
            } else {
                queued++;
                if (firstQueuedId == null) {
                    firstQueuedId = emailId;
                }
            }
        }

        if (queued > 0) {
            return new SchedulingResult(SchedulingStatus.OK).withEmailId(firstQueuedId);
        }
        return new SchedulingResult(suppressed > 0
                ? SchedulingStatus.SUPPRESSED : SchedulingStatus.NO_RECIPIENTS);
    }

    private Long resolveCustomer(final String entityType, final long entityId) {
        for (final String[] lookup : CUSTOMER_LOOKUPS) {
            if (lookup[0].equals(entityType)) {
                final List<Long> found = getNamedParameterJdbcTemplate().queryForList(lookup[1],
                        ParameterRepository.of("entityId", Long.valueOf(entityId)).build(),
                        Long.class);
                return found.isEmpty() ? null : found.get(0);
            }
        }
        return null;
    }

    private static String[] rolesFor(final String entityType) {
        for (final String[] mapping : ROLES_BY_TYPE) {
            if (mapping[0].equals(entityType)) {
                return new String[] {mapping[1], mapping[2]};
            }
        }
        // Unreachable: the type was checked against the same list above.
        return new String[] {null, null};
    }

    private static String normalise(final String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ENGLISH);
    }

    /** Exposed for the tests that assert the recipient routing without going through the queue. */
    List<String> recipientRolesFor(final String entityType) {
        return new ArrayList<String>(Arrays.asList(rolesFor(normalise(entityType))));
    }
}
