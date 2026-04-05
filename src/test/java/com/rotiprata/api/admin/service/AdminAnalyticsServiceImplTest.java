package com.rotiprata.api.admin.service;

import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.domain.AppRole;
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

    @Test
    void getFlaggedContentByMonthAndYear_shouldAggregateCountsByDate() {
        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "2026-03-01T10:00:00Z"),
            Map.of("created_at", "2026-03-01T12:00:00Z"),
            Map.of("created_at", "2026-03-02T09:00:00Z")
        ));

        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        assertEquals(2, result.size());
        assertEquals("2026-03-01", result.get(0).get("date"));
        assertEquals(2L, ((Number) result.get(0).get("count")).longValue());
        assertEquals("2026-03-02", result.get(1).get("date"));
        assertEquals(1L, ((Number) result.get(1).get("count")).longValue());
    }

    @Test
    void getAverageReviewTimeByMonthAndYear_shouldIgnoreUnresolvedRows() {
        Map<String, Object> unresolved = new HashMap<>();
        unresolved.put("created_at", "2026-03-01T10:00:00Z");
        unresolved.put("resolved_at", null);

        when(contentService.getFlaggedContentByMonthAndYear("token", "03", "2026")).thenReturn(List.of(
            Map.of("created_at", "2026-03-01T12:00:00Z", "resolved_at", "2026-03-01T12:45:00Z"),
            unresolved
        ));

        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        assertEquals(45.0, result);
    }

    @Test
    void getTopFlagUsers_shouldDelegateToRpc() {
        List<Map<String, Object>> expected = List.of(Map.of("user_id", "user-1", "flag_count", 5));
        doReturn(expected).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());

        List<Map<String, Object>> result = service.getTopFlagUsers(adminUserId, "token", "03", "2026");

        assertEquals(expected, result);
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_users"), any(), any());
    }

    @Test
    void getTopFlagContents_shouldDelegateToRpc() {
        List<Map<String, Object>> expected = List.of(Map.of("content_id", "content-1", "flag_count", 3));
        doReturn(expected).when(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());

        List<Map<String, Object>> result = service.getTopFlagContents(adminUserId, "token", "03", "2026");

        assertEquals(expected, result);
        verify(supabaseAdminRestClient).rpcList(eq("get_top_flag_content"), any(), any());
    }

    @Test
    void getAuditLogs_shouldQueryAuditLogTable() {
        List<Map<String, Object>> expected = List.of(Map.of("action", "UPDATE_CONTENT"));
        doReturn(expected).when(supabaseAdminRestClient).getList(eq("audit_logs"), anyString(), any());

        List<Map<String, Object>> result = service.getAuditLogs(adminUserId, "token", "03", "2026");

        assertEquals(expected, result);
        verify(supabaseAdminRestClient).getList(eq("audit_logs"), contains("select="), any());
    }

    @Test
    void analyticsEndpoints_shouldRejectNonAdminUsers() {
        UUID nonAdminUserId = UUID.randomUUID();
        when(userService.getRoles(nonAdminUserId, "token")).thenReturn(List.of(AppRole.USER));

        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> service.getFlaggedContentByMonthAndYear(nonAdminUserId, "token", "03", "2026")
        );

        assertEquals(403, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Admin role required"));
    }

    @Test
    void getFlaggedContentByMonthAndYear_shouldReturnEmptyList_whenServiceThrows() {
        when(contentService.getFlaggedContentByMonthAndYear(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("service error"));

        List<Map<String, Object>> result =
            service.getFlaggedContentByMonthAndYear(adminUserId, "token", "03", "2026");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    @Test
    void getAverageReviewTimeByMonthAndYear_shouldReturnZero_whenServiceThrows() {
        when(contentService.getFlaggedContentByMonthAndYear(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("service error"));

        double result = service.getAverageReviewTimeByMonthAndYear(adminUserId, "token", "03", "2026");

        assertEquals(0.0, result);
        verify(contentService, times(1)).getFlaggedContentByMonthAndYear("token", "03", "2026");
    }

    @Test
    void getTopFlagUsers_shouldReturnEmptyList_whenRpcThrows() {
        when(supabaseAdminRestClient.rpcList(anyString(), any(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        List<Map<String, Object>> result = service.getTopFlagUsers(adminUserId, "token", "03", "2026");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_users"), any(), any());
    }

    @Test
    void getTopFlagContents_shouldReturnEmptyList_whenRpcThrows() {
        when(supabaseAdminRestClient.rpcList(anyString(), any(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        List<Map<String, Object>> result = service.getTopFlagContents(adminUserId, "token", "03", "2026");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(supabaseAdminRestClient, times(1)).rpcList(eq("get_top_flag_content"), any(), any());
    }

    @Test
    void getAuditLogs_shouldReturnEmptyList_whenRpcThrows() {
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("RPC error"));

        List<Map<String, Object>> result = service.getAuditLogs(adminUserId, "token", "03", "2026");

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(supabaseAdminRestClient, times(1)).getList(eq("audit_logs"), contains("select="), any());
    }
}
