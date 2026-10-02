package org.example.am.internal.service;

import java.util.Date;

import org.example.am.internal.service.report.ActivityReport;

/**
 * Builds the order activity report: every customer an operator can order for, with their orders
 * counted by state and the oldest open one aged.
 */
public interface OrderActivityService {

    /**
     * @param asOf the moment to measure ages against; {@code null} means now
     * @return the report, never {@code null}, with its lines sorted so the customers with the most
     *         open orders come first
     */
    ActivityReport buildReport(Date asOf);
}
