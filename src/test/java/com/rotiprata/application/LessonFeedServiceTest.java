package com.rotiprata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LessonFeedServiceTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;

    @InjectMocks
    private LessonFeedService lessonFeedService;

    @Test
    void getLessonFeedShouldReturnPagedItemsAndHasMore() {
        List<Map<String, Object>> rows = IntStream.range(0, 13)
            .mapToObj(i -> Map.<String, Object>of("id", "lesson-" + i))
            .toList();

        when(
            supabaseRestClient.getList(
                eq("lessons"),
                any(String.class),
                eq("access-token"),
                any(TypeReference.class)
            )
        ).thenReturn(rows);

        LessonFeedResponse response = lessonFeedService.getLessonFeed(
            "access-token",
            1,
            12,
            null,
            null,
            null,
            "newest"
        );

        assertThat(response.items()).hasSize(12);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(12);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(supabaseRestClient).getList(
            eq("lessons"),
            queryCaptor.capture(),
            eq("access-token"),
            any(TypeReference.class)
        );
        String decodedQuery = URLDecoder.decode(queryCaptor.getValue(), StandardCharsets.UTF_8);
        assertThat(decodedQuery).contains("is_published=eq.true");
        assertThat(decodedQuery).contains("order=created_at.desc");
        assertThat(decodedQuery).contains("limit=13");
        assertThat(decodedQuery).contains("offset=0");
    }

    @Test
    void getLessonFeedShouldApplyFiltersAndSort() {
        when(
            supabaseRestClient.getList(
                eq("lessons"),
                any(String.class),
                eq("access-token"),
                any(TypeReference.class)
            )
        ).thenReturn(List.of());

        lessonFeedService.getLessonFeed(
            "access-token",
            2,
            10,
            "rizz",
            2,
            20,
            "popular"
        );

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(supabaseRestClient).getList(
            eq("lessons"),
            queryCaptor.capture(),
            eq("access-token"),
            any(TypeReference.class)
        );
        String decodedQuery = URLDecoder.decode(queryCaptor.getValue(), StandardCharsets.UTF_8);
        assertThat(decodedQuery).contains("difficulty_level=eq.2");
        assertThat(decodedQuery).contains("estimated_minutes=lte.20");
        assertThat(decodedQuery).contains("or=(title.ilike.*rizz*,description.ilike.*rizz*)");
        assertThat(decodedQuery).contains("order=completion_count.desc");
        assertThat(decodedQuery).contains("limit=11");
        assertThat(decodedQuery).contains("offset=10");
    }

    @Test
    void getLessonFeedShouldRejectUnsupportedSort() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> lessonFeedService.getLessonFeed(
                "access-token",
                1,
                12,
                null,
                null,
                null,
                "unknown"
            )
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void getLessonFeedShouldRequireAccessToken() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> lessonFeedService.getLessonFeed(
                "",
                1,
                12,
                null,
                null,
                null,
                "newest"
            )
        );

        assertThat(ex.getStatusCode().value()).isEqualTo(401);
    }
}
