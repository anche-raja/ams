package org.example.am.shared.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.example.am.shared.domain.AssetConfiguration;
import org.example.am.shared.domain.AssetConfigurationStatusType;
import org.example.am.shared.domain.AssetConfigurationType;
import org.example.am.shared.domain.DataSourceType;
import org.example.am.shared.domain.EmailEntityType;
import org.example.am.shared.domain.Event;
import org.example.am.shared.domain.EventType;
import org.example.am.shared.domain.NetworkChangeRequest;
import org.example.am.shared.domain.NetworkChangeRequestStatusType;
import org.example.am.shared.domain.NetworkChangeRequestType;
import org.example.am.shared.domain.NetworkConfigurationType;
import org.example.am.shared.domain.Rma;
import org.example.am.shared.domain.RmaStatusType;
import org.example.am.shared.domain.ShippingCarrier;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;

/**
 * Row mappers for projections that more than one DAO selects.
 *
 * <p>Mappers used by a single DAO stay private to that DAO; these are here so that the NCR, RMA and
 * event projections cannot drift between the read path and the search path.</p>
 */
final class SharedRowMappers {

    static final RowMapper<NetworkChangeRequest> NETWORK_CHANGE_REQUEST =
            new RowMapper<NetworkChangeRequest>() {

                @Override
                public NetworkChangeRequest mapRow(final ResultSet rs, final int rowNumber)
                        throws SQLException {
                    final NetworkChangeRequest request = new NetworkChangeRequest();
                    request.setNetworkChangeRequestId(ConversionUtils.getLong(rs, "NCR_ID"));
                    request.setRequestNumber(ConversionUtils.getString(rs, "REQUEST_NUMBER"));
                    request.setAssetId(ConversionUtils.getLong(rs, "ASSET_ID"));
                    request.setCustomerId(ConversionUtils.getLong(rs, "CUSTOMER_ID"));
                    request.setNetworkChangeRequestStatusType(NetworkChangeRequestStatusType
                            .lookup(ConversionUtils.getString(rs, "NCR_STATUS_CD")));
                    request.setRequestedDate(ConversionUtils.getDate(rs, "REQUESTED_DT"));
                    request.setSubmittedDate(ConversionUtils.getDate(rs, "SUBMITTED_DT"));
                    request.setScheduledDate(ConversionUtils.getDate(rs, "SCHEDULED_DT"));
                    request.setCompletedDate(ConversionUtils.getDate(rs, "COMPLETED_DT"));
                    request.setCancelledDate(ConversionUtils.getDate(rs, "CANCELLED_DT"));
                    request.setCancellationReason(ConversionUtils.getString(rs, "CANCELLATION_REASON"));
                    request.setComments(ConversionUtils.getString(rs, "COMMENTS"));
                    request.setCreatedDate(ConversionUtils.getDate(rs, "CREATED_DT"));
                    request.setModifiedDate(ConversionUtils.getDate(rs, "MODIFIED_DT"));
                    return request;
                }
            };

    /** Maps one row of the NCR type child table onto its typesafe constant. */
    static final RowMapper<NetworkChangeRequestType> NETWORK_CHANGE_REQUEST_TYPE =
            new RowMapper<NetworkChangeRequestType>() {

                @Override
                public NetworkChangeRequestType mapRow(final ResultSet rs, final int rowNumber)
                        throws SQLException {
                    return NetworkChangeRequestType.lookup(ConversionUtils.getString(rs, "NCR_TYPE_CD"));
                }
            };

