package com.rotiprata.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.rotiprata.application.AnalyticsService;

import java.util.Map;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;
    
    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
    
    @GetMapping("/flags")
    public List<Map<String, Object>> getFlaggedContentByMonthAndYear
    (
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam String month,
        @RequestParam String year
    ) {
        // parse month/year
        int m = Integer.parseInt(month);
        int y = Integer.parseInt(year);

        LocalDate today = LocalDate.now();
        if (y > today.getYear() || (y == today.getYear() && m > today.getMonthValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request future month");
        }

        String accessToken = jwt.getTokenValue();
        return analyticsService.getFlaggedContentByMonthAndYear(accessToken, month, year);
    }
}
