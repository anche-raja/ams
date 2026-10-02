package org.example.am.internal.web.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.example.am.internal.security.SecurityRoleType;
import org.example.am.internal.web.model.InstallOrderModel;
import org.example.am.shared.domain.Order;
import org.example.am.shared.domain.Timeslot;
import org.example.am.shared.service.InstallationCalendarService;
import org.example.am.shared.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.opensymphony.xwork2.Action;

/**
 * Step 3, the last one: the installation appointment, and the button that places the order.
 *
 * <p>The slots on offer come from the engineer region serving the site's ZIP code, no earlier than
 * the order's lead time allows. The page loads them as JSON through {@link #listSlots()}, so the
 * choice reflects capacity at the moment the page is looked at rather than when it was rendered.</p>
 *
 * <p>The slot is not held while the user looks at it. It is reserved at the moment the order is
 * placed, which is the only point at which holding an engineer's time is justified. A ZIP code that
 * no region covers yields no slots; that is a gap in the map rather than an error, so the order can
 * still be placed and operations book the visit by hand.</p>
 */
@Component("InstallAppointmentAction")
@Scope("prototype")
public class InstallAppointmentAction extends InstallOrderBaseAction {

    private static final long serialVersionUID = 1L;

    /** How far past the earliest date slots are offered. */
    private static final int BOOKING_HORIZON_DAYS = 30;

    private static final FastDateFormat DAY_FORMAT = FastDateFormat.getInstance("EEE d MMM yyyy");

    private static final int MAX_COMMENTS_LENGTH = 2000;

    @Autowired
    private transient InstallationCalendarService installationCalendarService;

    @Autowired
    private transient OrderService orderService;

    private String region;
    private Date earliestDate;
    private Map<String, Object> availability = Collections.<String, Object>emptyMap();

    /** Set once the order has been placed, for the redirect to the confirmation. */
    private Long placedOrderId;
    private boolean appointmentLost;

