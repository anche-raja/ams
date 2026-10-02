package org.example.am.shared.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.dao.InstallationCalendarDAO;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Installation-specific view of the calendar: which engineer regions have capacity on which day.
 */
@Repository("installationCalendarSharedDAO")
public class InstallationCalendarDAOImpl extends BaseDAO implements InstallationCalendarDAO {

    private static final String SELECT_INSTALL_SLOTS =
            "SELECT T.TIMESLOT_ID, T.START_TM, T.END_TM, T.CAPACITY, T.RESERVED_COUNT,"
          + "       T.AVAILABLE_FL, T.DISPLAY_LABEL, T.TIME_ZONE "
          + "  FROM AMS_TIMESLOTS T "
          + " WHERE T.CALL_TYPE_CD = 'INSTALL' "
          + "   AND T.REGION_CD = :regionCode "
          + "   AND T.START_TM BETWEEN :startDate AND :endDate "
          + "   AND T.AVAILABLE_FL = 'Y' "
          + "   AND T.RESERVED_COUNT < T.CAPACITY "
          + " ORDER BY T.START_TM ";

    private static final String SELECT_REGION =
            "SELECT R.REGION_CD FROM AMS_INSTALL_REGIONS R WHERE R.ZIP_CODE = :zipCode ";

    private static final RowMapper<Timeslot> INSTALL_SLOT_MAPPER = new RowMapper<Timeslot>() {

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
    };

    private static final RowMapper<String> REGION_MAPPER = new RowMapper<String>() {

        @Override
        public String mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            return ConversionUtils.getString(rs, "REGION_CD");
        }
    };

    @Override
    public List<Timeslot> getInstallationSlots(final String regionCode, final Date from, final Date to) {
        return getNamedParameterJdbcTemplate().query(SELECT_INSTALL_SLOTS,
                ParameterRepository.create()
                        .with("regionCode", regionCode)
                        .withDate(CommonConstants.PARAM_START_DATE, from)
                        .withDate(CommonConstants.PARAM_END_DATE, to)
                        .build(), INSTALL_SLOT_MAPPER);
    }

    @Override
    public String getRegionForZipCode(final String zipCode) {
        final List<String> rows = getNamedParameterJdbcTemplate().query(SELECT_REGION,
                ParameterRepository.of("zipCode", zipCode).build(), REGION_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
