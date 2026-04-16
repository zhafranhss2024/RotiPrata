package com.rotiprata.api.admin.service;

import com.rotiprata.api.admin.dto.AdminContentUpdateRequest;
import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.feed.service.ContentLessonLinkService;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers admin content lesson link scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class AdminContentLessonLinkTest {

    @Mock
    private SupabaseAdminClient supabaseAdminClient;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @Mock
    private ContentCreatorEnrichmentService contentCreatorEnrichmentService;

    @Mock
    private ContentService contentService;

    @Mock
    private ContentLessonLinkService contentLessonLinkService;

    @Mock
    private UserService userService;

    @Mock
    private AdminLoggingService adminLoggingService;

    private AdminServiceImpl adminService;
    private UUID adminUserId;
    private UUID contentId;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(
            supabaseAdminClient,
            supabaseAdminRestClient,
            contentCreatorEnrichmentService,
            contentService,
            contentLessonLinkService,
            userService,
            adminLoggingService
        );
        adminUserId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        when(userService.getRoles(adminUserId, "token")).thenReturn(List.of(AppRole.ADMIN));
    }

    /**
     * Verifies that update content metadata should sync lesson concept links when lesson ids are provided.
     */
    /** Verifies admin metadata updates also sync recommendation lesson links when lesson ids are supplied. */
    @Test
    void updateContentMetadata_ShouldSyncLessonConceptLinks_WhenLessonIdsAreProvided() {
        // arrange
        UUID lessonId = UUID.randomUUID();
        Content updatedContent = new Content();
        updatedContent.setId(contentId);
        when(supabaseAdminRestClient.patchList(eq("content"), anyString(), any(), any()))
            .thenReturn(List.of(updatedContent));

        AdminContentUpdateRequest request = new AdminContentUpdateRequest(
            "Title",
            "Description",
            "Objective",
            "Origin",
            "Literal",
            "Used",
            "Reference",
            UUID.randomUUID(),
            List.of("tag-one"),
            List.of(lessonId)
        );

        // act
        adminService.updateContentMetadata(adminUserId, contentId, request, "token");

        // assert
        org.junit.jupiter.api.Assertions.assertTrue(true);

        // verify
        verify(contentLessonLinkService).replaceContentLessonLinks(contentId, List.of(lessonId));
    }
}
