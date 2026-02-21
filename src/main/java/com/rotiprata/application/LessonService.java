package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.LessonStatsResponse;
import com.rotiprata.domain.Lesson;
import com.rotiprata.domain.UserConceptMastered;
import com.rotiprata.domain.UserLessonProgress;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonService {
    private static final TypeReference<List<Lesson>> LESSON_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UserLessonProgress>> LESSON_PROGRESS_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UserConceptMastered>> CONCEPTS_MASTERED_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;

    public LessonService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<Lesson> fetchLessonFeed(String accessToken, String query, Integer difficulty, Integer maxMinutes, int page, int pageSize) {
        int offset = Math.max(0, (page - 1) * pageSize);
        var queryBuilder = UriComponentsBuilder.newInstance()
            .queryParam("select", "*")
            .queryParam("is_published", "eq.true")
            .queryParam("order", "created_at.desc")
            .queryParam("limit", pageSize)
            .queryParam("offset", offset);

        if (query != null && !query.isBlank()) {
            String contains = "*" + sanitizeLikeValue(query.trim()) + "*";
            queryBuilder.queryParam("or", "(title.ilike." + contains + ",description.ilike." + contains + ")");
        }
        if (difficulty != null) {
            queryBuilder.queryParam("difficulty_level", "eq." + difficulty);
        }
        if (maxMinutes != null) {
            queryBuilder.queryParam("estimated_minutes", "lte." + maxMinutes);
        }

        return supabaseRestClient.getList(
            "lessons",
            queryBuilder.build().getQuery(),
            requireToken(accessToken),
            LESSON_LIST
        );
    }

    public long countLessons(String accessToken, String query, Integer difficulty, Integer maxMinutes) {
        var queryBuilder = UriComponentsBuilder.newInstance()
            .queryParam("select", "id")
            .queryParam("is_published", "eq.true")
            .queryParam("limit", 10000);

        if (query != null && !query.isBlank()) {
            String contains = "*" + sanitizeLikeValue(query.trim()) + "*";
            queryBuilder.queryParam("or", "(title.ilike." + contains + ",description.ilike." + contains + ")");
        }
        if (difficulty != null) {
            queryBuilder.queryParam("difficulty_level", "eq." + difficulty);
        }
        if (maxMinutes != null) {
            queryBuilder.queryParam("estimated_minutes", "lte." + maxMinutes);
        }

        return supabaseRestClient.getList("lessons", queryBuilder.build().getQuery(), requireToken(accessToken), LESSON_LIST).size();
    }

    public List<Lesson> searchLessons(String accessToken, String query) {
        return fetchLessonFeed(accessToken, query, null, null, 1, 24);
    }

    public Lesson getLessonById(String accessToken, UUID lessonId) {
        List<Lesson> lessons = supabaseRestClient.getList(
            "lessons",
            UriComponentsBuilder.newInstance()
                .queryParam("select", "*")
                .queryParam("id", "eq." + lessonId)
                .queryParam("is_published", "eq.true")
                .build().getQuery(),
            requireToken(accessToken),
            LESSON_LIST
        );
        if (lessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        return lessons.get(0);
    }

    public Map<String, Integer> getLessonProgressByLessonId(String accessToken, UUID userId) {
        List<UserLessonProgress> progressRows = supabaseRestClient.getList(
            "user_lesson_progress",
            UriComponentsBuilder.newInstance()
                .queryParam("select", "lesson_id,progress_percentage")
                .queryParam("user_id", "eq." + userId)
                .build().getQuery(),
            requireToken(accessToken),
            LESSON_PROGRESS_LIST
        );

        Map<String, Integer> progress = new HashMap<>();
        for (UserLessonProgress row : progressRows) {
            if (row.getLessonId() != null) {
                progress.put(row.getLessonId().toString(), safeInt(row.getProgressPercentage()));
            }
        }
        return progress;
    }

    public LessonStatsResponse getLessonStats(String accessToken, UUID userId, int currentStreak, double totalHoursLearned) {
        List<UserLessonProgress> progressRows = supabaseRestClient.getList(
            "user_lesson_progress",
            UriComponentsBuilder.newInstance()
                .queryParam("select", "status")
                .queryParam("user_id", "eq." + userId)
                .build().getQuery(),
            requireToken(accessToken),
            LESSON_PROGRESS_LIST
        );

        int completed = (int) progressRows.stream().filter(p -> "completed".equalsIgnoreCase(p.getStatus())).count();
        int enrolled = progressRows.size();

        int conceptsMastered = 0;
        try {
            conceptsMastered = supabaseRestClient.getList(
                "user_concepts_mastered",
                UriComponentsBuilder.newInstance()
                    .queryParam("select", "id")
                    .queryParam("user_id", "eq." + userId)
                    .queryParam("limit", 10000)
                    .build().getQuery(),
                requireToken(accessToken),
                CONCEPTS_MASTERED_LIST
            ).size();
        } catch (ResponseStatusException ignored) {
            // Non-critical stats fallback when table/view is unavailable.
        }

        return new LessonStatsResponse(enrolled, completed, currentStreak, conceptsMastered, totalHoursLearned);
    }

    public void upsertLessonProgress(String accessToken, UUID userId, UUID lessonId, int progress) {
        getLessonById(accessToken, lessonId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("progress_percentage", progress);
        payload.put("status", progress >= 100 ? "completed" : progress > 0 ? "in_progress" : "not_started");
        payload.put("last_accessed_at", OffsetDateTime.now());
        if (progress > 0) {
            payload.put("started_at", OffsetDateTime.now());
        }
        if (progress >= 100) {
            payload.put("completed_at", OffsetDateTime.now());
        }

        List<UserLessonProgress> existing = supabaseRestClient.getList(
            "user_lesson_progress",
            UriComponentsBuilder.newInstance()
                .queryParam("select", "id")
                .queryParam("user_id", "eq." + userId)
                .queryParam("lesson_id", "eq." + lessonId)
                .queryParam("limit", 1)
                .build().getQuery(),
            requireToken(accessToken),
            LESSON_PROGRESS_LIST
        );

        if (existing.isEmpty()) {
            payload.put("user_id", userId);
            payload.put("lesson_id", lessonId);
            supabaseRestClient.postList("user_lesson_progress", payload, requireToken(accessToken), LESSON_PROGRESS_LIST);
            return;
        }

        supabaseRestClient.patchList(
            "user_lesson_progress",
            UriComponentsBuilder.newInstance()
                .queryParam("id", "eq." + existing.get(0).getId())
                .build().getQuery(),
            payload,
            requireToken(accessToken),
            LESSON_PROGRESS_LIST
        );
    }

    private String requireToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String sanitizeLikeValue(String value) {
        return value.replace(",", " ").replace("(", " ").replace(")", " ");
    }
}
