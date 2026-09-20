package org.example.am.shared.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.dao.StoredProcedureDAO;
import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.service.ScheduleService;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scheduling algorithm shared by every kind of booking.
 *
 * <p>Subclasses supply only what makes their flow different: which calendar to read, how many
 * business days of lead time the work needs, and what entity type the reservation is recorded
 * against. Everything else - business day arithmetic, customer blackout filtering, the optimistic
 * reservation and its "someone got there first" outcome - lives here.</p>
 */
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public abstract class BaseScheduleServiceImpl implements ScheduleService {

    private static final Logger LOGGER = LogManager.getLogger(BaseScheduleServiceImpl.class);

    /** How far ahead the slot pickers look when the caller does not say. */
    protected static final int DEFAULT_SCHEDULING_WINDOW_DAYS = 60;

    @Autowired
    private CalendarService calendarService;

    @Autowired
    private StoredProcedureDAO storedProcedureDAO;

    @Autowired
    private ConfigService configService;

    /** @return the calendar this flow books against */
    protected abstract FacilitationCallType getCallType();

    /** @return the minimum lead time in business days */
    protected abstract int getMinimumLeadTimeBusinessDays();

    /** @return the entity type code recorded against the reservation */
    protected abstract String getEntityTypeCode();

    @Override
    public List<Timeslot> getAvailableSlots(final long customerId, final Date from, final Date to) {
        final Date earliest = getEarliestSchedulableDate(customerId);
        final Date windowStart = from == null || from.before(earliest) ? earliest : from;
        final Date windowEnd = to == null
                ? ConversionUtils.addDays(windowStart, DEFAULT_SCHEDULING_WINDOW_DAYS) : to;
        if (windowEnd.before(windowStart)) {
            return new ArrayList<Timeslot>();
        }

        final Set<Long> blackouts = toDayKeys(
                calendarService.getCustomerBlackoutDates(customerId, windowStart, windowEnd));

        final List<Timeslot> offerable = new ArrayList<Timeslot>();
        for (final Timeslot slot : calendarService.getAvailableTimeslots(getCallType(), windowStart,
                windowEnd)) {
            if (!slot.isSelectable() || slot.getStartTime() == null) {
                continue;
            }
            if (blackouts.contains(dayKey(slot.getStartTime()))) {
                continue;
            }
            offerable.add(slot);
        }
        return offerable;
    }

    @Override
    public Date getEarliestSchedulableDate(final long customerId) {
        return calendarService.addBusinessDays(new Date(), getMinimumLeadTimeBusinessDays());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean reserve(final long timeslotId, final long entityId, final Date scheduledDate,
            final String userId) {
        final String status = storedProcedureDAO.reserveTimeslot(timeslotId, entityId,
                getEntityTypeCode(), scheduledDate, userId);
        final boolean reserved =
                org.example.am.shared.dao.scheduling.SchedulingStatus.OK.equals(status);
        if (!reserved) {
            // Losing the race is expected under load, not exceptional.
            LOGGER.info("Timeslot {} could not be reserved for {} {}: {}", Long.valueOf(timeslotId),
                    getEntityTypeCode(), Long.valueOf(entityId), status);
        }
        return reserved;
    }

    protected CalendarService getCalendarService() {
        return calendarService;
    }

    protected ConfigService getConfigService() {
        return configService;
    }

    protected StoredProcedureDAO getStoredProcedureDAO() {
        return storedProcedureDAO;
    }

    private static Set<Long> toDayKeys(final List<Date> dates) {
        final Set<Long> keys = new HashSet<Long>();
        for (final Date date : dates) {
            if (date != null) {
                keys.add(dayKey(date));
            }
        }
        return keys;
    }

    private static Long dayKey(final Date date) {
        return Long.valueOf(ConversionUtils.truncateToDay(date).getTime());
    }
}
