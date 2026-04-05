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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
}
