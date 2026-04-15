package com.rotiprata.api.admin.service;

import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
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
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService getFlagReviewByContent tests")
class AdminServiceImplTest {

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

    private AdminServiceImpl adminService;
    private UUID adminUserId;
    private UUID contentId;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
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

    // Verifies actionable review details are produced when selected period has a pending report.
    @Test
    void getFlagReviewByContent_ShouldReturnPendingReview_WhenSelectedMonthHasPendingFlag() {
        // arrange
        UUID pendingFlagId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", pendingFlagId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", reporterId.toString(),
                "reason", "Spam",
                "description", "Needs action",
                "created_at", "2026-04-10T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", " ",
                "created_at", "2026-04-09T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        // assert
        assertEquals(contentId, review.get("contentId"));
        assertEquals("pending", review.get("status"));
        assertEquals(2, review.get("reportCount"));
        assertEquals(1, review.get("notesCount"));
        assertEquals(List.of("Spam"), review.get("reasons"));
        assertEquals("2026-04-10T12:00:00Z", review.get("latestReportAt"));
        assertEquals(pendingFlagId.toString(), review.get("actionableFlagId"));
        assertTrue((Boolean) review.get("canResolve"));
        assertTrue((Boolean) review.get("canTakeDown"));
        assertNotNull(review.get("content"));

        // verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
        verify(supabaseAdminRestClient, times(1)).getList(eq("content_flags"), anyString(), any());
    }
    