    static final RowMapper<Rma> RMA = new RowMapper<Rma>() {

        @Override
        public Rma mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final Rma rma = new Rma();
            rma.setRmaId(ConversionUtils.getLong(rs, "RMA_ID"));
            rma.setRmaNumber(ConversionUtils.getString(rs, "RMA_NUMBER"));
            rma.setAssetId(ConversionUtils.getLong(rs, "ASSET_ID"));
            rma.setAssetTag(ConversionUtils.getString(rs, "ASSET_TAG"));
            rma.setTrackingNumber(ConversionUtils.getString(rs, "TRACKING_NUMBER"));
            rma.setShippingCarrier(
                    ShippingCarrier.lookup(ConversionUtils.getString(rs, "SHIPPING_CARRIER_CD")));
            rma.setRmaStatusType(RmaStatusType.lookup(ConversionUtils.getString(rs, "RMA_STATUS_CD")));
            rma.setIssuedDate(ConversionUtils.getDate(rs, "ISSUED_DT"));
            rma.setDueDate(ConversionUtils.getDate(rs, "DUE_DT"));
            rma.setReceivedDate(ConversionUtils.getDate(rs, "RECEIVED_DT"));
            rma.setReason(ConversionUtils.getString(rs, "REASON"));
            return rma;
        }
    };

    static final RowMapper<Event> EVENT = new RowMapper<Event>() {

        @Override
        public Event mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final Event event = new Event();
            event.setEventId(ConversionUtils.getLong(rs, "EVENT_ID"));
            event.setEventType(EventType.lookup(ConversionUtils.getString(rs, "EVENT_TYPE_CD")));
            event.setEventDate(ConversionUtils.getDate(rs, "EVENT_DT"));
            event.setUserId(ConversionUtils.getString(rs, "USER_ID"));
            event.setUserDisplayName(ConversionUtils.getString(rs, "USER_DISPLAY_NAME"));
            event.setDescription(ConversionUtils.getString(rs, "DESCRIPTION"));
            event.setEntityId(ConversionUtils.getLong(rs, "ENTITY_ID"));
            event.setEntityType(EmailEntityType.lookup(ConversionUtils.getString(rs, "ENTITY_TYPE_CD")));
            event.setDataSourceType(
                    DataSourceType.lookup(ConversionUtils.getString(rs, "DATA_SOURCE_CD")));
            return event;
        }
    };

    static final RowMapper<AssetConfiguration> ASSET_CONFIGURATION = new RowMapper<AssetConfiguration>() {

        @Override
        public AssetConfiguration mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final AssetConfiguration configuration = new AssetConfiguration();
            configuration.setConfigurationId(ConversionUtils.getLong(rs, "CONFIG_ID"));
            configuration.setAssetId(ConversionUtils.getLong(rs, "ASSET_ID"));
            configuration.setAssetConfigurationType(
                    AssetConfigurationType.lookup(ConversionUtils.getString(rs, "CONFIG_TYPE_CD")));
            configuration.setAssetConfigurationStatusType(AssetConfigurationStatusType
                    .lookup(ConversionUtils.getString(rs, "CONFIG_STATUS_CD")));
            configuration.setNetworkConfigurationType(NetworkConfigurationType
                    .lookup(ConversionUtils.getString(rs, "NETWORK_CONFIG_CD")));
            configuration.setLanIpAddress(ConversionUtils.getString(rs, "LAN_IP_ADDRESS"));
            configuration.setLanSubnetMask(ConversionUtils.getString(rs, "LAN_SUBNET_MASK"));
            configuration.setWanIpAddress(ConversionUtils.getString(rs, "WAN_IP_ADDRESS"));
            configuration.setWanSubnetMask(ConversionUtils.getString(rs, "WAN_SUBNET_MASK"));
            configuration.setDefaultGateway(ConversionUtils.getString(rs, "DEFAULT_GATEWAY"));
            configuration.setLanGateway(ConversionUtils.getString(rs, "LAN_GATEWAY"));
            configuration.setPrimaryDnsAddress(ConversionUtils.getString(rs, "PRIMARY_DNS"));
            configuration.setSecondaryDnsAddress(ConversionUtils.getString(rs, "SECONDARY_DNS"));
            configuration.setCircuitId(ConversionUtils.getString(rs, "CIRCUIT_ID"));
            configuration.setBandwidthKbps(ConversionUtils.getInteger(rs, "BANDWIDTH_KBPS"));
            configuration.setEffectiveDate(ConversionUtils.getDate(rs, "EFFECTIVE_DT"));
            final Integer revision = ConversionUtils.getInteger(rs, "REVISION_NUM");
            configuration.setRevision(revision == null ? 0 : revision.intValue());
            return configuration;
        }
    };

    /** Single column date projections, read positionally. */
    static final RowMapper<Date> DATE = new RowMapper<Date>() {

        @Override
        public Date mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final java.sql.Timestamp value = rs.getTimestamp(1);
            return value == null ? null : new Date(value.getTime());
        }
    };

    private SharedRowMappers() {
        super();
    }
}
