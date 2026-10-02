package org.example.am.shared.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.am.shared.dao.BaseDAO;
import org.example.am.shared.dao.ConfigDAO;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.helper.ParameterRepository;
import org.example.am.shared.utils.CommonConstants;
import org.example.am.shared.utils.ConversionUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository("configSharedDAO")
public class ConfigDAOImpl extends BaseDAO implements ConfigDAO {

    private static final String SELECT_PROPERTY =
            "SELECT P.PROPERTY_VALUE "
          + "  FROM AMS_PROPERTIES P "
          + " WHERE P.PROPERTY_KEY = :propertyKey ";

    private static final String SELECT_ALL_PROPERTIES =
            "SELECT P.PROPERTY_KEY, P.PROPERTY_VALUE "
          + "  FROM AMS_PROPERTIES P "
          + " ORDER BY P.PROPERTY_KEY ";

    private static final String UPDATE_PROPERTY =
            "UPDATE AMS_PROPERTIES "
          + "   SET PROPERTY_VALUE = :propertyValue, MODIFIED_DT = SYSTIMESTAMP, MODIFIED_BY = :userId "
          + " WHERE PROPERTY_KEY = :propertyKey ";

    @Override
    public String getPropertyValue(final PropertyType propertyType) {
        if (propertyType == null) {
            return null;
        }
        try {
            return getNamedParameterJdbcTemplate().queryForObject(SELECT_PROPERTY,
                    ParameterRepository.of("propertyKey", propertyType.getCode()).build(),
                    String.class);
        } catch (final EmptyResultDataAccessException notConfigured) {
            logger.debug("Property {} is not configured", propertyType.getCode());
            return null;
        }
    }

    @Override
    public Map<String, String> getAllProperties() {
        final Map<String, String> properties = new LinkedHashMap<String, String>();
        getNamedParameterJdbcTemplate().query(SELECT_ALL_PROPERTIES,
                ParameterRepository.create().build(), new RowCallbackHandler() {

                    @Override
                    public void processRow(final ResultSet rs) throws SQLException {
                        properties.put(ConversionUtils.getString(rs, "PROPERTY_KEY"),
                                ConversionUtils.getString(rs, "PROPERTY_VALUE"));
                    }
                });
        return properties;
    }

    @Override
    public int updateProperty(final PropertyType propertyType, final String value, final String userId) {
        return getNamedParameterJdbcTemplate().update(UPDATE_PROPERTY,
                ParameterRepository.create()
                        .with("propertyValue", value)
                        .with(CommonConstants.PARAM_USER_ID, userId)
                        .with("propertyKey", propertyType == null ? null : propertyType.getCode())
                        .build());
    }
}
