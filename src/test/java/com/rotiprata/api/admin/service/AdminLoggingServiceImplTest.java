package com.rotiprata.api.admin.service;

import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    @SuppressWarnings("unchecked")
    void logAdminAction_ShouldCallSupabaseSuccessfully_WhenCalledWithValidArguments() {
        // Act
        service.logAdminAction(
                adminId,
                AdminLoggingService.AdminAction.DELETE_CONTENT,
                targetId,
                AdminLoggingService.TargetType.CONTENT,
                "Deleted test content"
        );

        // Assert
        verify(supabaseAdminRestClient, times(1)).postList(eq("audit_logs"), anyList(), any(TypeReference.class));
    }

    @Test
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

        verify(supabaseAdminRestClient, times(1)).postList(eq("audit_logs"), anyList(), any(TypeReference.class));
    }
}