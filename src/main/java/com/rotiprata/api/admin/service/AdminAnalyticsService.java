package com.rotiprata.api.admin.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for admin analytics operations.
 * Defines methods to retrieve flagged content, compute review times,
 * get top flagged users/contents, and fetch audit logs.
 *
 * <p>All methods are best-effort: in case of an internal error,
 * they return empty lists or 0 rather than throwing exceptions.
 */
public interface AdminAnalyticsService {

    /**
     * Retrieves flagged content aggregated by day for a given month and year.
     * Best-effort: returns an empty list if an error occurs.
     *
     * @param accessToken the user access token
     * @param month the month in "MM" format
     * @param year the year in "YYYY" format
     * @return a list of maps with keys "date" (YYYY-MM-DD) and "count"
     */
    List<Map<String, Object>> getFlaggedContentByMonthAndYear(UUID adminUserId, String accessToken, String month, String year);

    /**
     * Computes the average review time (in minutes) for flagged content in a given month and year.
     * Only considers resolved flags.
     * Best-effort: returns 0 if no resolved flags exist or an error occurs.
     *
     * @param accessToken the user access token
     * @param month the month in "MM" format
     * @param year the year in "YYYY" format
     * @return average review time in minutes, or 0 on failure
     */
    double getAverageReviewTimeByMonthAndYear(UUID adminUserId, String accessToken, String month, String year);

    /**
     * Retrieves the top users who flagged content in a given month and year.
     * Best-effort: returns an empty list if an error occurs.
     *
     * @param month the month in "MM" format
     * @param year the year in "YYYY" format
     * @return a list of maps representing top users and their flag counts
     */
    List<Map<String, Object>> getTopFlagUsers(UUID adminUserId, String accessToken, String month, String year);

    /**
     * Retrieves the top flagged contents for a given month and year.
     * Best-effort: returns an empty list if an error occurs.
     *
     * @param month the month in "MM" format
     * @param year the year in "YYYY" format
     * @return a list of maps representing top flagged contents
     */
    List<Map<String, Object>> getTopFlagContents(UUID adminUserId, String accessToken, String month, String year);

    /**
     * Fetches audit logs for a given month and year, including user display names.
     * Best-effort: returns an empty list if an error occurs.
     *
     * @param month the month in "MM" format
     * @param year the year in "YYYY" format
     * @return a list of audit log entries
     */
    List<Map<String, Object>> getAuditLogs(UUID adminUserId, String accessToken, String month, String year);
}
