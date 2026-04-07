package com.rotiprata.api.admin.service;

import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminLoggingServiceImpl tests")
class AdminLoggingServiceImplTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @InjectMocks
    private AdminLoggingServiceImpl service;

    private UUID adminId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    // Should call Supabase postList successfully with valid arguments
    void logAdminAction_ShouldCallSupabaseSuccessfully_WhenCalledWithValidArguments() {
        UUID admin = adminId;
        UUID target = targetId;

        service.logAdminAction(
                admin,
                AdminLoggingService.AdminAction.DELETE_CONTENT,
                target,
                AdminLoggingService.TargetType.CONTENT,
                "Deleted test content"
        );

        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any());
    }

    @Test
    // Should not throw exception even if Supabase fails
    void logAdminAction_ShouldNotThrow_WhenSupabaseThrowsException() {
        doThrow(new RuntimeException("Supabase error"))
                .when(supabaseAdminRestClient)
                .postList(anyString(), anyList(), any());

        assertDoesNotThrow(() -> service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.REJECT_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Rejected test content"
        ));

        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should drop description when it contains Bearer-like token
    void logAdminAction_ShouldDropDescription_WhenDescriptionContainsBearerToken() {
        ArgumentCaptor<List<Map<String, Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);

        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Bearer mocked-jwt-token"
        );

        verify(supabaseAdminRestClient)
                .postList(eq("audit_logs"), rowsCaptor.capture(), any());

        List<Map<String, Object>> rows = rowsCaptor.getValue();
        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should truncate long descriptions to 500 characters
    void logAdminAction_ShouldTruncateDescription_WhenDescriptionIsTooLong() {
        String longDesc = "x".repeat(1000); // 1000 characters
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                longDesc
        );

        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());

        String savedDesc = (String) captor.getValue().get(0).get("description");
        assertNotNull(savedDesc);
        assertEquals(500, savedDesc.length());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should allow null descriptions without error
    void logAdminAction_ShouldNotThrowAndSetDescriptionNull_WhenDescriptionIsNull() {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                null
        );

        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());

        Map<String, Object> row = captor.getValue().get(0);
        assertNull(row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should set description to null when input is blank or whitespace only
    void logAdminAction_ShouldSetDescriptionNull_WhenDescriptionIsBlank() {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "   \n\t   "  // blank/whitespace-only
        );

        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertNull(row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should normalize multiple spaces in description to single spaces
    void logAdminAction_ShouldNormalizeWhitespaceInDescription() {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "This   has    multiple   spaces"
        );

        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertEquals("This has multiple spaces", row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Should keep short, safe descriptions unchanged
    void logAdminAction_ShouldKeepSafeShortDescription() {
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        String safeDesc = "A safe description under 500 chars.";
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                safeDesc
        );

        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertEquals(safeDesc, row.get("description"));
    }
}