    public String initAppointment() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkStepReached(model, InstallOrderModel.STEP_APPOINTMENT);
        if (stop != null) {
            return stop;
        }
        loadRegion(model);
        return Action.SUCCESS;
    }

    /**
     * The slot picker's data: JSON, behind the AJAX token stack.
     *
     * <p>Shaped here as plain maps rather than serialising {@link Timeslot} as it stands, so the
     * browser receives display-ready values and nothing about capacity it has no use for.</p>
     */
    public String listSlots() throws Exception {
        final InstallOrderModel model = getModel();
        final String stop = checkStepReached(model, InstallOrderModel.STEP_APPOINTMENT);
        if (stop != null) {
            availability = Collections.<String, Object>singletonMap("detail",
                    "This order can no longer be scheduled. Reload the page.");
            return Action.ERROR;
        }
        loadRegion(model);

        final List<Map<String, Object>> slots = new ArrayList<Map<String, Object>>();
        for (final Timeslot slot : offeredSlots(model)) {
            final Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("timeslotId", slot.getTimeslotId());
            row.put("day", DAY_FORMAT.format(slot.getStartTime()));
            row.put("label", slot.getDisplayLabel());
            row.put("placesLeft", Integer.valueOf(slot.getRemainingCapacity()));
            row.put("selected", Boolean.valueOf(
                    slot.getTimeslotId().equals(model.getInstallationTimeslotId())));
            slots.add(row);
        }
        final Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("region", region);
        body.put("earliestDate", DAY_FORMAT.format(earliestDate));
        body.put("slots", slots);
        availability = body;
        return Action.SUCCESS;
    }

    /**
     * Places the order.
     *
     * <p>There is no review screen in front of this, so this is the one place the whole model is
     * checked. Everything the earlier steps established is re-validated rather than trusted: the
     * model has been sitting in a session, and a hand-made POST can reach this action without
     * having walked the steps at all.</p>
     */
    public String placeOrder() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_SUBMIT_ORDER);
        if (denied != null) {
            return denied;
        }
        final InstallOrderModel model = getModel();
        final String stop = checkStepReached(model, InstallOrderModel.STEP_APPOINTMENT);
        if (stop != null) {
            return stop;
        }
        loadRegion(model);
        if (!validateStepsComplete(model) || !validateChoice(model)) {
            return Action.INPUT;
        }

        final Order order = model.toOrder();
        order.setCurrentTime(getCurrentTime());
        order.setOrderNumber(generateOrderNumber());
        placedOrderId = Long.valueOf(orderService.placeInstallOrder(order, getUserId()));

        // The slot can fill between choosing it and pressing the button. The order stands, but the
        // confirmation has to say so; carried on the redirect, since an action message would not
        // survive it.
        appointmentLost = model.getInstallationTimeslotId() != null && !order.isInstallationScheduled();
        logger.info("User {} placed install order {} ({})", getUserId(), order.getOrderNumber(),
                appointmentLost ? "appointment lost" : "scheduled");

        // The session copy has served its purpose; leaving it would let a refresh place it twice.
        clearModel();
        return Action.SUCCESS;
    }

    /** Throws away the order in progress. Nothing has been written, so nothing needs reversing. */
    public String abandon() throws Exception {
        final String denied = requireRole(SecurityRoleType.INT_CREATE_ORDER);
        if (denied != null) {
            return denied;
        }
        clearModel();
        return Action.SUCCESS;
    }

    /**
     * @return the human facing order number. The database key is the identity; this is what
     *         appears on paperwork and is what people quote on the phone.
     */
    private static String generateOrderNumber() {
        return "ORD-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    }

    private void loadRegion(final InstallOrderModel model) {
        region = installationCalendarService.getRegion(model.getSiteAddress().getZipCode());
        earliestDate = orderService.getEarliestInstallationDate(withClock(model.toOrder()));
    }

    private Order withClock(final Order order) {
        order.setCurrentTime(getCurrentTime());
        return order;
    }

    private List<Timeslot> offeredSlots(final InstallOrderModel model) {
        if (region == null) {
            return Collections.<Timeslot>emptyList();
        }
        return installationCalendarService.getSlotsForZipCode(model.getSiteAddress().getZipCode(),
                earliestDate, DateUtils.addDays(earliestDate, BOOKING_HORIZON_DAYS));
    }

    /**
     * Records the chosen slot, re-checked against the slots currently on offer rather than trusted
     * from the form: a posted id that is not among them is a stale page or a hand-edited one.
     * Choosing none is allowed only when there was nothing to choose from.
     */
    private boolean validateChoice(final InstallOrderModel model) {
        final String comments = StringUtils.trimToNull(model.getComments());
        if (comments != null && comments.length() > MAX_COMMENTS_LENGTH) {
            addFieldErrorAndLog("comments",
                    "Notes for the engineer must be " + MAX_COMMENTS_LENGTH + " characters or fewer.");
            return false;
        }
        model.setComments(comments);

        final List<Timeslot> offered = offeredSlots(model);
        final Long chosen = model.getInstallationTimeslotId();
        if (chosen == null) {
            if (!offered.isEmpty()) {
                addFieldErrorAndLog("installationTimeslotId", "Choose an installation appointment.");
                return false;
            }
            model.setInstallationSlot(null);
            return true;
        }
        for (final Timeslot slot : offered) {
            if (chosen.equals(slot.getTimeslotId())) {
                model.setInstallationSlot(slot);
                return true;
            }
        }
        model.setInstallationSlot(null);
        addFieldErrorAndLog("installationTimeslotId",
                "That appointment is no longer available. Choose another.");
        return false;
    }

    private boolean validateStepsComplete(final InstallOrderModel model) {
        if (!model.getSiteAddress().isComplete()) {
            addActionError("The site address is incomplete.");
            return false;
        }
        if (StringUtils.isBlank(model.getSiteContact().getLastName())) {
            addActionError("The site contact is incomplete.");
            return false;
        }
        // The flag, not the network type: the device step defaults the type on arrival, so its
        // presence only proves the screen was opened. The flag is set after the validators pass.
        if (!model.isDeviceConfirmed()) {
            addActionError("The device configuration has not been completed.");
            return false;
        }
        return true;
    }

    public String getRegion() {
        return region;
    }

    /** @return {@code true} when the site's ZIP code is not mapped to an engineer region */
    public boolean isRegionUnmapped() {
        return region == null;
    }

    public Date getEarliestDate() {
        return earliestDate;
    }

    public Map<String, Object> getAvailability() {
        return availability;
    }

    public Long getPlacedOrderId() {
        return placedOrderId;
    }

    public boolean isAppointmentLost() {
        return appointmentLost;
    }
}
