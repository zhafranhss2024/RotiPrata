package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
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
    private final SupabaseAdminRestClient supabaseAdminRestClient;

    public LessonService(SupabaseRestClient supabaseRestClient, SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    public List<Map<String, Object>> getLessons(String accessToken) {
        String token = requireAccessToken(accessToken);
        return supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "*",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "is_published", "eq.true",
                "order", "created_at.desc"
            )),
            token,
            MAP_LIST
        );
    }


    public List<Map<String, Object>> getAdminLessons(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        return supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "*",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
    }

    public Map<String, Object> getAdminLessonById(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        return getAdminLessonById(lessonId);
    }

    public List<Map<String, Object>> searchLessons(String query, String accessToken) {
        String token = requireAccessToken(accessToken);
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.isBlank()) {
            return getLessons(token);
        }
        String safeQuery = escapeQuery(trimmedQuery);
        return supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "*",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "is_published", "eq.true",
                "or", "(title.ilike.*" + safeQuery + "*,description.ilike.*" + safeQuery + "*)",
                "order", "created_at.desc"
            )),
            token,
            MAP_LIST
        );
    }

    public Map<String, Object> getLessonById(UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Map<String, Object>> lessons = supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "id", "eq." + lessonId,
                "select", "*",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "is_published", "eq.true"
            )),
            token,
            MAP_LIST
        );
        if (lessons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson not found");
        }
        return lessons.get(0);
    }

    private Map<String, Object> getAdminLessonById(UUID lessonId) {
        List<Map<String, Object>> lessons = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId, "select", "*")),
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

        List<Map<String, Object>> questions = normalizeQuestions(payload.get("questions"));
        Boolean requestedPublish = parseBoolean(payload.get("is_published"));
        boolean publishRequested = Boolean.TRUE.equals(requestedPublish);

        validateLessonTitle(payload);

        List<String> publishErrors = collectLessonPublishErrors(payload);
        List<String> questionErrors = collectQuestionErrors(questions, publishRequested);
        boolean publish = publishRequested && publishErrors.isEmpty() && questionErrors.isEmpty();

        if (!publishRequested) {
            if (!questions.isEmpty() && !questionErrors.isEmpty()) {
                questions = List.of();
            }
        } else if (!publish) {
            if (!questionErrors.isEmpty()) {
                questions = List.of();
            }
        }

        if (publish) {
            validateLessonFields(payload);
            validateQuestions(questions);
        } else if (!questions.isEmpty()) {
            validateQuestions(questions);
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
        insert.put("is_published", publish);
        insert.put("is_active", true);
        insert.put("archived_at", null);
        insert.put("completion_count", 0);
        insert.put("created_at", OffsetDateTime.now());
        insert.put("updated_at", OffsetDateTime.now());

        List<Map<String, Object>> created = supabaseAdminRestClient.postList("lessons", insert, MAP_LIST);
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create lesson");
        }
        Map<String, Object> lesson = created.get(0);
        if (!questions.isEmpty()) {
            createQuizWithQuestions(userId, lesson, questions);
        }
        return lesson;
    }


    public Map<String, Object> updateLesson(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        Map<String, Object> lesson = getAdminLessonById(lessonId);

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

        Map<String, Object> merged = new LinkedHashMap<>(lesson);
        merged.putAll(patch);

        Boolean requestedPublish = parseBoolean(patch.get("is_published"));
        Boolean currentPublish = parseBoolean(lesson.get("is_published"));
        boolean publishTarget = requestedPublish != null ? requestedPublish : Boolean.TRUE.equals(currentPublish);

        validateLessonTitle(merged);

        if (publishTarget) {
            List<String> publishErrors = collectLessonPublishErrors(merged);
            boolean hasQuestions = hasLessonQuestions(lessonId);
            if (!publishErrors.isEmpty() || !hasQuestions) {
                publishTarget = false;
                patch.put("is_published", false);
            } else {
                validateLessonFields(merged);
                ensureLessonHasQuestions(lessonId);
            }
        }
        patch.put("updated_at", OffsetDateTime.now());

        List<Map<String, Object>> updated = supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            patch,
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

        getAdminLessonById(lessonId);

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("is_active", false);
        patch.put("archived_at", OffsetDateTime.now());
        patch.put("is_published", false);
        patch.put("updated_at", OffsetDateTime.now());

        supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            patch,
            MAP_LIST
        );

        archiveActiveQuiz(lessonId);
    }

    public Map<String, Object> createLessonQuiz(UUID userId, UUID lessonId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        List<Map<String, Object>> questions = normalizeQuestions(payload.get("questions"));
        validateQuestions(questions);
        Map<String, Object> quiz = replaceLessonQuizInternal(userId, lessonId, questions, true);
        if (quiz == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create quiz");
        }
        return quiz;
    }

    public List<Map<String, Object>> getActiveLessonQuizQuestions(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        return getActiveLessonQuizQuestionsInternal(lessonId);
    }

    public List<Map<String, Object>> replaceLessonQuiz(
        UUID userId,
        UUID lessonId,
        Map<String, Object> payload,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        List<Map<String, Object>> questions = normalizeQuestions(payload.get("questions"));
        replaceLessonQuizInternal(userId, lessonId, questions, false);
        return getActiveLessonQuizQuestionsInternal(lessonId);
    }

    private Map<String, Object> replaceLessonQuizInternal(
        UUID userId,
        UUID lessonId,
        List<Map<String, Object>> questions,
        boolean requireAtLeastOne
    ) {
        Map<String, Object> lesson = getAdminLessonById(lessonId);
        Map<String, Object> activeQuiz = findActiveLessonQuiz(lessonId);
        boolean shouldCreate = activeQuiz != null || !questions.isEmpty();

        if (requireAtLeastOne) {
            validateQuestions(questions);
        } else if (!questions.isEmpty()) {
            validateQuestions(questions);
        }

        if (activeQuiz != null) {
            archiveQuizById(activeQuiz.get("id"));
        }

        if (!shouldCreate) {
            return null;
        }

        return createQuizWithQuestions(userId, lesson, questions);
    }

    private List<Map<String, Object>> getActiveLessonQuizQuestionsInternal(UUID lessonId) {
        Map<String, Object> activeQuiz = findActiveLessonQuiz(lessonId);
        if (activeQuiz == null || activeQuiz.get("id") == null) {
            return List.of();
        }
        String quizId = activeQuiz.get("id").toString();
        return supabaseAdminRestClient.getList(
            "quiz_questions",
            buildQuery(Map.of(
                "select", "*",
                "quiz_id", "eq." + quizId,
                "order", "order_index.asc"
            )),
            MAP_LIST
        );
    }

    private Map<String, Object> findActiveLessonQuiz(UUID lessonId) {
        List<Map<String, Object>> quizzes = supabaseAdminRestClient.getList(
            "quizzes",
            buildQuery(Map.of(
                "select", "*",
                "lesson_id", "eq." + lessonId,
                "is_active", "eq.true",
                "archived_at", "is.null",
                "order", "created_at.desc",
                "limit", "1"
            )),
            MAP_LIST
        );
        if (quizzes.isEmpty()) {
            return null;
        }
        return quizzes.get(0);
    }

    private void archiveActiveQuiz(UUID lessonId) {
        Map<String, Object> activeQuiz = findActiveLessonQuiz(lessonId);
        if (activeQuiz == null || activeQuiz.get("id") == null) {
            return;
        }
        archiveQuizById(activeQuiz.get("id"));
    }

    private void archiveQuizById(Object quizId) {
        if (quizId == null) {
            return;
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("is_active", false);
        patch.put("archived_at", OffsetDateTime.now());
        patch.put("updated_at", OffsetDateTime.now());
        supabaseAdminRestClient.patchList(
            "quizzes",
            buildQuery(Map.of("id", "eq." + quizId.toString())),
            patch,
            MAP_LIST
        );
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
        List<Map<String, Object>> roles = supabaseAdminRestClient.getList(
            "user_roles",
            buildQuery(
                Map.of(
                    "select", "id",
                    "user_id", "eq." + userId,
                    "role", "eq.admin"
                )
            ),
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

    private String escapeQuery(String query) {
        return query.replace(",", " ").replace("(", " ").replace(")", " ");
    }

    private void validateLessonTitle(Map<String, Object> lesson) {
        if (stringValue(lesson.get("title")) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson title is required");
        }
    }

    private void validateLessonFields(Map<String, Object> lesson) {
        List<String> errors = collectLessonPublishErrors(lesson);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Lesson validation failed: missing/invalid " + String.join(", ", errors)
            );
        }
    }

    private void validateQuestions(List<Map<String, Object>> questions) {
        List<String> errors = collectQuestionErrors(questions, true);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Quiz validation failed: missing/invalid " + String.join(", ", errors)
            );
        }
    }

    private void ensureLessonHasQuestions(UUID lessonId) {
        if (!hasLessonQuestions(lessonId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson has no quiz questions");
        }
    }

    private boolean hasLessonQuestions(UUID lessonId) {
        Map<String, Object> activeQuiz = findActiveLessonQuiz(lessonId);
        if (activeQuiz == null || activeQuiz.get("id") == null) {
            return false;
        }
        String quizId = activeQuiz.get("id").toString();
        List<Map<String, Object>> questions = supabaseAdminRestClient.getList(
            "quiz_questions",
            buildQuery(Map.of("select", "id", "quiz_id", "eq." + quizId)),
            MAP_LIST
        );
        return !questions.isEmpty();
    }

    private List<String> collectLessonPublishErrors(Map<String, Object> lesson) {
        List<String> errors = new ArrayList<>();

        requireField(lesson, "title", errors);
        requireField(lesson, "summary", errors);
        requireField(lesson, "description", errors);
        requireField(lesson, "origin_content", errors);
        requireField(lesson, "definition_content", errors);
        requireField(lesson, "lore_content", errors);
        requireField(lesson, "evolution_content", errors);
        requireField(lesson, "comparison_content", errors);
        requireField(lesson, "badge_name", errors);

        List<String> objectives = extractStringList(lesson.get("learning_objectives"));
        if (objectives.isEmpty()) {
            errors.add("learning_objectives");
        }
        List<String> examples = extractStringList(lesson.get("usage_examples"));
        if (examples.isEmpty()) {
            errors.add("usage_examples");
        }

        Integer minutes = parseInteger(lesson.get("estimated_minutes"));
        if (minutes == null || minutes <= 0) {
            errors.add("estimated_minutes");
        }
        Integer xp = parseInteger(lesson.get("xp_reward"));
        if (xp == null || xp <= 0) {
            errors.add("xp_reward");
        }
        Integer difficulty = parseInteger(lesson.get("difficulty_level"));
        if (difficulty == null || difficulty < 1 || difficulty > 3) {
            errors.add("difficulty_level");
        }

        return errors;
    }

    private List<String> collectQuestionErrors(List<Map<String, Object>> questions, boolean requireAtLeastOne) {
        if (questions == null || questions.isEmpty()) {
            return requireAtLeastOne ? List.of("questions") : List.of();
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            String prefix = "questions[" + i + "].";
            if (stringValue(q.get("question_text")) == null) {
                errors.add(prefix + "question_text");
            }
            if (stringValue(q.get("explanation")) == null) {
                errors.add(prefix + "explanation");
            }
            String correct = stringValue(q.get("correct_answer"));
            if (correct == null || !List.of("A", "B", "C", "D").contains(correct)) {
                errors.add(prefix + "correct_answer");
            }
            Integer points = parseInteger(q.get("points"));
            if (points == null || points < 1 || points > 100) {
                errors.add(prefix + "points");
            }

            Map<String, Object> options = extractOptions(q.get("options"));
            for (String key : List.of("A", "B", "C", "D")) {
                String value = stringValue(options.get(key));
                if (value == null) {
                    errors.add(prefix + "options." + key);
                }
            }
        }
        return errors;
    }

    private Map<String, Object> createQuizWithQuestions(UUID userId, Map<String, Object> lesson, List<Map<String, Object>> questions) {
        UUID lessonId = UUID.fromString(lesson.get("id").toString());

        Map<String, Object> quizInsert = new LinkedHashMap<>();
        quizInsert.put("lesson_id", lessonId);
        quizInsert.put("title", lesson.getOrDefault("title", "Lesson Quiz") + " Quiz");
        quizInsert.put("description", "Auto-generated quiz for lesson");
        quizInsert.put("quiz_type", "multiple_choice");
        quizInsert.put("is_active", true);
        quizInsert.put("archived_at", null);
        quizInsert.put("created_by", userId);
        quizInsert.put("updated_at", OffsetDateTime.now());

        List<Map<String, Object>> quizzes = supabaseAdminRestClient.postList("quizzes", quizInsert, MAP_LIST);
        if (quizzes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create quiz");
        }

        Map<String, Object> quiz = quizzes.get(0);
        Object quizId = quiz.get("id");
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> rawMap = questions.get(i);
            Map<String, Object> questionMap = new LinkedHashMap<>();
            questionMap.put("quiz_id", quizId);
            questionMap.put("question_text", stringValue(rawMap.get("question_text")));
            questionMap.put("question_type", stringValue(rawMap.get("question_type")) != null
                ? stringValue(rawMap.get("question_type"))
                : "multiple_choice");
            questionMap.put("options", extractOptions(rawMap.get("options")));
            questionMap.put("correct_answer", stringValue(rawMap.get("correct_answer")));
            questionMap.put("explanation", stringValue(rawMap.get("explanation")));
            questionMap.put("points", parseInteger(rawMap.get("points")));
            questionMap.put("order_index", parseInteger(rawMap.get("order_index")) != null ? parseInteger(rawMap.get("order_index")) : i);
            supabaseAdminRestClient.postList("quiz_questions", questionMap, MAP_LIST);
        }

        return quiz;
    }

    private void requireField(Map<String, Object> lesson, String key, List<String> errors) {
        if (stringValue(lesson.get(key)) == null) {
            errors.add(key);
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private List<String> extractStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .map(item -> item == null ? null : item.toString().trim())
            .filter(item -> item != null && !item.isBlank())
            .toList();
    }

    private List<Map<String, Object>> normalizeQuestions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> tempMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) tempMap;
            questions.add(map);
        }
        return questions;
    }

    private Map<String, Object> extractOptions(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> options = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString().trim();
                if (key == null) {
                    continue;
                }
                options.put(key, entry.getValue());
            }
            return options;
        }
        return new LinkedHashMap<>();
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
