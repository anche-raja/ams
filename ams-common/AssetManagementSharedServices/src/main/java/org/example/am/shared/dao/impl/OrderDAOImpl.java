package org.example.am.shared.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.dao.OrderDAO;
import org.example.am.shared.domain.Asset;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.OrderStatusType;
import org.example.am.shared.domain.OrderType;
import org.example.am.shared.domain.ShippingCarrier;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository("orderSharedDAO")
public class OrderDAOImpl extends BaseDAO implements OrderDAO {

    private static final String ORDER_COLUMNS =
            "  O.ORDER_ID, O.ORDER_NUMBER, O.ORDER_TYPE_CD, O.ORDER_STATUS_CD, O.CUSTOMER_ID,"
          + "  O.ASSET_ID, O.SHIPPING_CARRIER_CD, O.TRACKING_NUMBER, O.SUBMITTED_DT, O.SHIPPED_DT,"
          + "  O.REQUESTED_INSTALL_DT, O.CANCELLED_DT, O.CANCELLATION_REASON, O.CANCEL_PENALTY_FL,"
          + "  O.COMMENTS, O.CREATED_DT, O.MODIFIED_DT,"
          + "  O.DEVICE_NICKNAME, O.SHIP_ADDRESS_ID, O.ORDER_CONTACT_ID, O.SHIP_CONTACT_ID,"
          + "  O.INSTALL_CONTACT_ID, O.MAINT_WINDOW_ID, O.CONFIG_ID, O.SHIP_WINDOW_ID ";

    private static final String SELECT_ORDER =
            "SELECT " + ORDER_COLUMNS
          + "  FROM AMS_ORDERS O "
          + " WHERE O.CUSTOMER_ID = :customerId AND O.ORDER_ID = :orderId ";

    private static final String SELECT_ORDER_BY_NUMBER =
            "SELECT " + ORDER_COLUMNS
          + "  FROM AMS_ORDERS O "
          + " WHERE UPPER(O.ORDER_NUMBER) = UPPER(:orderNumber) ";

    private static final String SELECT_ORDERS_FOR_CUSTOMER =
            "SELECT " + ORDER_COLUMNS
          + "  FROM AMS_ORDERS O "
          + " WHERE O.CUSTOMER_ID = :customerId "
          + " ORDER BY O.SUBMITTED_DT DESC, O.ORDER_ID DESC ";

    private static final String SELECT_OPEN_ORDERS_FOR_CUSTOMER =
            "SELECT " + ORDER_COLUMNS
          + "  FROM AMS_ORDERS O "
          + " WHERE O.CUSTOMER_ID = :customerId "
          + "   AND O.ORDER_STATUS_CD NOT IN ('COMPLETED', 'CANCELLED') "
          + " ORDER BY O.SUBMITTED_DT DESC, O.ORDER_ID DESC ";

    private static final String NEXT_ORDER_ID =
            "SELECT " + CommonConstants.SEQ_ORDERS + ".NEXTVAL FROM DUAL ";

    private static final String INSERT_ORDER =
            "INSERT INTO AMS_ORDERS "
          + "       ( ORDER_ID, ORDER_NUMBER, ORDER_TYPE_CD, ORDER_STATUS_CD, CUSTOMER_ID,"
          + "         ASSET_ID, SHIPPING_CARRIER_CD, REQUESTED_INSTALL_DT, SUBMITTED_DT, COMMENTS,"
          + "         DEVICE_NICKNAME, SHIP_ADDRESS_ID, ORDER_CONTACT_ID, SHIP_CONTACT_ID,"
          + "         INSTALL_CONTACT_ID,"
          + "         CREATED_DT, CREATED_BY, MODIFIED_DT, MODIFIED_BY ) "
          + "VALUES ( :orderId, :orderNumber, :orderTypeCode, :statusCode, :customerId,"
          + "         :assetId, :shippingCarrierCode, :requestedInstallDate, :submittedDate, :comments,"
          + "         :deviceNickname, :shipAddressId, :orderContactId, :shipContactId,"
          + "         :installContactId,"
          + "         SYSTIMESTAMP, :userId, SYSTIMESTAMP, :userId ) ";

