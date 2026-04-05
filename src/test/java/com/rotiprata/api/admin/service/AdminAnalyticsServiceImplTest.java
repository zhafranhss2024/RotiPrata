package com.rotiprata.api.admin.service;

import com.rotiprata.api.content.service.ContentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AdminAnalyticsServiceImpl Unit Tests - Full Coverage")
class AdminAnalyticsServiceImplTest {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    @Mock
    private ContentService contentService;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @InjectMocks
    private AdminAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initialize mocks
    }

    // ===== getFlaggedContentByMonthAndYear =====
    // Verifies flagged content is correctly aggregated by date when data exists
    @Test
    void getFlaggedContentByMonthAndYear_ShouldReturnAggregatedCounts_WhenContentExists() {
        // Arrange
        List<Map<String, Object>> rawFlags = List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z"),
            Map.of("created_at", "2026-03-01T12:00:00Z"),
            Map.of("created_at", "2026-03-02T09:00:00Z")
        );
        when(contentService.getFlaggedContentByMonthAndYear("token", "3", "2026")).thenReturn(rawFlags);

        // Act
        List<Map<String, Object>> result = service.getFlaggedContentByMonthAndYear("token", "3", "2026");

        // Assert
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).get("count"));
        assertEquals("2026-03-01", result.get(0).get("date"));
        assertEquals(1, result.get(1).get("count"));
        assertEquals("2026-03-02", result.get(1).get("date"));

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "3", "2026");
    }

    // Verifies method returns empty list when no flagged content is present
    @Test
    void getFlaggedContentByMonthAndYear_ShouldReturnEmptyList_WhenNoContentExists() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "3", "2026")).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = service.getFlaggedContentByMonthAndYear("token", "3", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "3", "2026");
    }

    // ===== getAverageReviewTimeByMonthAndYear =====
    // Checks average review time is correctly computed for resolved flags
    @Test
    void getAverageReviewTimeByMonthAndYear_ShouldComputeAverage_WhenReviewsExist() {
        // Arrange
        List<Map<String, Object>> rawFlags = List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z", "resolved_at", "2026-03-01T10:30:00Z"),
            Map.of("created_at", "2026-03-01T12:00:00Z", "resolved_at", "2026-03-01T12:45:00Z")
        );
        when(contentService.getFlaggedContentByMonthAndYear("token", "3", "2026")).thenReturn(rawFlags);

        // Act
        double avg = service.getAverageReviewTimeByMonthAndYear("token", "3", "2026");

        // Assert
        assertEquals(37.5, avg);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "3", "2026");
    }

    // Ensures 0 is returned when no flags have a resolved timestamp
    @Test
    void getAverageReviewTimeByMonthAndYear_ShouldReturnZero_WhenNoResolvedFlags() {
        // Arrange
        List<Map<String, Object>> rawFlags = List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z", "resolved_at", null),
            Map.of("created_at", "2026-03-01T12:00:00Z")
        );
        when(contentService.getFlaggedContentByMonthAndYear("token", "3", "2026")).thenReturn(rawFlags);

        // Act
        double avg = service.getAverageReviewTimeByMonthAndYear("token", "3", "2026");

        // Assert
        assertEquals(0.0, avg);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "3", "2026");
    }

     // Ensures 0 is returned when no flagged content exists
    @Test
    void getAverageReviewTimeByMonthAndYear_ShouldReturnZero_WhenNoContentExists() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "3", "2026")).thenReturn(Collections.emptyList());

        // Act
        double avg = service.getAverageReviewTimeByMonthAndYear("token", "3", "2026");

        // Assert
        assertEquals(0.0, avg);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "3", "2026");
    }

    // ===== getTopFlagUsers =====
    // Verifies top flagging users are returned when present
    @Test
    void getTopFlagUsers_ShouldReturnList_WhenUsersExist() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("user_id", 1, "flags", 5));
        when(supabaseAdminRestClient.rpcList(eq("get_top_flag_users"), any(), MAP_LIST)).thenReturn(expected);

        // Act
        List<Map<String, Object>> result = service.getTopFlagUsers("3", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_users"), any(), any());
    }

    // Verifies empty list is returned when no top users exist
    @Test
    void getTopFlagUsers_ShouldReturnEmptyList_WhenNoUsersExist() {
        // Arrange
        when(supabaseAdminRestClient.rpcList(eq("get_top_flag_users"), any(), any())).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = service.getTopFlagUsers("3", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_users"), any(), any());
    }

    // ===== getTopFlagContents =====
    // Verifies top flagged contents are returned when present
    @Test
    void getTopFlagContents_ShouldReturnList_WhenContentsExist() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("content_id", 1, "flags", 3));
        when(supabaseAdminRestClient.rpcList(eq("get_top_flag_content"), any(), MAP_LIST)).thenReturn(expected);

        // Act
        List<Map<String, Object>> result = service.getTopFlagContents("3", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_content"), any(), any());
    }

    // Verifies empty list is returned when no top flagged contents exist
    @Test
    void getTopFlagContents_ShouldReturnEmptyList_WhenNoContentsExist() {
        // Arrange
        when(supabaseAdminRestClient.rpcList(eq("get_top_flag_content"), any(), any())).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = service.getTopFlagContents("3", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_content"), any(), any());
    }

    // ===== getAuditLogs =====
    // Verifies audit logs are returned correctly when logs exist
    @Test
    void getAuditLogs_ShouldReturnList_WhenLogsExist() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("action", "delete", "user", "admin"));
        when(supabaseAdminRestClient.getList(eq("audit_logs"), anyString(), MAP_LIST)).thenReturn(expected);

        // Act
        List<Map<String, Object>> result = service.getAuditLogs("3", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("audit_logs"), anyString(), any());
    }

    // Verifies empty list is returned when no audit logs exist
    @Test
    void getAuditLogs_ShouldReturnEmptyList_WhenNoLogsExist() {
        // Arrange
        when(supabaseAdminRestClient.getList(eq("audit_logs"), anyString(), any())).thenReturn(Collections.emptyList());

        // Act
        List<Map<String, Object>> result = service.getAuditLogs("3", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("audit_logs"), anyString(), any());
    }
}