package org.example.am.shared.dao.scheduling;

/**
 * What a scheduling operation reports back.
 *
 * <p>The PL/SQL returned a status plus, on three of the nine procedures, one extra OUT value. This
 * carries the same shape rather than returning a bare string, so the extra values have somewhere to
 * live that the caller can ignore.</p>
 *
 * <p>The status is never {@code null}: every procedure assigned it on its first line, because
 * Oracle does not copy OUT parameters back on an unhandled exception and an unassigned one read as
 * {@code null} in Java. That hazard is gone, but the guarantee is worth keeping.</p>
 */
public final class SchedulingResult {

    private final String status;
    private Long emailId;
    private Long circuitWindowId;
    private int releasedCount;

    public SchedulingResult(final String status) {
        super();
        this.status = status == null ? SchedulingStatus.ERROR : status;
    }

    /** @return {@code true} only for {@link SchedulingStatus#OK} */
    public boolean isOk() {
        return SchedulingStatus.OK.equals(status);
    }

    public String getStatus() {
        return status;
    }

    /** @return the first genuinely queued notification, or {@code null} */
    public Long getEmailId() {
        return emailId;
    }

    SchedulingResult withEmailId(final Long emailId) {
        this.emailId = emailId;
        return this;
    }

    /** @return the circuit window taken by a site-type change, or {@code null} */
    public Long getCircuitWindowId() {
        return circuitWindowId;
    }

    SchedulingResult withCircuitWindowId(final Long circuitWindowId) {
        this.circuitWindowId = circuitWindowId;
        return this;
    }

    /** @return how many circuit windows a cancellation gave back */
    public int getReleasedCount() {
        return releasedCount;
    }

    SchedulingResult withReleasedCount(final int releasedCount) {
        this.releasedCount = releasedCount;
        return this;
    }

    @Override
    public String toString() {
        return "SchedulingResult[" + status + "]";
    }
}