    /**
     * Links the order to the three things that can only exist once it does.
     *
     * <p>The maintenance window and the configuration both carry the order id as their own foreign
     * key, so neither row can be written until the order has been inserted. The despatch window is
     * here for a different reason: reserving it needs the order id as the reservation's entity, and
     * writing SHIP_WINDOW_ID before the reservation succeeded would leave the order claiming a
     * window the ledger does not back.</p>
     */
    private static final String LINK_ORDER_ARTIFACTS =
            "UPDATE AMS_ORDERS "
          + "   SET MAINT_WINDOW_ID = :maintenanceWindowId, CONFIG_ID = :configurationId,"
          + "       SHIP_WINDOW_ID = :shipWindowId,"
          + "       MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE ORDER_ID = :orderId ";

    private static final RowMapper<Order> ORDER_MAPPER = new OrderMapper();

    @Override
    public Order getOrder(final long customerId, final long orderId) {
        final List<Order> rows = getNamedParameterJdbcTemplate().query(SELECT_ORDER,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_CUSTOMER_ID, Long.valueOf(customerId))
                        .with(CommonConstants.PARAM_ORDER_ID, Long.valueOf(orderId))
                        .build(), ORDER_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public Order getOrderByOrderNumber(final String orderNumber) {
        if (orderNumber == null || orderNumber.trim().length() == 0) {
            return null;
        }
        final List<Order> rows = getNamedParameterJdbcTemplate().query(SELECT_ORDER_BY_NUMBER,
                ParameterRepository.of("orderNumber", orderNumber.trim()).build(), ORDER_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Order> getOrdersForCustomer(final long customerId) {
        return getNamedParameterJdbcTemplate().query(SELECT_ORDERS_FOR_CUSTOMER,
                ParameterRepository.of(CommonConstants.PARAM_CUSTOMER_ID, Long.valueOf(customerId))
                        .build(), ORDER_MAPPER);
    }

    @Override
    public List<Order> getOpenOrdersForCustomer(final long customerId) {
        return getNamedParameterJdbcTemplate().query(SELECT_OPEN_ORDERS_FOR_CUSTOMER,
                ParameterRepository.of(CommonConstants.PARAM_CUSTOMER_ID, Long.valueOf(customerId))
                        .build(), ORDER_MAPPER);
    }

    @Override
    public long insertOrder(final Order order, final String userId) {
        final Long orderId = getNamedParameterJdbcTemplate().queryForObject(NEXT_ORDER_ID,
                ParameterRepository.create().build(), Long.class);
        getNamedParameterJdbcTemplate().update(INSERT_ORDER,
                ParameterRepository.create()
                        .with(CommonConstants.PARAM_ORDER_ID, orderId)
                        .with("orderNumber", order.getOrderNumber())
                        .with("orderTypeCode", code(order.getOrderType()))
                        .with(CommonConstants.PARAM_STATUS_CODE, code(order.getOrderStatusType()))
                        .with(CommonConstants.PARAM_CUSTOMER_ID, order.getCustomerId())
                        .with(CommonConstants.PARAM_ASSET_ID,
                                order.getAsset() == null ? null : order.getAsset().getAssetId())
                        .with("shippingCarrierCode", code(order.getShippingCarrier()))
                        .withDate("requestedInstallDate", order.getRequestedInstallationDate())
                        .withDate("submittedDate", order.getSubmittedDate())
                        .with("comments", order.getComments())
                        .with("deviceNickname", order.getDeviceNickname())
                        .with("shipAddressId", order.getShipAddressId())
                        .with("orderContactId", order.getOrderingContactId())
                        .with("shipContactId", order.getShippingContactId())
                        .with("installContactId", order.getInstallationContactId())
                        .with(CommonConstants.PARAM_USER_ID, userId)
                        .build());
        order.setOrderId(orderId);
        logger.info("Inserted order {} ({}) for customer {}", orderId, order.getOrderNumber(),
                order.getCustomerId());
        return orderId.longValue();
    }

    @Override
    public int linkOrderArtifacts(final long orderId, final Long maintenanceWindowId,
            final Long configurationId, final Long shippingWindowId, final String userId) {
        return getNamedParameterJdbcTemplate().update(LINK_ORDER_ARTIFACTS,
                ParameterRepository.create()
                        .with("maintenanceWindowId", maintenanceWindowId)
                        .with("configurationId", configurationId)
                        .with("shipWindowId", shippingWindowId)
                        .with(CommonConstants.PARAM_USER_ID, userId)
                        .with(CommonConstants.PARAM_ORDER_ID, Long.valueOf(orderId))
                        .build());
    }

    private static String code(final org.example.am.shared.domain.LoadableType type) {
        return type == null ? null : type.getCode();
    }

    private static class OrderMapper implements RowMapper<Order> {

        @Override
        public Order mapRow(final ResultSet rs, final int rowNumber) throws SQLException {
            final Order order = new Order();
            order.setOrderId(ConversionUtils.getLong(rs, "ORDER_ID"));
            order.setOrderNumber(ConversionUtils.getString(rs, "ORDER_NUMBER"));
            order.setOrderType(OrderType.lookup(ConversionUtils.getString(rs, "ORDER_TYPE_CD")));
            order.setOrderStatusType(
                    OrderStatusType.lookup(ConversionUtils.getString(rs, "ORDER_STATUS_CD")));
            order.setCustomerId(ConversionUtils.getLong(rs, "CUSTOMER_ID"));

            // ASSET_ID has always been selected but was never read, so getAsset() was null even on
            // an order that plainly had one. Only the id is populated here: the rest of the asset
            // is a separate read, and callers that need it fetch it through AssetDAO.
            final Long assetId = ConversionUtils.getLong(rs, "ASSET_ID");
            if (assetId != null) {
                final Asset asset = new Asset();
                asset.setAssetId(assetId);
                order.setAsset(asset);
            }
            order.setShippingCarrier(
                    ShippingCarrier.lookup(ConversionUtils.getString(rs, "SHIPPING_CARRIER_CD")));
            order.setTrackingNumber(ConversionUtils.getString(rs, "TRACKING_NUMBER"));
            order.setSubmittedDate(ConversionUtils.getDate(rs, "SUBMITTED_DT"));
            order.setShippedDate(ConversionUtils.getDate(rs, "SHIPPED_DT"));
            order.setRequestedInstallationDate(ConversionUtils.getDate(rs, "REQUESTED_INSTALL_DT"));
            order.setCancelledDate(ConversionUtils.getDate(rs, "CANCELLED_DT"));
            order.setCancellationReason(ConversionUtils.getString(rs, "CANCELLATION_REASON"));
            order.setCancelledWithPenalty(ConversionUtils.getBoolean(rs, "CANCEL_PENALTY_FL"));
            order.setComments(ConversionUtils.getString(rs, "COMMENTS"));
            order.setCreatedDate(ConversionUtils.getDate(rs, "CREATED_DT"));
            order.setModifiedDate(ConversionUtils.getDate(rs, "MODIFIED_DT"));
            order.setDeviceNickname(ConversionUtils.getString(rs, "DEVICE_NICKNAME"));
            // Ids only. The objects they point at are read by OrderServiceImpl.getOrderDetail,
            // and only when a caller actually needs them.
            order.setShipAddressId(ConversionUtils.getLong(rs, "SHIP_ADDRESS_ID"));
            order.setOrderingContactId(ConversionUtils.getLong(rs, "ORDER_CONTACT_ID"));
            order.setShippingContactId(ConversionUtils.getLong(rs, "SHIP_CONTACT_ID"));
            order.setInstallationContactId(ConversionUtils.getLong(rs, "INSTALL_CONTACT_ID"));
            order.setMaintenanceWindowId(ConversionUtils.getLong(rs, "MAINT_WINDOW_ID"));
            order.setConfigurationId(ConversionUtils.getLong(rs, "CONFIG_ID"));
            order.setShippingWindowTimeslotId(ConversionUtils.getLong(rs, "SHIP_WINDOW_ID"));
            return order;
        }
    }
}
