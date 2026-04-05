package com.rotiprata.api.admin.controller;

import com.rotiprata.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.rotiprata.api.admin.service.AdminAnalyticsService;

import java.util.Map;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;
    
    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
    
    @GetMapping("/flags")
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear
    (
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        validateMonthYear(month, year);

        UUID adminUserId = SecurityUtils.getUserId(jwt);
        String accessToken = jwt.getTokenValue();
        return analyticsService.getFlaggedContentByMonthAndYear(adminUserId, accessToken, month, year);
    }

    @GetMapping("/avg-review-time")
    public Map<String, Object> getAvgReviewTime(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        validateMonthYear(month, year);

        UUID adminUserId = SecurityUtils.getUserId(jwt);
        String accessToken = jwt.getTokenValue();
        double avgMinutes = analyticsService.getAverageReviewTimeByMonthAndYear(adminUserId, accessToken, month, year);

        Map<String, Object> result = new HashMap<>();
        result.put("avgReviewTime", avgMinutes);
        return result;
    }

    @GetMapping("/top-flag-users")
    public List<Map<String, Object>> getTopFlagUsers
    (
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        validateMonthYear(month, year);
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        String accessToken = jwt.getTokenValue();
        return analyticsService.getTopFlagUsers(adminUserId, accessToken, month, year);
    }

    @GetMapping("/top-flag-contents")
    public List<Map<String, Object>> getTopFlagContents
    (
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        validateMonthYear(month, year);
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        String accessToken = jwt.getTokenValue();
        return analyticsService.getTopFlagContents(adminUserId, accessToken, month, year);
    }

    @GetMapping("/audit-logs")
    public List<Map<String, Object>> getAuditLogs
    (
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        validateMonthYear(month, year);
        UUID adminUserId = SecurityUtils.getUserId(jwt);
        String accessToken = jwt.getTokenValue();
        return analyticsService.getAuditLogs(adminUserId, accessToken, month, year);
    }

    // Private Helpers

    private void validateMonthYear(String month, String year) {
        int m, y;
        try {
            m = Integer.parseInt(month);
            y = Integer.parseInt(year);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month or year", e);
        }
        if (m < 1 || m > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12");
        }
        if (y < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Year must be positive");
        }

        LocalDate today = LocalDate.now();
        if (y > today.getYear() || (y == today.getYear() && m > today.getMonthValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request future month");
        }
    }

}