    // Verifies bad request is returned when year is provided without month.
    @Test
    void getFlagReviewByContent_ShouldThrowBadRequest_WhenOnlyYearIsProvided() {
        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, null, 2026, "token")
        );

        // assert
        assertEquals(400, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Month and year are required together"));

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies bad request is returned when the provided month is below range.
    @Test
    void getFlagReviewByContent_ShouldThrowBadRequest_WhenMonthIsBelowRange() {
        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 0, 2026, "token")
        );

        // assert
        assertEquals(400, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Month must be between 1 and 12"));

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_flags"), anyString(), any());
    }


    // Verifies review is read-only when selected period has only resolved reports.
    @Test
    void getFlagReviewByContent_ShouldReturnResolvedReview_WhenSelectedMonthHasNoPendingFlag() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Abuse",
                "description", "Handled",
                "created_at", "2026-04-10T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "  ",
                "created_at", "2026-04-09T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        // assert
        assertEquals("resolved", review.get("status"));
        assertNull(review.get("actionableFlagId"));
        assertFalse((Boolean) review.get("canResolve"));
        assertFalse((Boolean) review.get("canTakeDown"));
        assertEquals(List.of("Abuse", "Spam"), review.get("reasons"));

        // verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
    }

    // Verifies period filtering returns not found when no row is in the requested month/year.
    @Test
    void getFlagReviewByContent_ShouldThrowNotFound_WhenNoFlagsMatchSelectedMonth() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Out of scope",
                "created_at", "2026-05-01T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token")
        );

        // assert
        assertEquals(404, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Flag review not found"));

        // verify
        verify(contentCreatorEnrichmentService, never()).enrichWithCreatorProfiles(any());
    }

    // Verifies null month/year mode falls back to pending-only rows.
    @Test
    void getFlagReviewByContent_ShouldFilterPendingOnly_WhenMonthAndYearAreNull() {
        // arrange
        UUID pendingFlagId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Abuse",
                "description", "Already resolved",
                "created_at", "2026-05-02T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", pendingFlagId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Pending only",
                "created_at", "2026-04-01T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, null, null, "token");

        // assert
        assertEquals("pending", review.get("status"));
        assertEquals(1, review.get("reportCount"));
        assertEquals(pendingFlagId.toString(), review.get("actionableFlagId"));

        // verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
    }

    // Verifies null month/year mode throws not found when no pending row exists.
    @Test
    void getFlagReviewByContent_ShouldThrowNotFound_WhenMonthAndYearAreNullAndNoPendingFlags() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Resolved only",
                "created_at", "2026-04-01T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, null, null, "token")
        );

        // assert
        assertEquals(404, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Flag review not found"));

        // verify
        verify(contentCreatorEnrichmentService, never()).enrichWithCreatorProfiles(any());
    }

    // Verifies bad request is returned when month and year are not provided together.
    @Test
    void getFlagReviewByContent_ShouldThrowBadRequest_WhenOnlyMonthIsProvided() {
        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 4, null, "token")
        );

        // assert
        assertEquals(400, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Month and year are required together"));

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies bad request is returned when the provided month is out of range.
    @Test
    void getFlagReviewByContent_ShouldThrowBadRequest_WhenMonthIsOutOfRange() {
        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 13, 2026, "token")
        );

        // assert
        assertEquals(400, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Month must be between 1 and 12"));

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_flags"), anyString(), any());
    }

        // Verifies pending rows with malformed id do not become actionable actions.
    @Test
    void getFlagReviewByContent_ShouldNotSetActionableId_WhenPendingIdIsMalformed() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", "not-a-uuid",
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Needs review",
                "created_at", "2026-04-10T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        // assert
        assertEquals("pending", review.get("status"));
        assertNull(review.get("actionableFlagId"));
        assertFalse((Boolean) review.get("canResolve"));
        assertFalse((Boolean) review.get("canTakeDown"));
    }

    // Verifies rows with invalid created_at are excluded from month/year filtering.
    @Test
    void getFlagReviewByContent_ShouldThrowNotFound_WhenCreatedAtCannotBeParsed() {
        // arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Broken timestamp",
                "created_at", "invalid-date",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token")
        );

        // assert
        assertEquals(404, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Flag review not found"));
    }

    // Verifies non-admin users cannot access review endpoints.
    @Test
    void getFlagReviewByContent_ShouldThrowForbidden_WhenUserIsNotAdmin() {
        // arrange
        UUID nonAdminUserId = UUID.randomUUID();
        when(userService.getRoles(nonAdminUserId, "token")).thenReturn(List.of(AppRole.USER));

        // act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(nonAdminUserId, contentId, 4, 2026, "token")
        );

        // assert
        assertEquals(403, thrown.getStatusCode().value());
        assertTrue(thrown.getReason().contains("Admin role required"));

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies historical review is read-only when selected month has no pending reports.
    @Test
    void getFlagReviewByContent_ShouldBeReadOnly_WhenHistoricalMonthHasNoPendingFlags() {
        //arrange
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

        //act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        //assert
        assertEquals("resolved", review.get("status"));
        assertNull(review.get("actionableFlagId"));
        assertEquals(false, review.get("canResolve"));
        assertEquals(false, review.get("canTakeDown"));

        //verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
    }

    // Verifies the actionable pending flag is chosen from the requested month and year only.
    @Test
    void getFlagReviewByContent_ShouldScopeActionableFlag_WhenMonthAndYearAreProvided() {
        //arrange
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

        //act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token");

        //assert
        assertEquals("pending", review.get("status"));
        assertEquals(aprilPendingId.toString(), review.get("actionableFlagId"));
        assertTrue((Boolean) review.get("canResolve"));
        assertTrue((Boolean) review.get("canTakeDown"));
        assertFalse(mayPendingId.toString().equals(review.get("actionableFlagId")));

        //verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
    }

    // Verifies current pending review mode filters to pending rows when no month/year is provided.
    @Test
    void getFlagReviewByContent_ShouldReturnPendingReview_WhenPeriodIsNotProvided() {
        //arrange
        UUID pendingId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", pendingId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", " Spam ",
                "description", "Note",
                "created_at", "2026-04-12T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "resolved",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Ignored",
                "description", "old",
                "created_at", "2026-04-11T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        //act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, null, null, "token");

        //assert
        assertEquals("pending", review.get("status"));
        assertEquals(1, review.get("reportCount"));
        assertEquals(1, review.get("notesCount"));
        assertEquals(List.of("Spam"), review.get("reasons"));

        //verify
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(any());
    }

    // Verifies a not-found error is returned when no rows match the selected review period.
    @Test
    void getFlagReviewByContent_ShouldThrowNotFound_WhenNoRowsMatchRequestedPeriod() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "Spam",
                "description", "Pending report",
                "created_at", "2026-05-01T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 4, 2026, "token")
        );

        //assert
        assertEquals(404, thrown.getStatusCode().value());
        assertEquals("Flag review not found", thrown.getReason());

        //verify
        verify(contentCreatorEnrichmentService, times(0)).enrichWithCreatorProfiles(any());
    }

    // Verifies request validation rejects partial period input.
    @Test
    void getFlagReviewByContent_ShouldThrowBadRequest_WhenMonthOrYearIsMissing() {
        //arrange

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, 4, null, "token")
        );

        //assert
        assertEquals(400, thrown.getStatusCode().value());
        assertEquals("Month and year are required together", thrown.getReason());

        //verify
        verify(supabaseAdminRestClient, times(0)).getList(anyString(), anyString(), any());
    }

    // Verifies authorization fails when the access token is blank.
    @Test
    void getFlagReviewByContent_ShouldThrowUnauthorized_WhenAccessTokenIsBlank() {
        //arrange

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> adminService.getFlagReviewByContent(adminUserId, contentId, null, null, "   ")
        );

        //assert
        assertEquals(401, thrown.getStatusCode().value());
        assertEquals("Missing access token", thrown.getReason());

        //verify
        verify(userService, times(0)).getRoles(any(), anyString());
    }

    // Verifies reasons are deduplicated, blank notes are ignored, and enrichment receives the content payload.
    @Test
    void getFlagReviewByContent_ShouldNormalizeReviewFields_WhenRowsContainWhitespaceAndDuplicates() {
        //arrange
        UUID pendingId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of(
            Map.of(
                "id", pendingId.toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "  spam  ",
                "description", "   ",
                "created_at", "2026-04-12T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            ),
            Map.of(
                "id", UUID.randomUUID().toString(),
                "content_id", contentId.toString(),
                "status", "pending",
                "reported_by", UUID.randomUUID().toString(),
                "reason", "spam",
                "description", "note",
                "created_at", "2026-04-11T12:00:00Z",
                "content", Map.of("id", contentId.toString(), "title", "Roti")
            )
        ));

        //act
        Map<String, Object> review =
            adminService.getFlagReviewByContent(adminUserId, contentId, null, null, "token");

        //assert
        assertEquals(2, review.get("reportCount"));
        assertEquals(1, review.get("notesCount"));
        assertEquals(List.of("spam"), review.get("reasons"));

        //verify
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentCreatorEnrichmentService, times(1)).enrichWithCreatorProfiles(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(contentId.toString(), String.valueOf(captor.getValue().get(0).get("id")));
    }

    // Verifies pending flag lookup returns the row when the flag exists and is still pending.
    @Test
    void getPendingFlag_ShouldReturnRow_WhenStatusIsPending() {
        //arrange
        UUID flagId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(
            List.of(Map.of("id", flagId.toString(), "content_id", contentId.toString(), "status", "pending"))
        );

        //act
        Map<String, Object> row = invokePrivate("getPendingFlag", new Class[] {UUID.class}, flagId);

        //assert
        assertEquals(flagId.toString(), row.get("id"));

        //verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies pending flag lookup throws 404 when no row exists for the flag id.
    @Test
    void getPendingFlag_ShouldThrowNotFound_WhenFlagDoesNotExist() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(List.of());

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> invokePrivate("getPendingFlag", new Class[] {UUID.class}, UUID.randomUUID())
        );

        //assert
        assertEquals(HttpStatus.NOT_FOUND, thrown.getStatusCode());
        assertEquals("Flag not found", thrown.getReason());

        //verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies pending flag lookup throws conflict when the flag is already resolved.
    @Test
    void getPendingFlag_ShouldThrowConflict_WhenFlagIsNotPending() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("content_flags"), anyString(), any())).thenReturn(
            List.of(Map.of("id", UUID.randomUUID().toString(), "content_id", contentId.toString(), "status", "resolved"))
        );

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> invokePrivate("getPendingFlag", new Class[] {UUID.class}, UUID.randomUUID())
        );

        //assert
        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        assertEquals("Flag already resolved", thrown.getReason());

        //verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("content_flags"), anyString(), any());
    }

    // Verifies reporter filtering keeps rows for matched profiles and drops unmatched reporters.
    @Test
    void filterFlagRowsByReporter_ShouldReturnMatchedRows_WhenReporterExists() {
        //arrange
        UUID matchedUserId = UUID.randomUUID();
        UUID unmatchedUserId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(
            List.of(Map.of("user_id", matchedUserId.toString()))
        );
        List<Map<String, Object>> rows = List.of(
            Map.of("reported_by", matchedUserId.toString()),
            Map.of("reported_by", unmatchedUserId.toString())
        );

        //act
        List<Map<String, Object>> filtered = invokePrivate(
            "filterFlagRowsByReporter",
            new Class[] {List.class, String.class},
            rows,
            "alice"
        );

        //assert
        assertEquals(1, filtered.size());
        assertEquals(matchedUserId.toString(), filtered.get(0).get("reported_by"));

        //verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("profiles"), anyString(), any());
    }

    // Verifies reporter filtering returns empty list when no profile matches the query.
    @Test
    void filterFlagRowsByReporter_ShouldReturnEmptyList_WhenNoReporterMatchFound() {
        //arrange
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(List.of());
        List<Map<String, Object>> rows = List.of(Map.of("reported_by", UUID.randomUUID().toString()));

        //act
        List<Map<String, Object>> filtered = invokePrivate(
            "filterFlagRowsByReporter",
            new Class[] {List.class, String.class},
            rows,
            "nobody"
        );

        //assert
        assertTrue(filtered.isEmpty());

        //verify
        verify(supabaseAdminRestClient, times(1)).getList(eq("profiles"), anyString(), any());
    }

    // Verifies reporter filtering returns original rows when query is blank.
    @Test
    void filterFlagRowsByReporter_ShouldReturnOriginalRows_WhenQueryIsBlank() {
        //arrange
        List<Map<String, Object>> rows = List.of(Map.of("reported_by", UUID.randomUUID().toString()));

        //act
        List<Map<String, Object>> filtered = invokePrivate(
            "filterFlagRowsByReporter",
            new Class[] {List.class, String.class},
            rows,
            "   "
        );

        //assert
        assertEquals(rows, filtered);

        //verify
        verify(supabaseAdminRestClient, times(0)).getList(eq("profiles"), anyString(), any());
    }

    // Verifies utility parsers and normalizers handle valid and invalid values correctly.
    @Test
    void utilityMethods_ShouldNormalizeAndParseValues_WhenMixedInputsAreProvided() {
        //arrange

        //act
        String sanitized = invokePrivate("sanitizeText", new Class[] {String.class, int.class}, " A \n B\tC ", 5);
        String tag = invokePrivate("sanitizeTag", new Class[] {String.class}, "#  spicy ");
        UUID validUuid = invokePrivate("parseUuid", new Class[] {Object.class}, UUID.randomUUID().toString());
        UUID invalidUuid = invokePrivate("parseUuid", new Class[] {Object.class}, new Object[]{ "not-a-uuid" });
        Integer parsedInt = invokePrivate("parseInteger", new Class[] {Object.class}, "42");
        Integer invalidInt = invokePrivate("parseInteger", new Class[] {Object.class}, new Object[]{ "x" });
        int parsedPrimitiveInt = invokePrivate("toInt", new Class[] {Object.class}, "7");
        int invalidPrimitiveInt = invokePrivate("toInt", new Class[] {Object.class}, new Object[]{ "oops" });
        LocalDateTime parsedDateTime = invokePrivate("parseLocalDateTime", new Class[] {Object.class}, "2026-04-08T10:15:30");
        LocalDateTime invalidDateTime = invokePrivate("parseLocalDateTime", new Class[] {Object.class}, new Object[]{ "bad" });
        OffsetDateTime parsedOffset = invokePrivate("parseOffsetDateTime", new Class[] {Object.class}, "2026-04-08T10:15:30Z");
        OffsetDateTime invalidOffset = invokePrivate("parseOffsetDateTime", new Class[] {Object.class}, new Object[]{ "bad" });
        String normalizedReporter = invokePrivate("normalizeReporterQuery", new Class[] {String.class}, "@alice");
        String normalizedBlank = invokePrivate("normalizeReporterQuery", new Class[] {String.class}, new Object[]{ "   " });
        String normalizedNullable = invokePrivate("normalizeNullableText", new Class[] {String.class}, "  value ");
        String normalizedNullableBlank = invokePrivate("normalizeNullableText", new Class[] {String.class}, new Object[]{ " " });
        String asString = invokePrivate("toStringOrNull", new Class[] {Object.class}, 123);
        String nullString = invokePrivate("toStringOrNull", new Class[] {Object.class}, new Object[]{ null });

        //assert
        assertEquals("A B C", sanitized);
        assertEquals("spicy", tag);
        assertNotNull(validUuid);
        assertNull(invalidUuid);
        assertEquals(42, parsedInt);
        assertNull(invalidInt);
        assertEquals(7, parsedPrimitiveInt);
        assertEquals(0, invalidPrimitiveInt);
        assertNotNull(parsedDateTime);
        assertNull(invalidDateTime);
        assertNotNull(parsedOffset);
        assertNull(invalidOffset);
        assertEquals("alice", normalizedReporter);
        assertNull(normalizedBlank);
        assertEquals("value", normalizedNullable);
        assertNull(normalizedNullableBlank);
        assertEquals("123", asString);
        assertNull(nullString);

        //verify
        verify(supabaseAdminRestClient, times(0)).getList(anyString(), anyString(), any());
    }

    // Verifies required field sanitizer throws a bad-request error when value is blank after cleanup.
    @Test
    void sanitizeRequired_ShouldThrowBadRequest_WhenSanitizedValueIsBlank() {
        //arrange

        //act
        ResponseStatusException thrown = assertThrows(
            ResponseStatusException.class,
            () -> invokePrivate("sanitizeRequired", new Class[] {String.class, int.class, String.class}, "   ", 10, "title")
        );

        //assert
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals("title is required", thrown.getReason());

        //verify
        verify(supabaseAdminRestClient, times(0)).getList(anyString(), anyString(), any());
    }

    // Verifies access-token and role validation methods enforce unauthorized and forbidden failures.
    @Test
    void authValidation_ShouldThrowUnauthorizedOrForbidden_WhenTokenMissingOrRoleInsufficient() {
        //arrange
        UUID nonAdminUserId = UUID.randomUUID();
        when(userService.getRoles(nonAdminUserId, "token")).thenReturn(List.of(AppRole.USER));

        //act
        ResponseStatusException unauthorized = assertThrows(
            ResponseStatusException.class,
            () -> invokePrivate("requireAccessToken", new Class[] {String.class}, " ")
        );
        ResponseStatusException forbidden = assertThrows(
            ResponseStatusException.class,
            () -> invokePrivate("requireAdmin", new Class[] {UUID.class, String.class}, nonAdminUserId, "token")
        );

        //assert
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        //verify
        verify(userService, times(1)).getRoles(nonAdminUserId, "token");
    }

    // Verifies query builder and UUID joiner generate encoded query text correctly.
    @Test
    void queryHelpers_ShouldBuildExpectedQuery_WhenParamsAndIdsProvided() {
        //arrange
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();

        //act
        String query = invokePrivate(
            "buildQuery",
            new Class[] {Map.class},
            new LinkedHashMap<>(Map.of("select", "id", "status", "eq.pending"))
        );
        String joined = invokePrivate("joinUuids", new Class[] {Set.class}, Set.of(one, two));

        //assert
        assertTrue(query.contains("select=id"));
        assertTrue(query.contains("status=eq.pending"));
        assertTrue(joined.contains(one.toString()));
        assertTrue(joined.contains(two.toString()));

        //verify
        verify(supabaseAdminRestClient, times(0)).getList(anyString(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = AdminServiceImpl.class.getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return (T) method.invoke(adminService, args);
        } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(ex.getTargetException());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
