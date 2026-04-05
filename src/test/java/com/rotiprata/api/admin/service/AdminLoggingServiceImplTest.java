package com.rotiprata.api.admin.service;

import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminLoggingServiceImplTest {

    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @InjectMocks
    private AdminLoggingServiceImpl service;

    private UUID adminId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adminId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    // Should call Supabase postList successfully with valid arguments
    @SuppressWarnings("unchecked")
    void logAdminAction_ShouldCallSupabaseSuccessfully_WhenCalledWithValidArguments() {
        // Arrange
        UUID admin = adminId;
        UUID target = targetId;

        // Act
        service.logAdminAction(
                admin,
                AdminLoggingService.AdminAction.DELETE_CONTENT,
                target,
                AdminLoggingService.TargetType.CONTENT,
                "Deleted test content"
        );

        // Assert & Verify
        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any(TypeReference.class));
    }

    @Test
    // Should not throw exception even if Supabase fails
    @SuppressWarnings("unchecked")
    void logAdminAction_ShouldNotThrow_WhenSupabaseThrowsException() {
        // Arrange
        doThrow(new RuntimeException("Supabase error"))
                .when(supabaseAdminRestClient)
                .postList(anyString(), anyList(), any());

        // Act & Assert
        assertDoesNotThrow(() -> service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.REJECT_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Rejected test content"
        ));

        // Verify
        verify(supabaseAdminRestClient, times(1))
                .postList(eq("audit_logs"), anyList(), any(TypeReference.class));
    }

    @Test
    // Should drop description when it contains Bearer-like token
    @SuppressWarnings("unchecked")
    void logAdminAction_ShouldDropDescription_WhenDescriptionContainsBearerToken() {
        // Arrange
        ArgumentCaptor<List<Map<String, Object>>> rowsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.UPDATE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Bearer mocked-jwt-token"
        );

        // Verify
        verify(supabaseAdminRestClient)
                .postList(eq("audit_logs"), rowsCaptor.capture(), any(TypeReference.class));

        // Assert
        List<Map<String, Object>> rows = rowsCaptor.getValue();
        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("description"));
    }
}