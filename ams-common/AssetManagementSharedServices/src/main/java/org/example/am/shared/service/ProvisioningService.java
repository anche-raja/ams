package org.example.am.shared.service;

import org.example.am.shared.domain.Asset;
import org.example.am.shared.domain.AssetType;

/**
 * Takes a placed order through to a live asset.
 *
 * <p>Two steps, because in the real world they happen days apart and the states in between are
 * what operations needs to see: the warehouse despatches the box, then an engineer fits it. The
 * calendar booking sits between the two and is unchanged - it already moves the order to
 * {@code SCHEDULED} through the timeslot scheduling DAO.</p>
 *
 * <pre>
 *   order SUBMITTED  --despatch-->  SHIPPED    asset ORDERED -> SHIPPED, installation NOTSCHED
 *                    --book-->      SCHEDULED  (existing calendar)
 *                    --complete-->  COMPLETED  asset ACTIVE, configuration APPLIED
 * </pre>
 */
public interface ProvisioningService {

    /**
     * Creates the asset the order was for and marks the order despatched.
     *
     * <p>The order's configuration and maintenance window were written against the order because no
     * asset existed yet; despatch is where they are re-pointed at the device they describe.</p>
     *
     * <p>The hardware type is an input rather than something read off the order: the ordering flow
     * never asks which model, and the warehouse is the first point at which anybody knows what came
     * off the shelf.</p>
     *
     * @return the asset created, with its generated tag and serial
     * @throws IllegalStateException when the order is not in a state that can be despatched
     */
    Asset despatchOrder(long customerId, long orderId, AssetType assetType, String trackingNumber,
            String userId);

    /**
     * Records the visit as done: asset live, configuration applied, order closed.
     *
     * @return {@code true} when this call completed it, {@code false} when it was already closed -
     *         so a resubmitted form reports honestly instead of pretending to have done the work
     */
    boolean completeInstallation(long customerId, long orderId, String technicianName, String notes,
            String userId);
}
