package org.example.am.shared.dao;

import org.example.am.shared.domain.Installation;

/**
 * Writes {@code AMS_INSTALLATIONS}.
 *
 * <p>An install order raises one row when it is placed: the record the engineer's visit hangs
 * off. It is written unscheduled, and the installation timeslot reservation is what moves it to
 * {@code SCHEDULED} - so a row that stays {@code NOTSCHED} is an order whose appointment was taken
 * by someone else between choosing it and pressing the button.</p>
 */
public interface InstallationDAO {

    /** @return the installation raised for this order, or {@code null} if there is none */
    Installation getInstallationForOrder(long orderId);

    /**
     * Raises the visit record for an order, unscheduled.
     *
     * @param assetId {@code null} for a new install, whose device does not exist until it is built
     * @return the generated installation id
     */
    long insertInstallation(long orderId, Long assetId, Long installAddressId, Long installContactId,
            String userId);
}
