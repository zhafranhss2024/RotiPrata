package com.rotiprata.api.generalutils;

import java.util.Map;

public class DateUtils {
    public static String nextMonth(String yearStr, String monthStr) {
        int year = Integer.parseInt(yearStr);
        int month = Integer.parseInt(monthStr);
        if (month == 12) return (year + 1) + "-01-01";
        return year + "-" + String.format("%02d", month + 1) + "-01";
    }

    public static String buildDateQuery(String month, String year) {
        String start = String.format("%s-%s-01T00:00:00Z", year, month);
        String end = String.format("%sT00:00:00Z", nextMonth(year, month));
        return String.format("created_at=gte.%s&created_at=lt.%s", start, end);
    }
    
     public static Map<String, Object> buildMonthYearParams(String month, String year) {
        int monthInt = Integer.parseInt(month);
        int yearInt  = Integer.parseInt(year);
        return Map.of("p_month", monthInt, "p_year", yearInt);
    }
}
