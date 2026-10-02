package org.example.am.shared.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.example.am.shared.dao.InstallationCalendarDAO;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.InstallationCalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Installation scheduling view of the calendar, resolved by the destination postcode's engineer
 * region.
 */
@Service("installationCalendarService")
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class InstallationCalendarServiceImpl implements InstallationCalendarService {

    @Autowired
    private InstallationCalendarDAO installationCalendarDAO;

    @Override
    public List<Timeslot> getSlotsForZipCode(final String zipCode, final Date from, final Date to) {
        final String regionCode = installationCalendarDAO.getRegionForZipCode(zipCode);
        if (regionCode == null) {
            return Collections.<Timeslot>emptyList();
        }
        return installationCalendarDAO.getInstallationSlots(regionCode, from, to);
    }

    @Override
    public String getRegion(final String zipCode) {
        return installationCalendarDAO.getRegionForZipCode(zipCode);
    }
}
