package org.example.am.internal.service.dao.impl;

import java.util.Date;

import org.example.am.internal.service.dao.StoredProcedureDAO;
import org.example.am.shared.dao.scheduling.EntityEmailDAO;
import org.example.am.shared.dao.scheduling.NcrSchedulingDAO;
import org.example.am.shared.dao.scheduling.SchedulingResult;
import org.example.am.shared.dao.scheduling.TimeslotSchedulingDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * The scheduling and notification operations, as the internal application sees them.
 *
 * <p>The interface, the bean name and every method signature are unchanged from when these were
 * Oracle stored procedures; the logic now lives in {@code org.example.am.shared.dao.scheduling} and
 * this class is a thin delegate.</p>
 *
 * <p>Both this and its shared counterpart now delegate to the <em>same</em> implementations. They
 * used to compile separate {@code StoredProcedure} objects against the same nine procedures, which
 * had already drifted: the two {@code AddEntityEmailProcedure} classes declared identical
 * parameters but read different OUT values, so the shared one returned the queued id and this one
 * returned the status. That divergence is gone - this returns the status, as its interface always
 * said it did, and the shared one returns the id.</p>
 */
@Repository("internalStoredProcedureDAO")
public class StoredProcedureDAOImpl implements StoredProcedureDAO {

    @Autowired
    private TimeslotSchedulingDAO timeslotSchedulingDAO;

    @Autowired
    private NcrSchedulingDAO ncrSchedulingDAO;

    @Autowired
    private EntityEmailDAO entityEmailDAO;

    @Override
    public String reserveTimeslot(final long timeslotId, final long entityId,
            final String entityTypeCode, final Date scheduledDate, final String userId) {
        return timeslotSchedulingDAO
                .reserve(timeslotId, entityId, entityTypeCode, scheduledDate, userId)
                .getStatus();
    }

    @Override
    public String cancelTimeslot(final long timeslotId, final long entityId,
            final String entityTypeCode, final String userId) {
        return timeslotSchedulingDAO.cancel(timeslotId, entityId, entityTypeCode, userId)
                .getStatus();
    }

    /**
     * The {@code complex} flag selects the site-type variant, which additionally takes a circuit
     * window. It was never a procedure parameter - it chose which of two procedures to call.
     */
    @Override
    public String reserveNetworkChangeDate(final long networkChangeRequestId, final long assetId,
            final String siteTypeCode, final Date scheduledDate, final boolean complex,
            final String userId) {
        final SchedulingResult result = complex
                ? ncrSchedulingDAO.scheduleSiteTypeChange(networkChangeRequestId, assetId,
                        siteTypeCode, scheduledDate, userId)
                : ncrSchedulingDAO.scheduleChangeDate(networkChangeRequestId, scheduledDate, userId);
        return result.getStatus();
    }

    @Override
    public String cancelNetworkChangeDate(final long networkChangeRequestId, final long assetId,
            final String reason, final boolean complex, final String userId) {
        final SchedulingResult result = complex
                ? ncrSchedulingDAO.cancelSiteTypeChange(networkChangeRequestId, reason, userId)
                : ncrSchedulingDAO.cancelChangeDate(networkChangeRequestId, reason, userId);
        return result.getStatus();
    }

    @Override
    public String reserveDecommissionDate(final long decommissionId, final long assetId,
            final Date scheduledDate, final boolean hardwareReturnRequired, final String userId) {
        return timeslotSchedulingDAO.scheduleDecommission(decommissionId, assetId, scheduledDate,
                hardwareReturnRequired, userId).getStatus();
    }

    @Override
    public String cancelDecommissionDate(final long decommissionId, final String reason,
            final String userId) {
        return timeslotSchedulingDAO.cancelDecommission(decommissionId, reason, userId).getStatus();
    }

    @Override
    public String addEntityEmail(final String entityTypeCode, final long entityId,
            final String templateCode, final String userId) {
        return entityEmailDAO.addEntityEmail(entityTypeCode, entityId, templateCode, userId)
                .getStatus();
    }
}
