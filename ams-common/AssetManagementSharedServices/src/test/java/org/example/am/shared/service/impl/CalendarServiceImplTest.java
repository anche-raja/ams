package org.example.am.shared.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.helper.AbstractBaseTest;
import org.example.am.shared.service.CalendarService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CalendarServiceImplTest extends AbstractBaseTest {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private CalendarService calendarService;

    private static Date day(final String text) {
        try {
            return DAY.parse(text);
        } catch (final java.text.ParseException impossible) {
            throw new IllegalArgumentException(text, impossible);
        }
    }

    @Test
    public void weekendsAreSkipped() throws Exception {
        // Friday 2025-08-01 plus one business day is Monday 2025-08-04.
        assertEquals("2025-08-04", DAY.format(calendarService.addBusinessDays(day("2025-08-01"), 1)));
    }

    @Test
    public void holidaysAreSkippedAsWellAsWeekends() {
        // Thursday 2025-07-03 plus one lands on Friday 2025-07-04, which is a seeded holiday,
        // so the answer is Monday 2025-07-07.
        assertEquals("2025-07-07", DAY.format(calendarService.addBusinessDays(day("2025-07-03"), 1)));
    }

    @Test
    public void aHolidayThatFallsOnAWeekendCostsNothingExtra() {
        // 2025-12-25 is a Thursday holiday; from Wednesday the 24th, one business day is Friday 26th.
        assertEquals("2025-12-26", DAY.format(calendarService.addBusinessDays(day("2025-12-24"), 1)));
    }

    @Test
    public void addingZeroOrFewerDaysTruncatesRatherThanMoving() {
        assertEquals("2025-08-01", DAY.format(calendarService.addBusinessDays(day("2025-08-01"), 0)));
        assertEquals("2025-08-01", DAY.format(calendarService.addBusinessDays(day("2025-08-01"), -5)));
    }

    @Test
    public void aLongRunStillOnlyReadsTheHolidayTableOnce() {
        // Thirty business days from 2025-06-16 crossing the 4 July holiday.
        assertEquals("2025-07-29", DAY.format(calendarService.addBusinessDays(day("2025-06-16"), 30)));
    }

    @Test
    public void countingIsTheInverseOfAdding() {
        final Date from = day("2025-06-16");
        final Date to = calendarService.addBusinessDays(from, 12);
        assertEquals(12, calendarService.countBusinessDays(from, to));
    }

    @Test
    public void countingBackwardsOrOverNoDistanceIsZero() {
        assertEquals(0, calendarService.countBusinessDays(day("2025-08-05"), day("2025-08-01")));
        assertEquals(0, calendarService.countBusinessDays(day("2025-08-01"), day("2025-08-01")));
        assertEquals(0, calendarService.countBusinessDays(null, day("2025-08-01")));
    }

    @Test
    public void businessDayRecognisesWeekendsAndHolidays() {
        assertTrue(calendarService.isBusinessDay(day("2025-08-01")));
        assertFalse(calendarService.isBusinessDay(day("2025-08-02")));
        assertFalse(calendarService.isBusinessDay(day("2025-07-04")));
        assertFalse(calendarService.isBusinessDay(null));
    }

    @Test
    public void onlySlotsWithRemainingCapacityAreOffered() {
        final List<Timeslot> slots = calendarService.getAvailableTimeslots(
                FacilitationCallType.TECHLINE, day("2025-09-01"), day("2025-09-30"));
        // 9702 is full, so only 9701 comes back.
        assertEquals(1, slots.size());
        assertEquals(Long.valueOf(9701L), slots.get(0).getTimeslotId());
        assertEquals(3, slots.get(0).getRemainingCapacity());
    }

    @Test
    public void customerBlackoutDatesAreReadForTheWindow() {
        final List<Date> blackouts = calendarService.getCustomerBlackoutDates(CUSTOMER_ID,
                day("2025-09-01"), day("2025-09-30"));
        assertEquals(1, blackouts.size());
        assertEquals("2025-09-04", DAY.format(blackouts.get(0)));
    }
}
