package org.example.am.shared.dao.impl;

import java.util.Date;

import org.example.am.shared.dao.StoredProcedureDAO;
import org.example.am.shared.dao.scheduling.EntityEmailDAO;
import org.example.am.shared.dao.scheduling.TimeslotSchedulingDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * The scheduling and notification operations, as the shared services see them.
 *
 * <p>The interface, the bean name and every method signature are unchanged from when these were
 * Oracle stored procedures. What has changed is underneath: the logic now lives in Java, in
 * {@code org.example.am.shared.dao.scheduling}, and this class is a thin delegate.</p>
 *
 * <p>The name is kept deliberately. Renaming it would touch every caller and every bean reference
 * for no behavioural gain, and the original PL/SQL is still on disk under
 * {@code db/oracle/06_packages} as the specification the port was written from - so the connection
 * is worth leaving visible.</p>
 */
@Repository("storedProcedureSharedDAO")
public class StoredProcedureDAOImpl implements StoredProcedureDAO {

    @Autowired
    private TimeslotSchedulingDAO timeslotSchedulingDAO;

    @Autowired
    private EntityEmailDAO entityEmailDAO;

    @Override
    public String reserveTimeslot(final long timeslotId, final long entityId,
            final String entityType, final Date scheduledDate, final String userId) {
        return timeslotSchedulingDAO
                .reserve(timeslotId, entityId, entityType, scheduledDate, userId)
                .getStatus();
    }

    /**
     * @return the id of the first genuinely queued notification, or {@code null} when every
     *         recipient was suppressed, there were none, or the attempt failed
     */
    @Override
    public Long addEntityEmail(final String entityTypeCode, final long entityId,
            final String templateCode, final String userId) {
        return entityEmailDAO.addEntityEmail(entityTypeCode, entityId, templateCode, userId)
                .getEmailId();
    }
}
