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
    // Verifies that Supabase postList is called when logging a valid admin action
    void logAdminAction_ShouldCallSupabaseSuccessfully_WhenCalledWithValidArguments() {
        // arrange
        UUID admin = adminId;
        UUID target = targetId;

        // act
        service.logAdminAction(
                admin,
                AdminLoggingService.AdminAction.DELETE_CONTENT,
                target,
                AdminLoggingService.TargetType.CONTENT,
                "Deleted test content"
        );

        // assert & verify
        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any());
    }

    @Test
    // Ensures exceptions from Supabase do not propagate
    void logAdminAction_ShouldNotThrow_WhenSupabaseThrowsException() {
        // arrange
        doThrow(new RuntimeException("Supabase error"))
                .when(supabaseAdminRestClient)
                .postList(anyString(), anyList(), any());

        // act & assert
        assertDoesNotThrow(() -> service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.REJECT_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Rejected test content"
        ));

        // verify
        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Clears description if it contains a Bearer-like token
    void logAdminAction_ShouldDropDescription_WhenDescriptionContainsBearerToken() {
        // arrange
        ArgumentCaptor<List<Map<String, Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Bearer mocked-jwt-token"
        );

        // assert & verify
        verify(supabaseAdminRestClient)
                .postList(eq("audit_logs"), rowsCaptor.capture(), any());
        List<Map<String, Object>> rows = rowsCaptor.getValue();
        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Truncates descriptions exceeding 500 characters
    void logAdminAction_ShouldTruncateDescription_WhenDescriptionIsTooLong() {
        // arrange
        String longDesc = "x".repeat(1000);
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                longDesc
        );

        // assert & verify
        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        String savedDesc = (String) captor.getValue().get(0).get("description");
        assertNotNull(savedDesc);
        assertEquals(500, savedDesc.length());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Handles null descriptions gracefully
    void logAdminAction_ShouldNotThrowAndSetDescriptionNull_WhenDescriptionIsNull() {
        // arrange
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                null
        );

        // assert & verify
        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertNull(row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Converts blank or whitespace-only descriptions to null
    void logAdminAction_ShouldSetDescriptionNull_WhenDescriptionIsBlank() {
        // arrange
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "   \n\t   "
        );

        // assert & verify
        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertNull(row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Normalizes multiple spaces in description
    void logAdminAction_ShouldNormalizeWhitespaceInDescription_WhenDescriptionHasExtraSpaces() {
        // arrange
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "This   has    multiple   spaces"
        );

        // assert & verify
        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertEquals("This has multiple spaces", row.get("description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    // Keeps short safe descriptions unchanged
    void logAdminAction_ShouldKeepSafeShortDescription_WhenDescriptionIsValid() {
        // arrange
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        String safeDesc = "A safe description under 500 chars.";

        // act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                safeDesc
        );

        // assert & verify
        verify(supabaseAdminRestClient).postList(eq("audit_logs"), captor.capture(), any());
        Map<String, Object> row = captor.getValue().get(0);
        assertEquals(safeDesc, row.get("description"));
    }
}