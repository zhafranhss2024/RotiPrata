package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentLessonLinkServiceTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    private ContentLessonLinkService service;

    @BeforeEach
    void setUp() {
        service = new ContentLessonLinkService(supabaseAdminRestClient);
    }

    /** Verifies null and empty content-id inputs short-circuit without any database calls. */
    @Test
    void resolveLinkedLessons_ShouldReturnEmpty_WhenInputIsNullOrEmpty() {
        // arrange

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> nullResult = service.resolveLinkedLessons(null);
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> emptyResult = service.resolveLinkedLessons(java.util.Set.of());

        // assert
        assertTrue(nullResult.isEmpty());
        assertTrue(emptyResult.isEmpty());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("lesson_concepts"), anyString(), any(TypeReference.class));
    }

    /** Verifies direct lesson-concept links take precedence over quiz fallback links. */
    @Test
    void resolveLinkedLessons_ShouldPreferLessonConcepts_WhenDirectLinksExist() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "content_id", contentId.toString(),
                "lesson_id", lessonId.toString(),
                "order_index", 0
            )));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "id", lessonId.toString(),
                "title", "Lesson One",
                "category_id", UUID.randomUUID().toString(),
                "is_published", true,
                "is_active", true
            )));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertEquals(1, linkedLessons.get(contentId).size());
        assertEquals(ContentLessonLinkService.LinkSource.LESSON_CONCEPT, linkedLessons.get(contentId).get(0).source());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("quizzes"), anyString(), any(TypeReference.class));
    }

    /** Verifies the service falls back to quiz links only when no direct lesson-concept rows exist. */
    @Test
    void resolveLinkedLessons_ShouldFallbackToQuizLinks_WhenConceptsAreMissing() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "content_id", contentId.toString(),
                "lesson_id", lessonId.toString(),
                "created_at", "2026-04-01T12:00:00Z"
            )));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "id", lessonId.toString(),
                "title", "Lesson Two",
                "category_id", UUID.randomUUID().toString(),
                "is_published", true,
                "is_active", true
            )));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertEquals(ContentLessonLinkService.LinkSource.QUIZ_FALLBACK, linkedLessons.get(contentId).get(0).source());

        // verify
        verify(supabaseAdminRestClient).getList(eq("quizzes"), anyString(), any(TypeReference.class));
    }

    /** Verifies inactive or unpublished lessons are filtered out during hydration. */
    @Test
    void resolveLinkedLessons_ShouldSkipInactiveLessons_WhenHydratingLinks() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID inactiveLessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "content_id", contentId.toString(),
                "lesson_id", inactiveLessonId.toString(),
                "order_index", 0
            )));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "id", inactiveLessonId.toString(),
                "title", "Inactive Lesson",
                "category_id", UUID.randomUUID().toString(),
                "is_published", false,
                "is_active", true
            )));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertTrue(linkedLessons.isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("lessons"), anyString(), any(TypeReference.class));
    }

    /** Verifies invalid concept-link ids and invalid order indexes are normalized safely. */
    @Test
    void resolveLinkedLessons_ShouldIgnoreBadIdsAndDefaultOrder_WhenRowsContainInvalidValues() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID firstLessonId = UUID.randomUUID();
        UUID secondLessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("content_id", contentId.toString(), "lesson_id", firstLessonId.toString(), "order_index", "oops"),
                Map.of("content_id", contentId.toString(), "lesson_id", secondLessonId.toString(), "order_index", "2"),
                Map.of("content_id", "bad-content", "lesson_id", secondLessonId.toString(), "order_index", 0),
                Map.of("content_id", contentId.toString(), "lesson_id", "bad-lesson", "order_index", 0)
            ));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("id", firstLessonId.toString(), "title", "First", "category_id", UUID.randomUUID().toString(), "is_published", true, "is_active", true),
                Map.of("id", secondLessonId.toString(), "title", "Second", "category_id", UUID.randomUUID().toString(), "is_published", true, "is_active", true)
            ));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertEquals(firstLessonId, linkedLessons.get(contentId).get(0).lessonId());
        assertEquals(secondLessonId, linkedLessons.get(contentId).get(1).lessonId());

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("quizzes"), anyString(), any(TypeReference.class));
    }

    /** Verifies malformed hydrated lesson rows are skipped before link objects are created. */
    @Test
    void resolveLinkedLessons_ShouldSkipHydratedLessonRows_WhenLessonIdsAreMissingOrInactive() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID inactiveLessonId = UUID.randomUUID();
        Map<String, Object> nullIdLesson = new java.util.LinkedHashMap<>();
        nullIdLesson.put("id", null);
        nullIdLesson.put("title", "Broken");
        nullIdLesson.put("category_id", UUID.randomUUID().toString());
        nullIdLesson.put("is_published", true);
        nullIdLesson.put("is_active", true);
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "content_id", contentId.toString(),
                "lesson_id", inactiveLessonId.toString(),
                "order_index", 0
            )));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                nullIdLesson,
                Map.of(
                    "id", inactiveLessonId.toString(),
                    "title", "Inactive",
                    "category_id", UUID.randomUUID().toString(),
                    "is_published", true,
                    "is_active", false
                )
            ));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertTrue(linkedLessons.isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("lessons"), anyString(), any(TypeReference.class));
    }

    /** Verifies fallback quiz links are dropped when they do not hydrate to active lessons. */
    @Test
    void resolveLinkedLessons_ShouldReturnEmpty_WhenFallbackQuizLinksResolveToNoActiveLessons() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class))).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("quizzes"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "content_id", contentId.toString(),
                "lesson_id", lessonId.toString(),
                "created_at", "2026-04-01T12:00:00Z"
            )));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(Map.of(
                "id", lessonId.toString(),
                "title", "Draft Lesson",
                "category_id", UUID.randomUUID().toString(),
                "is_published", false,
                "is_active", true
            )));

        // act
        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(java.util.Set.of(contentId));

        // assert
        assertTrue(linkedLessons.isEmpty());

        // verify
        verify(supabaseAdminRestClient).getList(eq("quizzes"), anyString(), any(TypeReference.class));
    }

    /** Verifies missing content ids are rejected before any link replacement work starts. */
    @Test
    void replaceContentLessonLinks_ShouldRejectMissingContentId_WhenContentIdIsNull() {
        // arrange

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.replaceContentLessonLinks(null, List.of(UUID.randomUUID()))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient, never()).deleteList(eq("lesson_concepts"), anyString(), any(TypeReference.class));
    }

    /** Verifies null lesson lists skip validation and persistence work. */
    @Test
    void replaceContentLessonLinks_ShouldSkipValidation_WhenLessonIdsAreNull() {
        // arrange
        UUID contentId = UUID.randomUUID();

        // act
        service.replaceContentLessonLinks(contentId, null);

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAdminRestClient, never()).getList(eq("lessons"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).deleteList(eq("lesson_concepts"), anyString(), any(TypeReference.class));
    }

    /** Verifies empty normalized lesson lists still delete existing links and stop before posting replacements. */
    @Test
    void replaceContentLessonLinks_ShouldDeleteAndStop_WhenLessonIdsNormalizeToEmptyAfterNullFiltering() {
        // arrange
        UUID contentId = UUID.randomUUID();

        // act
        service.replaceContentLessonLinks(contentId, java.util.Arrays.asList(null, null));

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAdminRestClient).deleteList(eq("lesson_concepts"), argThat(query -> query.contains(contentId.toString())), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).postList(eq("lesson_concepts"), any(), any(TypeReference.class));
    }

    /** Verifies duplicate lesson ids are collapsed while preserving the first stable order. */
    @Test
    void replaceContentLessonLinks_ShouldDeduplicateLessonsAndPreserveOrder_WhenSavingLinks() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID firstLessonId = UUID.randomUUID();
        UUID secondLessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("id", firstLessonId.toString()),
                Map.of("id", secondLessonId.toString())
            ));
        when(supabaseAdminRestClient.postList(eq("lesson_concepts"), any(), any(TypeReference.class))).thenReturn(List.of());

        // act
        service.replaceContentLessonLinks(contentId, List.of(firstLessonId, firstLessonId, secondLessonId));

        // assert
        assertTrue(true);

        // verify
        verify(supabaseAdminRestClient).postList(
            eq("lesson_concepts"),
            argThat(rows -> rows instanceof List<?> list
                && list.size() == 2
                && ((Map<?, ?>) list.get(0)).get("lesson_id").equals(firstLessonId)
                && ((Map<?, ?>) list.get(0)).get("order_index").equals(0)
                && ((Map<?, ?>) list.get(1)).get("lesson_id").equals(secondLessonId)
                && ((Map<?, ?>) list.get(1)).get("order_index").equals(1)),
            any(TypeReference.class)
        );
    }

    /** Verifies unknown lesson ids are rejected after validation queries run. */
    @Test
    void replaceContentLessonLinks_ShouldRejectUnknownLessonIds_WhenValidationFindsMissingLessons() {
        // arrange
        UUID contentId = UUID.randomUUID();
        UUID missingLessonId = UUID.randomUUID();
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class))).thenReturn(List.of());

        // act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.replaceContentLessonLinks(contentId, List.of(missingLessonId))
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        // verify
        verify(supabaseAdminRestClient).getList(eq("lessons"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).postList(eq("lesson_concepts"), any(), any(TypeReference.class));
    }
}
