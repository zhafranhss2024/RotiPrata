package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseRestClient supabaseRestClient;

    public LessonService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<Map<String, Object>> getLessons(String accessToken) {
        String token = requireAccessToken(accessToken);
        return supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of("select", "*", "order", "created_at.desc")),
            token,
            MAP_LIST
        );
    }


    public List<Map<String, Object>> getAdminLessons(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        return getLessons(token);
    }

    public List<Map<String, Object>> searchLessons(String query, String accessToken) {
        String token = requireAccessToken(accessToken);
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isBlank()) {
            return getLessons(token);
        }
        return supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of("select", "*", "title", "ilike.*" + safeQuery + "*", "order", "created_at.desc")),
            token,
            MAP_LIST
        );
    }

    public Map<String, Object> getLessonById(UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Map<String, Object>> lessons = supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId, "select", "*")),
            token,
            MAP_LIST
        );
        if (lessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        return lessons.get(0);
    }

    public List<Map<String, Object>> getLessonSections(UUID lessonId, String accessToken) {
        Map<String, Object> lesson = getLessonById(lessonId, accessToken);
        List<Map<String, Object>> sections = new ArrayList<>();
        addSection(sections, "intro", "Origin", lesson.get("origin_content"), 1);
        addSection(sections, "definition", "Definition", lesson.get("definition_content"), 2);
        addSection(sections, "usage", "Usage Examples", lesson.get("usage_examples"), 3);
        addSection(sections, "lore", "Lore", lesson.get("lore_content"), 4);
        addSection(sections, "evolution", "Evolution", lesson.get("evolution_content"), 5);
        addSection(sections, "comparison", "Comparison", lesson.get("comparison_content"), 6);
        return sections;
    }

    public Map<String, Object> createLesson(UUID userId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        if (payload.get("title") == null || payload.get("title").toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson title is required");
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("created_by", userId);
        copyIfPresent(payload, insert, "title");
        copyIfPresent(payload, insert, "description");
        copyIfPresent(payload, insert, "summary");
        copyIfPresent(payload, insert, "learning_objectives");
        copyIfPresent(payload, insert, "estimated_minutes");
        copyIfPresent(payload, insert, "xp_reward");
        copyIfPresent(payload, insert, "badge_name");
        copyIfPresent(payload, insert, "difficulty_level");
        copyIfPresent(payload, insert, "origin_content");
        copyIfPresent(payload, insert, "definition_content");
        copyIfPresent(payload, insert, "usage_examples");
        copyIfPresent(payload, insert, "lore_content");
        copyIfPresent(payload, insert, "evolution_content");
        copyIfPresent(payload, insert, "comparison_content");
        insert.put("is_published", true);

        List<Map<String, Object>> created = supabaseRestClient.postList("lessons", insert, token, MAP_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create lesson");
        }
        return created.get(0);
    }


    public Map<String, Object> updateLesson(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        Map<String, Object> lesson = getLessonById(lessonId, token);

        Map<String, Object> patch = new LinkedHashMap<>();
        copyIfPresent(payload, patch, "title");
        copyIfPresent(payload, patch, "description");
        copyIfPresent(payload, patch, "summary");
        copyIfPresent(payload, patch, "learning_objectives");
        copyIfPresent(payload, patch, "estimated_minutes");
        copyIfPresent(payload, patch, "xp_reward");
        copyIfPresent(payload, patch, "badge_name");
        copyIfPresent(payload, patch, "difficulty_level");
        copyIfPresent(payload, patch, "origin_content");
        copyIfPresent(payload, patch, "definition_content");
        copyIfPresent(payload, patch, "usage_examples");
        copyIfPresent(payload, patch, "lore_content");
        copyIfPresent(payload, patch, "evolution_content");
        copyIfPresent(payload, patch, "comparison_content");
        copyIfPresent(payload, patch, "is_published");

        if (patch.isEmpty()) {
            return lesson;
        }

        List<Map<String, Object>> updated = supabaseRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            patch,
            token,
            MAP_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to update lesson");
        }
        return updated.get(0);
    }

    public void deleteLesson(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        getLessonById(lessonId, token);

        supabaseRestClient.deleteList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            token,
            MAP_LIST
        );
    }

    public Map<String, Object> createLessonQuiz(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        Map<String, Object> lesson = getLessonById(lessonId, token);

        Map<String, Object> quizInsert = new LinkedHashMap<>();
        quizInsert.put("lesson_id", lessonId);
        quizInsert.put("title", lesson.getOrDefault("title", "Lesson Quiz") + " Quiz");
        quizInsert.put("description", "Auto-generated quiz for lesson");
        quizInsert.put("quiz_type", "multiple_choice");
        quizInsert.put("created_by", userId);

        List<Map<String, Object>> quizzes = supabaseRestClient.postList("quizzes", quizInsert, token, MAP_LIST);
        if (quizzes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create quiz");
        }

        Map<String, Object> quiz = quizzes.get(0);
        Object questionsObj = payload.get("questions");
        if (questionsObj instanceof List<?> questions) {
            for (int i = 0; i < questions.size(); i++) {
                Object rawQuestion = questions.get(i);
        
                if (!(rawQuestion instanceof Map<?, ?> tempMap)) {
                    continue;
                }
        
                @SuppressWarnings("unchecked")
                Map<String, Object> rawMap = (Map<String, Object>) tempMap;
        
                Map<String, Object> questionMap = new LinkedHashMap<>();
                questionMap.put("quiz_id", quiz.get("id"));
        
                copyIfPresent(rawMap, questionMap, "question_text");
                copyIfPresent(rawMap, questionMap, "question_type");
                copyIfPresent(rawMap, questionMap, "options");
                copyIfPresent(rawMap, questionMap, "correct_answer");
                copyIfPresent(rawMap, questionMap, "explanation");
        
                questionMap.put("points",
                        rawMap.getOrDefault("points", 10));
        
                questionMap.put("order_index",
                        rawMap.getOrDefault("order_index", i));
        
                supabaseRestClient.postList("quiz_questions", questionMap, token, MAP_LIST);
            }
        }
        

        return quiz;
    }

    public void enrollLesson(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        getLessonById(lessonId, token);

        List<Map<String, Object>> existing = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of("select", "id,status", "user_id", "eq." + userId, "lesson_id", "eq." + lessonId)),
            token,
            MAP_LIST
        );

        if (existing.isEmpty()) {
            Map<String, Object> insert = new LinkedHashMap<>();
            insert.put("user_id", userId);
            insert.put("lesson_id", lessonId);
            insert.put("status", "in_progress");
            insert.put("progress_percentage", 0);
            insert.put("started_at", OffsetDateTime.now());
            insert.put("last_accessed_at", OffsetDateTime.now());
            supabaseRestClient.postList("user_lesson_progress", insert, token, MAP_LIST);
            return;
        }

        Map<String, Object> patch = new HashMap<>();
        patch.put("last_accessed_at", OffsetDateTime.now());
        if ("not_started".equals(existing.get(0).get("status"))) {
            patch.put("status", "in_progress");
        }
        supabaseRestClient.patchList(
            "user_lesson_progress",
            buildQuery(Map.of("user_id", "eq." + userId, "lesson_id", "eq." + lessonId)),
            patch,
            token,
            MAP_LIST
        );
    }

    public void updateLessonProgress(UUID userId, UUID lessonId, int progress, String accessToken) {
        String token = requireAccessToken(accessToken);
        int clampedProgress = Math.max(0, Math.min(progress, 100));
        Map<String, Object> patch = new HashMap<>();
        patch.put("progress_percentage", clampedProgress);
        patch.put("last_accessed_at", OffsetDateTime.now());
        patch.put("status", clampedProgress >= 100 ? "completed" : "in_progress");
        if (clampedProgress >= 100) {
            patch.put("completed_at", OffsetDateTime.now());
        }

        List<Map<String, Object>> updated = supabaseRestClient.patchList(
            "user_lesson_progress",
            buildQuery(Map.of("user_id", "eq." + userId, "lesson_id", "eq." + lessonId)),
            patch,
            token,
            MAP_LIST
        );
        if (updated.isEmpty()) {
            enrollLesson(userId, lessonId, token);
            supabaseRestClient.patchList(
                "user_lesson_progress",
                buildQuery(Map.of("user_id", "eq." + userId, "lesson_id", "eq." + lessonId)),
                patch,
                token,
                MAP_LIST
            );
        }
    }

    public void saveLesson(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        getLessonById(lessonId, token);

        List<Map<String, Object>> existing = supabaseRestClient.getList(
            "saved_content",
            buildQuery(Map.of("select", "id", "user_id", "eq." + userId, "lesson_id", "eq." + lessonId)),
            token,
            MAP_LIST
        );
        if (!existing.isEmpty()) {
            return;
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("lesson_id", lessonId);
        supabaseRestClient.postList("saved_content", insert, token, MAP_LIST);
    }

    public Map<String, Integer> getUserLessonProgress(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of("select", "lesson_id,progress_percentage", "user_id", "eq." + userId)),
            token,
            MAP_LIST
        );

        Map<String, Integer> progress = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object lessonId = row.get("lesson_id");
            Object percentage = row.get("progress_percentage");
            if (lessonId == null) {
                continue;
            }
            int value = percentage instanceof Number n ? n.intValue() : 0;
            progress.put(lessonId.toString(), value);
        }
        return progress;
    }

    public Map<String, Integer> getUserStats(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);

        List<Map<String, Object>> progressRows = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of("select", "progress_percentage,status", "user_id", "eq." + userId)),
            token,
            MAP_LIST
        );

        int lessonsEnrolled = progressRows.size();
        int lessonsCompleted = (int) progressRows.stream()
            .filter(row -> {
                Object status = row.get("status");
                Object pct = row.get("progress_percentage");
                return "completed".equals(status) || (pct instanceof Number n && n.intValue() >= 100);
            })
            .count();

        List<Map<String, Object>> masteredRows = supabaseRestClient.getList(
            "user_concepts_mastered",
            buildQuery(Map.of("select", "id", "user_id", "eq." + userId)),
            token,
            MAP_LIST
        );

        Map<String, Integer> stats = new HashMap<>();
        stats.put("lessonsEnrolled", lessonsEnrolled);
        stats.put("lessonsCompleted", lessonsCompleted);
        stats.put("currentStreak", 0);
        stats.put("conceptsMastered", masteredRows.size());
        stats.put("hoursLearned", 0);
        return stats;
    }

    private void ensureAdmin(UUID userId, String accessToken) {
        List<Map<String, Object>> roles = supabaseRestClient.getList(
            "user_roles",
            buildQuery(
                Map.of(
                    "select", "id",
                    "user_id", "eq." + userId,
                    "or", "(role.eq.admin,role.eq.super_admin)"
                )
            ),
            accessToken,
            MAP_LIST
        );
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }


    private void addSection(List<Map<String, Object>> sections, String id, String title, Object rawContent, int order) {
        String content = stringify(rawContent);
        if (content == null || content.isBlank()) {
            return;
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", id);
        section.put("title", title);
        section.put("content", content);
        section.put("order_index", order);
        section.put("duration_minutes", 3);
        section.put("completed", false);
        sections.add(section);
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).reduce((a, b) -> a + "\n" + b).orElse("");
        }
        return value.toString();
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }
}
