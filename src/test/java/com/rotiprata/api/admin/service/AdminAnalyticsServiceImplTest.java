package com.rotiprata.api.admin.service;

import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnalyticsServiceImpl tests")
class AdminAnalyticsServiceImplTest {

    @Mock
    private ContentService contentService;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @Mock
    private UserService userService;

    private AdminAnalyticsServiceImpl service;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        service = new AdminAnalyticsServiceImpl(contentService, supabaseAdminRestClient, userService);
        adminUserId = UUID.randomUUID();
        lenient().when(userService.getRoles(adminUserId, "token")).thenReturn(List.of(AppRole.ADMIN));
    }

    // ===== getFlaggedContentByMonthAndYear =====
    // Verifies flagged content is correctly aggregated by date when data exists.
    @Test
    void getFlaggedContentByMonthAndYear_shouldAggregateCountsByDate() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z"),
            Map.of("created_at", "2026-03-01T12:00:00Z"),
            Map.of("created_at", "2026-03-02T09:00:00Z")
        ));

        // Act
        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(2, result.size());
        assertEquals("2026-03-01", result.get(0).get("date"));
        assertEquals(2L, ((Number) result.get(0).get("count")).longValue());
        assertEquals("2026-03-02", result.get(1).get("date"));
        assertEquals(1L, ((Number) result.get(1).get("count")).longValue());

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Verifies method returns empty list when no flagged content is present.
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnEmptyList_whenNoContentExists() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of());

        // Act
        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Ignores malformed timestamps and counts only valid flagged content rows.
    @Test
    void getFlaggedContentByMonthAndYear_shouldSkipInvalidTimestamps() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "invalid-timestamp"),
            Map.of("created_at", "2026-03-01T10:00:00Z")
        ));

        // Act
        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(1, result.size());
        assertEquals("2026-03-01", result.get(0).get("date"));
        assertEquals(1L, ((Number) result.get(0).get("count")).longValue());

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // ===== getAverageReviewTimeByMonthAndYear =====
    // Checks average review time is correctly computed for resolved flags.
    @Test
    void getAverageReviewTimeByMonthAndYear_shouldIgnoreUnresolvedRows() {
        // Arrange
        Map<String, Object> unresolved = new HashMap<>();
        unresolved.put("created_at", "2026-03-01T10:00:00Z");
        unresolved.put("resolved_at", null);

        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "2026-03-01T12:00:00Z", "resolved_at", "2026-03-01T12:45:00Z"),
            unresolved
        ));

        // Act
        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(45.0, result);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Checks average review time across multiple resolved flags.
    @Test
    void getAverageReviewTimeByMonthAndYear_shouldComputeAverage_whenReviewsExist() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z", "resolved_at", "2026-03-01T10:30:00Z"),
            Map.of("created_at", "2026-03-01T12:00:00Z", "resolved_at", "2026-03-01T12:45:00Z")
        ));

        // Act
        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(37.5, result);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Ensures 0 is returned when no flags have a resolved timestamp.
    @Test
    void getAverageReviewTimeByMonthAndYear_shouldReturnZero_whenNoResolvedFlags() {
        // Arrange
        Map<String, Object> unresolved = new HashMap<>();
        unresolved.put("created_at", "2026-03-01T10:00:00Z");
        unresolved.put("resolved_at", null);

        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            unresolved,
            Map.of("created_at", "2026-03-01T12:00:00Z")
        ));

        // Act
        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(0.0, result);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Ensures 0 is returned when no flagged content exists.
    @Test
    void getAverageReviewTimeByMonthAndYear_shouldReturnZero_whenNoContentExists() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of());

        // Act
        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(0.0, result);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // ===== RPC-backed analytics =====
    // Verifies top flagging users are returned when present.
    @Test
    void getTopFlagUsers_shouldDelegateToRpc() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("user_id", "user-1", "flag_count", 5));
        doReturn(expected).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());

        // Act
        List<Map<String, Object>> result = service.getTopFlagUsers(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());
    }

    // Verifies empty list is returned when no top users exist.
    @Test
    void getTopFlagUsers_shouldReturnEmptyList_whenNoUsersExist() {
        // Arrange
        doReturn(List.of()).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());

        // Act
        List<Map<String, Object>> result = service.getTopFlagUsers(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());
    }

    // Verifies top flagged contents are returned when present.
    @Test
    void getTopFlagContents_shouldDelegateToRpc() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("content_id", "content-1", "flag_count", 3));
        doReturn(expected).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());

        // Act
        List<Map<String, Object>> result = service.getTopFlagContents(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());
    }

    // Verifies empty list is returned when no top flagged contents exist.
    @Test
    void getTopFlagContents_shouldReturnEmptyList_whenNoContentsExist() {
        // Arrange
        doReturn(List.of()).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());

        // Act
        List<Map<String, Object>> result = service.getTopFlagContents(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());
    }

    // Verifies audit logs are returned correctly when logs exist.
    @Test
    void getAuditLogs_shouldQueryAuditLogTable() {
        // Arrange
        List<Map<String, Object>> expected = List.of(Map.of("action", "UPDATE_CONTENT"));
        doReturn(expected).when(supabaseAdminRestClient).getList(eq("audit_logs"), anyString(), any());

        // Act
        List<Map<String, Object>> result = service.getAuditLogs(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(expected, result);

        // Verify
        verify(supabaseAdminRestClient).getList(eq("audit_logs"), contains("select="), any());
    }

    // Verifies empty list is returned when no audit logs exist.
    @Test
    void getAuditLogs_shouldReturnEmptyList_whenNoLogsExist() {
        // Arrange
        doReturn(List.of()).when(supabaseAdminRestClient).getList(eq("audit_logs"), anyString(), any());

        // Act
        List<Map<String, Object>> result = service.getAuditLogs(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient).getList(eq("audit_logs"), contains("select="), any());
    }

    // ===== Authorization and best-effort handling =====
    // Ensures analytics methods reject users without the ADMIN role.
    @Test
    void analyticsEndpoints_shouldRejectNonAdminUsers() {
        // Arrange
        UUID nonAdminUserId = UUID.randomUUID();
        when(userService.getRoles(nonAdminUserId, "token")).thenReturn(List.of(AppRole.USER));

        // Act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> service.getFlaggedContentByMonthAndYear(nonAdminUserId, "token", "03", "2026")
        );

        // Assert
        assertEquals(403, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Admin role required"));
    }

    // Returns an empty list instead of propagating service exceptions.
    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnEmptyList_whenServiceThrows() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("service error"));

        // Act
        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Returns zero instead of propagating service exceptions.
    @Test
    void getAverageReviewTimeByMonthAndYear_shouldReturnZero_whenServiceThrows() {
        // Arrange
        when(contentService.getFlaggedContentByMonthAndYear(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("service error"));

        // Act
        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        // Assert
        assertEquals(0.0, result);

        // Verify
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    // Returns an empty list instead of propagating RPC exceptions for top users.
    @Test
    void getTopFlagUsers_shouldReturnEmptyList_whenRpcThrows() {
        // Arrange
        when(supabaseAdminRestClient.rpcList(anyString(), any(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        // Act
        List<Map<String, Object>> result = service.getTopFlagUsers(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_users"), any(), any());
    }

    // Returns an empty list instead of propagating RPC exceptions for top content.
    @Test
    void getTopFlagContents_shouldReturnEmptyList_whenRpcThrows() {
        // Arrange
        when(supabaseAdminRestClient.rpcList(anyString(), any(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        // Act
        List<Map<String, Object>> result = service.getTopFlagContents(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_content"), any(), any());
    }

    // Returns an empty list instead of propagating audit log query exceptions.
    @Test
    void getAuditLogs_shouldReturnEmptyList_whenRpcThrows() {
        // Arrange
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        // Act
        List<Map<String, Object>> result = service.getAuditLogs(adminUserId, "token", "03", "2026");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("audit_logs"), contains("select="), any());
    }
}
