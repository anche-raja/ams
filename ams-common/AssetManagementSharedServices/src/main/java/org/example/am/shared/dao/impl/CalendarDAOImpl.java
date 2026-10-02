package org.example.am.shared.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.dao.CalendarDAO;
import org.example.am.shared.domain.FacilitationCallType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository("calendarSharedDAO")
public class CalendarDAOImpl extends BaseDAO implements CalendarDAO {

    private static final String SELECT_HOLIDAYS =
            "SELECT H.HOLIDAY_DT "
          + "  FROM AMS_HOLIDAYS H "
          + " WHERE H.HOLIDAY_DT >= :startDate "
          + "   AND H.HOLIDAY_DT <= :endDate "
          + " ORDER BY H.HOLIDAY_DT ";

    private static final String SELECT_HOLIDAY_COUNT =
            "SELECT COUNT(*) FROM AMS_HOLIDAYS H WHERE TRUNC(H.HOLIDAY_DT) = TRUNC(:holidayDate) ";

    /**
     * Only slots that are open and still have room are offered. The capacity comparison is done in
     * SQL so that a slot filled by another operator between the page render and the submit is not
     * offered again.
     */
    private static final String SELECT_AVAILABLE_TIMESLOTS =
            "SELECT T.TIMESLOT_ID, T.START_TM, T.END_TM, T.CAPACITY, T.RESERVED_COUNT,"
          + "       T.AVAILABLE_FL, T.DISPLAY_LABEL, T.TIME_ZONE "
          + "  FROM AMS_TIMESLOTS T "
          + " WHERE T.CALL_TYPE_CD = :callTypeCode "
          + "   AND T.START_TM >= :startDate "
          + "   AND T.START_TM <= :endDate "
          + "   AND T.AVAILABLE_FL = 'Y' "
          + "   AND T.RESERVED_COUNT < T.CAPACITY "
          + " ORDER BY T.START_TM ";

    private static final String SELECT_TIMESLOT =
            "SELECT T.TIMESLOT_ID, T.START_TM, T.END_TM, T.CAPACITY, T.RESERVED_COUNT,"
          + "       T.AVAILABLE_FL, T.DISPLAY_LABEL, T.TIME_ZONE "
          + "  FROM AMS_TIMESLOTS T "
          + " WHERE T.TIMESLOT_ID = :timeslotId ";

    private static final String SELECT_CUSTOMER_BLACKOUTS =
            "SELECT S.BLACKOUT_DT "
          + "  FROM AMS_CUSTOMER_SCHEDULES S "
          + " WHERE S.CUSTOMER_ID = :customerId "
          + "   AND S.BLACKOUT_DT >= :startDate "
          + "   AND S.BLACKOUT_DT <= :endDate "
          + " ORDER BY S.BLACKOUT_DT ";

    private static final RowMapper<Timeslot> TIMESLOT_MAPPER = new TimeslotMapper();
    private static final RowMapper<Date> DATE_MAPPER = new DateMapper();

    @Override
    public List<Date> getHolidays(final Date from, final Date to) {
        if (from == null || to == null) {
            return new ArrayList<Date>();
        }
        return getNamedParameterJdbcTemplate().query(SELECT_HOLIDAYS,
                ParameterRepository.create()
                        .withDate(CommonConstants.PARAM_START_DATE, ConversionUtils.truncateToDay(from))
                        .withDate(CommonConstants.PARAM_END_DATE, ConversionUtils.truncateToDay(to))
                        .build(), DATE_MAPPER);
    }

    @Override
    public boolean isHoliday(final Date date) {
        if (date == null) {
            return false;
        }
        final Integer count = getNamedParameterJdbcTemplate().queryForObject(SELECT_HOLIDAY_COUNT,
                ParameterRepository.create().withDate("holidayDate", date).build(), Integer.class);
        return count != null && count.intValue() > 0;
    }

    @Override
    public List<Timeslot> getAvailableTimeslots(final FacilitationCallType callType, final Date from,
            final Date to) {
        return getNamedParameterJdbcTemplate().query(SELECT_AVAILABLE_TIMESLOTS,
                ParameterRepository.create()
                        .with("callTypeCode", callType == null ? null : callType.getCode())
                        .withDate(CommonConstants.PARAM_START_DATE, from)
                        .withDate(CommonConstants.PARAM_END_DATE, to)
                        .build(), TIMESLOT_MAPPER);
    }

    @Override
    public Timeslot getTimeslot(final long timeslotId) {
        final List<Timeslot> rows = getNamedParameterJdbcTemplate().query(SELECT_TIMESLOT,
                ParameterRepository.of("timeslotId", Long.valueOf(timeslotId)).build(),
                TIMESLOT_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Date> getCustomerBlackoutDates(final long customerId, final Date from, final Date to) {
        return getNamedParameterJdbcTemplate().query(SELECT_CUSTOMER_BLACKOUTS,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_CUSTOMER_ID, Long.valueOf(customerId))
                        .withDate(CommonConstants.PARAM_START_DATE, from)
                        .withDate(CommonConstants.PARAM_END_DATE, to)
                        .build(), DATE_MAPPER);
    }

    private static class TimeslotMapper implements RowMapper<Timeslot> {

        @Override
        public Timeslot mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final Timeslot timeslot = new Timeslot();
            timeslot.setTimeslotId(ConversionUtils.getLong(rs, "TIMESLOT_ID"));
            timeslot.setStartTime(ConversionUtils.getDate(rs, "START_TM"));
            timeslot.setEndTime(ConversionUtils.getDate(rs, "END_TM"));
            final Integer capacity = ConversionUtils.getInteger(rs, "CAPACITY");
            timeslot.setCapacity(capacity == null ? 0 : capacity.intValue());
            final Integer reserved = ConversionUtils.getInteger(rs, "RESERVED_COUNT");
            timeslot.setReserved(reserved == null ? 0 : reserved.intValue());
            timeslot.setAvailable(ConversionUtils.getBoolean(rs, "AVAILABLE_FL"));
            timeslot.setDisplayLabel(ConversionUtils.getString(rs, "DISPLAY_LABEL"));
            timeslot.setTimeZone(ConversionUtils.getString(rs, "TIME_ZONE"));
            return timeslot;
        }
    }

    /** Single column date projections, read positionally. */
    private static class DateMapper implements RowMapper<Date> {

        @Override
        public Date mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final java.sql.Timestamp value = rs.getTimestamp(1);
            return value == null ? null : ConversionUtils.truncateToDay(new Date(value.getTime()));
        }
    }
}
