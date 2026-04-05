package com.rotiprata.api.admin.service;

import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService flag review tests")
class AdminServiceTest {

    @Mock
    private SupabaseAdminClient supabaseAdminClient;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @Mock
    private ContentCreatorEnrichmentService contentCreatorEnrichmentService;

    @Mock
    private ContentService contentService;

    @Mock
    private UserService userService;

    @Mock
    private AdminLoggingService adminLoggingService;

    private AdminService adminService;
    private UUID adminUserId;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
            supabaseAdminClient,
            supabaseAdminRestClient,
            contentCreatorEnrichmentService,
            contentService,
            userService,
            adminLoggingService
        );
        adminUserId = UUID.randomUUID();
        contentId = UUID.randomUUID();

        lenient().when(userService.getRoles(adminUserId, "token")).thenReturn(List.of(AppRole.ADMIN));
        lenient().when(contentCreatorEnrichmentService.enrichWithCreatorProfiles(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getFlagReviewByContent_shouldBeReadOnlyForHistoricalMonthWithoutPendingFlags() {
        UUID mayPendingId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", mayPendingId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Pending report",
                "created_at", "2026-05-01T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Abuse",
                "description", "Resolved report",
                "created_at", "2026-04-10T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        assertEquals("resolved", review.get("status"));
        assertNull(review.get("actionableFlagId"));
        assertEquals(false, review.get("canResolve"));
        assertEquals(false, review.get("canTakeDown"));
    }

    @Test
    void getFlagReviewByContent_shouldScopeActionableFlagToSelectedMonth() {
        UUID mayPendingId = UUID.randomUUID();
        UUID aprilPendingId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", mayPendingId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "May report",
                "created_at", "2026-05-05T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", aprilPendingId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Abuse",
                "description", "April report",
                "created_at", "2026-04-10T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        assertEquals("pending", review.get("status"));
        assertEquals(aprilPendingId.toString(), review.get("actionableFlagId"));
        assertTrue((Boolean) review.get("canResolve"));
        assertTrue((Boolean) review.get("canTakeDown"));
        assertFalse(mayPendingId.toString().equals(review.get("actionableFlagId")));
    }
}
