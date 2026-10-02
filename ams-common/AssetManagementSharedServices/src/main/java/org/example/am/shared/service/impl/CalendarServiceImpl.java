package org.example.am.shared.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.example.am.shared.dao.CalendarDAO;
import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.CalendarService;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("calendarService")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class CalendarServiceImpl implements CalendarService {

    /**
     * How far past the target the holiday window is stretched. Adding N business days can never
     * need more than N calendar days plus weekends and holidays; this allowance covers a run of
     * consecutive holidays without a second query.
     */
    private static final int HOLIDAY_WINDOW_SLACK_DAYS = 21;

    @Autowired
    private CalendarDAO calendarDAO;

    @Override
    public Date addBusinessDays(final Date from, final int businessDays) {
        if (from == null) {
            return null;
        }
        if (businessDays <= 0) {
            return ConversionUtils.truncateToDay(from);
        }
        final Date start = ConversionUtils.truncateToDay(from);
        final Date windowEnd = ConversionUtils.addDays(start,
                businessDays * 2 + HOLIDAY_WINDOW_SLACK_DAYS);
        final Set<Long> holidays = toDayKeys(calendarDAO.getHolidays(start, windowEnd));

        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        int remaining = businessDays;
        while (remaining > 0) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            if (isBusinessDay(calendar, holidays)) {
                remaining--;
            }
        }
        return calendar.getTime();
    }

    @Override
    public int countBusinessDays(final Date from, final Date to) {
        if (from == null || to == null) {
            return 0;
        }
        final Date start = ConversionUtils.truncateToDay(from);
        final Date end = ConversionUtils.truncateToDay(to);
        if (!end.after(start)) {
            return 0;
        }
        final Set<Long> holidays = toDayKeys(calendarDAO.getHolidays(start, end));

        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(start);
        int count = 0;
        while (calendar.getTime().before(end)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            if (isBusinessDay(calendar, holidays)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean isBusinessDay(final Date date) {
        if (date == null) {
            return false;
        }
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        if (isWeekend(calendar)) {
            return false;
        }
        return !calendarDAO.isHoliday(date);
    }

    @Override
    public List<Timeslot> getAvailableTimeslots(final FacilitationCallType callType, final Date from,
            final Date to) {
        return calendarDAO.getAvailableTimeslots(callType, from, to);
    }

    @Override
    public Timeslot getTimeslot(final long timeslotId) {
        return calendarDAO.getTimeslot(timeslotId);
    }

    @Override
    public List<Date> getCustomerBlackoutDates(final long customerId, final Date from, final Date to) {
        return calendarDAO.getCustomerBlackoutDates(customerId, from, to);
    }

    private static boolean isBusinessDay(final Calendar calendar, final Set<Long> holidays) {
        if (isWeekend(calendar)) {
            return false;
        }
        return !holidays.contains(Long.valueOf(calendar.getTimeInMillis()));
    }

    private static boolean isWeekend(final Calendar calendar) {
        final int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }

    /**
     * Holidays are compared by truncated millisecond value so that a set lookup replaces a linear
     * scan of the list for every candidate day.
     */
    private static Set<Long> toDayKeys(final List<Date> dates) {
        final Set<Long> keys = new HashSet<Long>();
        for (final Date date : dates) {
            if (date != null) {
                keys.add(Long.valueOf(ConversionUtils.truncateToDay(date).getTime()));
            }
        }
        return keys;
    }

    public void setCalendarDAO(final CalendarDAO calendarDAO) {
        this.calendarDAO = calendarDAO;
    }
}
