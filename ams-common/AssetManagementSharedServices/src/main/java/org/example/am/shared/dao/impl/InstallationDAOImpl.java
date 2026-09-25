package org.example.am.shared.dao.impl;

import java.util.List;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.dao.InstallationDAO;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.domain.Installation;
import org.example.am.shared.domain.InstallationStatusType;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** @see InstallationDAO */
@Repository("installationDAO")
public class InstallationDAOImpl extends BaseDAO implements InstallationDAO {

    private static final String SELECT_FOR_ORDER =
            "SELECT I.INSTALLATION_ID, I.ASSET_ID, I.ORDER_ID, I.INSTALL_STATUS_CD,"
          + "       I.SCHEDULED_DT, I.TIMESLOT_ID, I.INSTALL_ADDRESS_ID, I.INSTALL_CONTACT_ID,"
          + "       I.TECHNICIAN_NAME, I.NOTES, I.COMPLETED_DT "
          + "  FROM AMS_INSTALLATIONS I WHERE I.ORDER_ID = :orderId ";

    private static final String NEXT_INSTALLATION_ID =
            "SELECT " + CommonConstants.SEQ_INSTALLATIONS + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_INSTALLATION =
            "INSERT INTO AMS_INSTALLATIONS "
          + "       ( INSTALLATION_ID, ASSET_ID, ORDER_ID, INSTALL_STATUS_CD,"
          + "         INSTALL_ADDRESS_ID, INSTALL_CONTACT_ID,"
          + "         CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :installationId, :assetId, :orderId, 'NOTSCHED',"
          + "         :installAddressId, :installContactId,"
          + "         SYSTIMESTAMP, :userId, SYSTIMESTAMP, :userId ) ";

    private static final RowMapper<Installation> MAPPER = new InstallationMapper();

    @Override
    public Installation getInstallationForOrder(final long orderId) {
        final List<Installation> rows = getNamedParameterJdbcTemplate().query(SELECT_FOR_ORDER,
                ParameterRepository.of(CommonConstants.PARAM_ORDER_ID, Long.valueOf(orderId)).build(),
                MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public long insertInstallation(final long orderId, final Long assetId,
            final Long installAddressId, final Long installContactId, final String userId) {
        final Long installationId = getNamedParameterJdbcTemplate().queryForObject(
                NEXT_INSTALLATION_ID, ParameterRepository.create().build(), Long.class);
        getNamedParameterJdbcTemplate().update(INSERT_INSTALLATION, ParameterRepository.create()
                .with("installationId", installationId)
                .with(CommonConstants.PARAM_ASSET_ID, assetId)
                .with(CommonConstants.PARAM_ORDER_ID, Long.valueOf(orderId))
                .with("installAddressId", installAddressId)
                .with("installContactId", installContactId)
                .with(CommonConstants.PARAM_USER_ID, userId)
                .build());
        logger.info("Raised installation {} for order {}", installationId, Long.valueOf(orderId));
        return installationId.longValue();
    }

    private static final class InstallationMapper implements RowMapper<Installation> {
        @Override
        public Installation mapRow(final java.sql.ResultSet rs, final int rowNum)
                throws java.sql.SQLException {
            final Installation installation = new Installation();
            installation.setInstallationId(Long.valueOf(rs.getLong("INSTALLATION_ID")));
            installation.setOrderId(ConversionUtils.getLong(rs, "ORDER_ID"));
            installation.setInstallationStatusType(
                    InstallationStatusType.lookup(rs.getString("INSTALL_STATUS_CD")));
            installation.setScheduledDate(rs.getTimestamp("SCHEDULED_DT"));
            // Only the id: the caller reads the slot itself when it needs the label and times.
            final Long timeslotId = ConversionUtils.getLong(rs, "TIMESLOT_ID");
            if (timeslotId != null) {
                final Timeslot timeslot = new Timeslot();
                timeslot.setTimeslotId(timeslotId);
                installation.setTimeslot(timeslot);
            }
            installation.setCompletedDate(rs.getTimestamp("COMPLETED_DT"));
            installation.setTechnicianName(rs.getString("TECHNICIAN_NAME"));
            installation.setNotes(rs.getString("NOTES"));
            return installation;
        }
    }
}
