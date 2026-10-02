package org.example.am.shared.service.impl;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.am.shared.dao.ConfigDAO;
import org.example.am.shared.domain.PropertyType;
import org.example.am.shared.service.ConfigService;
import org.example.am.shared.utils.CommonConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("configService")
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class ConfigServiceImpl implements ConfigService {

    private static final Logger LOGGER = LogManager.getLogger(ConfigServiceImpl.class);

    private static final String PRODUCTION_ENVIRONMENT = "PRODUCTION";

    @Autowired
    private ConfigDAO configDAO;

    @Override
    public String getString(final PropertyType propertyType, final String defaultValue) {
        final String value = configDAO.getPropertyValue(propertyType);
        return value == null ? defaultValue : value;
    }

    @Override
    public int getInt(final PropertyType propertyType, final int defaultValue) {
        final String value = configDAO.getPropertyValue(propertyType);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException notANumber) {
            // A mistyped property must not take the screen down; fall back and say so loudly.
            LOGGER.warn("Property {} holds '{}', which is not a number; using default {}",
                    propertyType.getCode(), value, Integer.valueOf(defaultValue));
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(final PropertyType propertyType, final boolean defaultValue) {
        final String value = configDAO.getPropertyValue(propertyType);
        if (value == null) {
            return defaultValue;
        }
        final String trimmed = value.trim();
        if (CommonConstants.YES.equalsIgnoreCase(trimmed) || "TRUE".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if (CommonConstants.NO.equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed)) {
            return false;
        }
        LOGGER.warn("Property {} holds '{}', which is not a flag; using default {}",
                propertyType.getCode(), value, Boolean.valueOf(defaultValue));
        return defaultValue;
    }

    @Override
    public Map<String, String> getAllProperties() {
        return configDAO.getAllProperties();
    }

    @Override
    public boolean isNonProductionEnvironment() {
        return !PRODUCTION_ENVIRONMENT.equalsIgnoreCase(getEnvironmentName());
    }

    @Override
    public String getEnvironmentName() {
        return getString(PropertyType.ENVIRONMENT_NAME, PRODUCTION_ENVIRONMENT);
    }

    public void setConfigDAO(final ConfigDAO configDAO) {
        this.configDAO = configDAO;
    }
}
