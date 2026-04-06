package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void resolveLinkedLessons_shouldPreferLessonConceptsOverQuizFallback() {
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

        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(Set.of(contentId));

        assertEquals(1, linkedLessons.get(contentId).size());
        assertEquals(ContentLessonLinkService.LinkSource.LESSON_CONCEPT, linkedLessons.get(contentId).get(0).source());
        verify(supabaseAdminRestClient, never()).getList(eq("quizzes"), anyString(), any(TypeReference.class));
    }

    @Test
    void resolveLinkedLessons_shouldFallbackToQuizLinksWhenConceptsMissing() {
        UUID contentId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        when(supabaseAdminRestClient.getList(eq("lesson_concepts"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of());
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

        Map<UUID, List<ContentLessonLinkService.LinkedLesson>> linkedLessons = service.resolveLinkedLessons(Set.of(contentId));

        assertTrue(linkedLessons.containsKey(contentId));
        assertEquals(ContentLessonLinkService.LinkSource.QUIZ_FALLBACK, linkedLessons.get(contentId).get(0).source());
    }

    @Test
    void resolveLinkedLessons_shouldReturnEmptyForEmptyInput() {
        assertTrue(service.resolveLinkedLessons(Set.of()).isEmpty());
        verify(supabaseAdminRestClient, never()).getList(eq("lesson_concepts"), anyString(), any(TypeReference.class));
    }

    @Test
    void resolveLinkedLessons_shouldSkipInactiveLessonsDuringHydration() {
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

        assertTrue(service.resolveLinkedLessons(Set.of(contentId)).isEmpty());
    }

    @Test
    void replaceContentLessonLinks_shouldRejectMissingContentId() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.replaceContentLessonLinks(null, List.of(UUID.randomUUID()))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void replaceContentLessonLinks_shouldIgnoreNullLessonList() {
        service.replaceContentLessonLinks(UUID.randomUUID(), null);

        verify(supabaseAdminRestClient, never()).deleteList(eq("lesson_concepts"), anyString(), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).postList(eq("lesson_concepts"), any(), any(TypeReference.class));
    }

    @Test
    void replaceContentLessonLinks_shouldDeleteExistingLinksWithoutPostingWhenLessonsEmpty() {
        UUID contentId = UUID.randomUUID();

        service.replaceContentLessonLinks(contentId, List.of());

        verify(supabaseAdminRestClient).deleteList(eq("lesson_concepts"), argThat(query -> query.contains(contentId.toString())), any(TypeReference.class));
        verify(supabaseAdminRestClient, never()).postList(eq("lesson_concepts"), any(), any(TypeReference.class));
    }

    @Test
    void replaceContentLessonLinks_shouldDeduplicateLessonsAndPreserveOrder() {
        UUID contentId = UUID.randomUUID();
        UUID firstLessonId = UUID.randomUUID();
        UUID secondLessonId = UUID.randomUUID();

        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of(
                Map.of("id", firstLessonId.toString()),
                Map.of("id", secondLessonId.toString())
            ));
        when(supabaseAdminRestClient.postList(eq("lesson_concepts"), any(), any(TypeReference.class)))
            .thenReturn(List.of());

        // The service keeps the first occurrence so order_index remains stable across re-saves.
        service.replaceContentLessonLinks(contentId, List.of(firstLessonId, firstLessonId, secondLessonId));

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

    @Test
    void replaceContentLessonLinks_shouldRejectUnknownLessonIds() {
        UUID contentId = UUID.randomUUID();
        UUID missingLessonId = UUID.randomUUID();

        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any(TypeReference.class)))
            .thenReturn(List.of());

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.replaceContentLessonLinks(contentId, List.of(missingLessonId))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
