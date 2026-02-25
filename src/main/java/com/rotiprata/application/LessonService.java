package com.rotiprata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.AdminLessonDraftResponse;
import com.rotiprata.api.dto.AdminLessonWizardStep;
import com.rotiprata.api.dto.AdminPublishLessonResponse;
import com.rotiprata.api.dto.AdminStepSaveRequest;
import com.rotiprata.api.dto.AdminStepSaveResponse;
import com.rotiprata.api.dto.AdminValidationError;
import com.rotiprata.api.dto.LessonFeedRequest;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.api.dto.LessonHubLessonResponse;
import com.rotiprata.api.dto.LessonHubResponse;
import com.rotiprata.api.dto.LessonHubSummaryResponse;
import com.rotiprata.api.dto.LessonHubUnitResponse;
import com.rotiprata.api.dto.LessonProgressResponse;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonService {
    private static final int DEFAULT_LESSON_FEED_PAGE = 1;
    private static final int DEFAULT_LESSON_FEED_PAGE_SIZE = 12;
    private static final int MAX_LESSON_FEED_PAGE_SIZE = 50;
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final Set<String> SUPPORTED_QUIZ_TYPES = Set.of(
        "multiple_choice",
        "true_false",
        "cloze",
        "word_bank",
        "conversation",
        "match_pairs",
        "short_text"
    );

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final LessonQuizService lessonQuizService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LessonService(
        SupabaseRestClient supabaseRestClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        LessonQuizService lessonQuizService
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.lessonQuizService = lessonQuizService;
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

    public LessonFeedResponse getLessonFeed(String accessToken, LessonFeedRequest request) {
        String token = requireAccessToken(accessToken);
        int page = request == null ? DEFAULT_LESSON_FEED_PAGE : normalizeLessonFeedPage(request.page());
        int pageSize = request == null ? DEFAULT_LESSON_FEED_PAGE_SIZE : normalizeLessonFeedPageSize(request.pageSize());
        int offset = (page - 1) * pageSize;
        int limit = pageSize + 1;

        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("select", "*");
        params.put("is_published", "eq.true");
        params.put("is_active", "eq.true");
        params.put("archived_at", "is.null");
        applyQueryFilter(request == null ? null : request.query(), params);
        applyDifficultyFilter(request == null ? null : request.difficulty(), params);
        applyDurationFilter(request == null ? null : request.duration(), params);
        params.put("order", resolveSort(request == null ? null : request.sort()));
        params.put("limit", String.valueOf(limit));
        params.put("offset", String.valueOf(offset));

        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "lessons",
            buildQuery(params),
            token,
            MAP_LIST
        );
        boolean hasMore = rows.size() > pageSize;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, pageSize) : rows;
        return new LessonFeedResponse(items, hasMore, page, pageSize);
    }

    public LessonHubResponse getLessonHub(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<Map<String, Object>> lessons = supabaseRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "*",
                "is_active", "eq.true",
                "archived_at", "is.null",
                "is_published", "eq.true",
                "order", "created_at.asc"
            )),
            token,
            MAP_LIST
        );

        Map<String, Integer> progressByLesson = getUserLessonProgress(userId, token);
        int completedLessons = 0;
        int currentIndex = -1;
        for (int i = 0; i < lessons.size(); i++) {
            String lessonId = stringValue(lessons.get(i).get("id"));
            int progress = lessonId == null ? 0 : progressByLesson.getOrDefault(lessonId, 0);
            if (progress >= 100) {
                completedLessons += 1;
                continue;
            }
            if (currentIndex < 0) {
                currentIndex = i;
            }
        }
        if (currentIndex < 0 && !lessons.isEmpty()) {
            currentIndex = lessons.size() - 1;
        }

        final String[] palette = new String[] { "green", "blue", "orange", "pink" };
        final int unitSize = 6;
        List<LessonHubUnitResponse> units = new ArrayList<>();
        for (int start = 0, unitNumber = 1; start < lessons.size(); start += unitSize, unitNumber++) {
            int end = Math.min(start + unitSize, lessons.size());
            List<LessonHubLessonResponse> unitLessons = new ArrayList<>();
            for (int i = start; i < end; i++) {
                Map<String, Object> lesson = lessons.get(i);
                String lessonIdRaw = stringValue(lesson.get("id"));
                UUID lessonId = lessonIdRaw == null ? null : UUID.fromString(lessonIdRaw);
                int progress = lessonIdRaw == null ? 0 : progressByLesson.getOrDefault(lessonIdRaw, 0);
                boolean completed = progress >= 100;
                boolean current = i == currentIndex;
                boolean visuallyLocked = !completed && currentIndex >= 0 && i > currentIndex;
                unitLessons.add(
                    new LessonHubLessonResponse(
                        lessonId,
                        stringValue(lesson.get("title")),
                        parseInteger(lesson.get("difficulty_level")),
                        parseInteger(lesson.get("estimated_minutes")),
                        parseInteger(lesson.get("xp_reward")),
                        parseInteger(lesson.get("completion_count")),
                        progress,
                        completed,
                        current,
                        visuallyLocked
                    )
                );
            }
            units.add(
                new LessonHubUnitResponse(
                    "unit-" + unitNumber,
                    "Unit " + unitNumber,
                    unitNumber,
                    palette[(unitNumber - 1) % palette.length],
                    unitLessons
                )
            );
        }

        int streak = 0;
        try {
            streak = getUserStats(userId, token).getOrDefault("currentStreak", 0);
        } catch (Exception ignored) {
            // Best-effort summary stat.
        }
        return new LessonHubResponse(
            units,
            new LessonHubSummaryResponse(lessons.size(), completedLessons, streak)
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

    public AdminLessonDraftResponse createLessonDraft(UUID userId, Map<String, Object> payload, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        Map<String, Object> input = payload == null ? Map.of() : payload;
        Map<String, Object> draftPayload = new LinkedHashMap<>();
        String title = stringValue(input.get("title"));
        draftPayload.put("title", title == null ? "Untitled Lesson " + OffsetDateTime.now().toLocalDate() : title);
        draftPayload.put("is_published", false);

        copyIfPresent(input, draftPayload, "summary");
        copyIfPresent(input, draftPayload, "description");
        copyIfPresent(input, draftPayload, "estimated_minutes");
        copyIfPresent(input, draftPayload, "xp_reward");
        copyIfPresent(input, draftPayload, "difficulty_level");
        copyIfPresent(input, draftPayload, "badge_name");

        Map<String, Object> lesson = createLesson(userId, draftPayload, token);
        UUID lessonId = UUID.fromString(lesson.get("id").toString());
        List<Map<String, Object>> questions = getActiveLessonQuizQuestionsInternal(lessonId);
        return new AdminLessonDraftResponse(
            lessonId,
            computeWizardCompleteness(lesson, questions),
            lesson
        );
    }

    public AdminStepSaveResponse saveLessonStep(
        UUID userId,
        UUID lessonId,
        String stepKey,
        AdminStepSaveRequest request,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        AdminLessonWizardStep step = AdminLessonWizardStep.fromKey(stepKey);

        Map<String, Object> lesson = getAdminLessonById(lessonId);
        Map<String, Object> lessonPatch = request == null || request.lesson() == null ? Map.of() : request.lesson();
        List<Map<String, Object>> questionsInput = request == null ? List.of() : normalizeQuestions(request.questions());
        Map<String, Object> mergedLesson = new LinkedHashMap<>(lesson);
        mergedLesson.putAll(lessonPatch);

        List<Map<String, Object>> validationQuestionSource = questionsInput.isEmpty()
            ? getActiveLessonQuizQuestionsInternal(lessonId)
            : questionsInput;
        List<AdminValidationError> errors = validateStepDraft(step, mergedLesson, validationQuestionSource);
        if (!errors.isEmpty()) {
            return new AdminStepSaveResponse(
                step.key(),
                false,
                errors,
                mergedLesson,
                computeWizardCompleteness(mergedLesson, validationQuestionSource)
            );
        }

        persistLessonStepPatch(lessonId, step, lessonPatch);
        boolean quizPersisted = false;
        if ((step == AdminLessonWizardStep.QUIZ_SETUP || step == AdminLessonWizardStep.QUIZ_BUILDER)
            && !questionsInput.isEmpty()) {
            if (collectQuestionErrors(questionsInput, true).isEmpty()) {
                replaceLessonQuizInternal(userId, lessonId, questionsInput, false);
                quizPersisted = true;
            }
        }

        Map<String, Object> refreshedLesson = getAdminLessonById(lessonId);
        List<Map<String, Object>> refreshedQuestions = quizPersisted
            ? getActiveLessonQuizQuestionsInternal(lessonId)
            : validationQuestionSource;
        return new AdminStepSaveResponse(
            step.key(),
            true,
            List.of(),
            refreshedLesson,
            computeWizardCompleteness(refreshedLesson, refreshedQuestions)
        );
    }

    public AdminPublishLessonResponse publishLessonWithValidation(
        UUID userId,
        UUID lessonId,
        AdminStepSaveRequest request,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);

        Map<String, Object> lesson = getAdminLessonById(lessonId);
        Map<String, Object> lessonPatch = request == null || request.lesson() == null ? Map.of() : request.lesson();
        List<Map<String, Object>> questionsInput = request == null ? List.of() : normalizeQuestions(request.questions());
        Map<String, Object> mergedLesson = new LinkedHashMap<>(lesson);
        mergedLesson.putAll(lessonPatch);

        List<Map<String, Object>> publishQuestions = questionsInput.isEmpty()
            ? getActiveLessonQuizQuestionsInternal(lessonId)
            : questionsInput;
        List<AdminValidationError> errors = validatePublishFull(mergedLesson, publishQuestions);
        if (!errors.isEmpty()) {
            return new AdminPublishLessonResponse(
                false,
                firstInvalidStep(errors),
                errors,
                mergedLesson
            );
        }

        if (!questionsInput.isEmpty()) {
            replaceLessonQuizInternal(userId, lessonId, questionsInput, true);
        }

        Map<String, Object> publishPatch = new LinkedHashMap<>();
        applyEditableLessonFields(mergedLesson, publishPatch);
        publishPatch.put("is_published", true);
        publishPatch.put("is_active", true);
        publishPatch.put("updated_at", OffsetDateTime.now());
        supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            publishPatch,
            MAP_LIST
        );

        Map<String, Object> refreshedLesson = getAdminLessonById(lessonId);
        return new AdminPublishLessonResponse(
            true,
            null,
            List.of(),
            refreshedLesson
        );
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
        return buildLessonSections(lesson);
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
        if (!questionErrors.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Quiz validation failed: missing/invalid " + String.join(", ", questionErrors)
            );
        }
        boolean publish = publishRequested && publishErrors.isEmpty() && questionErrors.isEmpty();

        if (!publishRequested) {
            // Keep draft mode permissive for lesson fields but strict for quiz schema.
        } else if (!publish) {
            // Publish toggles off when lesson content is incomplete.
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

    public List<Map<String, Object>> getAdminQuizQuestionTypes(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        ensureAdmin(userId, token);
        List<Map<String, Object>> types = new ArrayList<>();
        types.add(questionTypeMeta("multiple_choice", "Single-select choices (A/B/C/D)", "{ \"choices\": { \"A\": \"\", \"B\": \"\" } }", "A"));
        types.add(questionTypeMeta("true_false", "Boolean true/false question", "{}", "true"));
        types.add(questionTypeMeta("cloze", "Fill blank(s) with provided choices", "{ \"blankOptions\": { \"blank1\": { \"A\": \"\", \"B\": \"\" } } }", "{ \"blank1\": \"A\" }"));
        types.add(questionTypeMeta("word_bank", "Build answer from ordered token list", "{ \"tokens\": [ { \"id\": \"t1\", \"text\": \"hello\" }, { \"id\": \"t2\", \"text\": \"world\" } ] }", "[\"t1\",\"t2\"]"));
        types.add(questionTypeMeta("conversation", "Choose best reply for each turn", "{ \"turns\": [ { \"id\": \"turn_1\", \"prompt\": \"\", \"replies\": [ { \"id\": \"r1\", \"text\": \"\" }, { \"id\": \"r2\", \"text\": \"\" } ] } ] }", "{ \"turn_1\": \"r1\" }"));
        types.add(questionTypeMeta("match_pairs", "Match left and right items", "{ \"left\": [ { \"id\": \"l1\", \"text\": \"\" } ], \"right\": [ { \"id\": \"r1\", \"text\": \"\" } ] }", "{ \"l1\": \"r1\" }"));
        types.add(questionTypeMeta("short_text", "Free text answer compared server-side", "{ \"placeholder\": \"Type answer\", \"minLength\": 1, \"maxLength\": 120 }", "{\"accepted\":[\"example answer\"]}"));
        return types;
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

    public LessonProgressResponse getLessonProgress(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        Map<String, Object> lesson = getLessonById(lessonId, token);
        List<Map<String, Object>> sections = buildLessonSections(lesson);
        LessonProgressState state = loadLessonProgressState(userId, lessonId, sections, token);
        LessonQuizService.ProgressMetadata metadata = lessonQuizService.getProgressMetadata(
            userId,
            lessonId,
            sections,
            state.completedSections(),
            state.isEnrolled(),
            token
        );
        return buildProgressResponse(state, sections, metadata);
    }

    public LessonProgressResponse completeLessonSection(UUID userId, UUID lessonId, String sectionId, String accessToken) {
        String token = requireAccessToken(accessToken);
        Map<String, Object> lesson = getLessonById(lessonId, token);
        List<Map<String, Object>> sections = buildLessonSections(lesson);
        LessonProgressState state = loadLessonProgressState(userId, lessonId, sections, token);
        return completeLessonSectionInternal(userId, lessonId, sectionId, sections, state, token);
    }

    public void enrollLesson(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        Map<String, Object> lesson = getLessonById(lessonId, token);
        List<Map<String, Object>> sections = buildLessonSections(lesson);
        LessonProgressState state = loadLessonProgressState(userId, lessonId, sections, token);
        OffsetDateTime now = OffsetDateTime.now();

        if (state.isEnrolled() && state.progressRowId() != null) {
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("last_accessed_at", now);
            if ("not_started".equals(state.status())) {
                patch.put("status", "in_progress");
            }
            if (state.startedAt() == null) {
                patch.put("started_at", now);
            }
            patchProgressRowById(state.progressRowId(), patch, token);
            return;
        }

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("lesson_id", lessonId);
        insert.put("status", "in_progress");
        insert.put("progress_percentage", 0);
        insert.put("current_section", null);
        insert.put("started_at", now);
        insert.put("last_accessed_at", now);
        insert.put("completed_at", null);
        try {
            supabaseRestClient.postList("user_lesson_progress", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
            LessonProgressState refreshed = loadLessonProgressState(userId, lessonId, sections, token);
            if (refreshed.progressRowId() != null) {
                patchProgressRowById(
                    refreshed.progressRowId(),
                    Map.of("last_accessed_at", now),
                    token
                );
            }
        }
    }

    public void updateLessonProgress(UUID userId, UUID lessonId, int progress, String accessToken) {
        String token = requireAccessToken(accessToken);
        Map<String, Object> lesson = getLessonById(lessonId, token);
        List<Map<String, Object>> sections = buildLessonSections(lesson);
        if (sections.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson has no readable sections");
        }

        LessonProgressState state = loadLessonProgressState(userId, lessonId, sections, token);
        int clampedProgress = Math.max(0, Math.min(progress, 100));
        int currentProgress = state.progressPercentage();
        if (clampedProgress < currentProgress) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Progress regression is not allowed");
        }
        if (clampedProgress == currentProgress) {
            if (state.progressRowId() != null) {
                patchProgressRowById(
                    state.progressRowId(),
                    Map.of("last_accessed_at", OffsetDateTime.now()),
                    token
                );
            }
            return;
        }

        int totalSections = sections.size();
        int completed = state.completedSections();
        boolean hasQuiz = lessonQuizService.hasActiveLessonQuiz(lessonId);
        if (completed >= totalSections) {
            String message = hasQuiz
                ? "Complete the quiz to finish this lesson"
                : "Lesson is already completed";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
        int totalStops = totalSections + (hasQuiz ? 1 : 0);
        int nextProgress = toProgressPercentage(completed + 1, totalStops);
        if (clampedProgress != nextProgress) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Deprecated progress endpoint only supports advancing one section at a time"
            );
        }
        String nextSectionId = sectionIdAt(sections, completed);
        completeLessonSectionInternal(userId, lessonId, nextSectionId, sections, state, token);
    }

    public void saveLesson(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        getLessonById(lessonId, token);

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("lesson_id", lessonId);
        insert.put("saved_at", OffsetDateTime.now());
        try {
            supabaseRestClient.postList("saved_content", insert, token, MAP_LIST);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
        }
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
            progress.merge(lessonId.toString(), value, Math::max);
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
        int currentStreak = 0;
        List<Map<String, Object>> profileRows = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("select", "current_streak", "user_id", "eq." + userId, "limit", "1")),
            token,
            MAP_LIST
        );
        if (!profileRows.isEmpty()) {
            currentStreak = parseInteger(profileRows.get(0).get("current_streak")) == null
                ? 0
                : parseInteger(profileRows.get(0).get("current_streak"));
        }
        stats.put("currentStreak", currentStreak);
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

    private List<Map<String, Object>> buildLessonSections(Map<String, Object> lesson) {
        List<Map<String, Object>> sections = new ArrayList<>();
        addSection(sections, LessonFlowConstants.SECTION_INTRO, "Origin", lesson.get("origin_content"), 1);
        addSection(sections, LessonFlowConstants.SECTION_DEFINITION, "Definition", lesson.get("definition_content"), 2);
        addSection(sections, LessonFlowConstants.SECTION_USAGE, "Usage Examples", lesson.get("usage_examples"), 3);
        addSection(sections, LessonFlowConstants.SECTION_LORE, "Lore", lesson.get("lore_content"), 4);
        addSection(sections, LessonFlowConstants.SECTION_EVOLUTION, "Evolution", lesson.get("evolution_content"), 5);
        addSection(sections, LessonFlowConstants.SECTION_COMPARISON, "Comparison", lesson.get("comparison_content"), 6);
        return sections;
    }

    private LessonProgressResponse completeLessonSectionInternal(
        UUID userId,
        UUID lessonId,
        String sectionId,
        List<Map<String, Object>> sections,
        LessonProgressState state,
        String token
    ) {
        if (sections.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lesson has no readable sections");
        }
        String normalizedSectionId = sectionId == null ? null : sectionId.trim();
        int targetIndex = sectionIndexOf(sections, normalizedSectionId);
        if (targetIndex < 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lesson section not found");
        }
        int completed = state.completedSections();
        boolean hasQuiz = lessonQuizService.hasActiveLessonQuiz(lessonId);
        if (targetIndex < completed) {
            LessonQuizService.ProgressMetadata metadata = lessonQuizService.getProgressMetadata(
                userId,
                lessonId,
                sections,
                state.completedSections(),
                state.isEnrolled(),
                token
            );
            return buildProgressResponse(state, sections, metadata);
        }
        if (targetIndex > completed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete earlier sections first");
        }

        int totalSections = sections.size();
        int totalStops = totalSections + (hasQuiz ? 1 : 0);
        int newCompleted = Math.min(totalSections, completed + 1);
        int newProgress = toProgressPercentage(newCompleted, totalStops);
        String currentSection = sectionIdAt(sections, newCompleted - 1);
        String status = (!hasQuiz && newCompleted >= totalSections) ? "completed" : "in_progress";
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startedAt = state.startedAt() == null ? now : state.startedAt();

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("progress_percentage", newProgress);
        patch.put("status", status);
        patch.put("current_section", currentSection);
        patch.put("started_at", startedAt);
        patch.put("last_accessed_at", now);
        patch.put("completed_at", "completed".equals(status) ? now : null);

        String rowId = state.progressRowId();
        if (rowId != null) {
            patchProgressRowById(rowId, patch, token);
        } else {
            Map<String, Object> insert = new LinkedHashMap<>(patch);
            insert.put("user_id", userId);
            insert.put("lesson_id", lessonId);
            try {
                List<Map<String, Object>> created = supabaseRestClient.postList(
                    "user_lesson_progress",
                    insert,
                    token,
                    MAP_LIST
                );
                if (!created.isEmpty() && created.get(0).get("id") != null) {
                    rowId = created.get(0).get("id").toString();
                }
            } catch (ResponseStatusException ex) {
                if (!isUniqueViolation(ex)) {
                    throw ex;
                }
                LessonProgressState refreshed = loadLessonProgressState(userId, lessonId, sections, token);
                rowId = refreshed.progressRowId();
                if (rowId != null) {
                    patchProgressRowById(rowId, patch, token);
                }
            }
        }

        LessonProgressState updatedState = new LessonProgressState(
            rowId,
            newProgress,
            status,
            currentSection,
            newCompleted,
            true,
            startedAt
        );
        LessonQuizService.ProgressMetadata metadata = lessonQuizService.getProgressMetadata(
            userId,
            lessonId,
            sections,
            updatedState.completedSections(),
            updatedState.isEnrolled(),
            token
        );
        return buildProgressResponse(updatedState, sections, metadata);
    }

    private LessonProgressState loadLessonProgressState(
        UUID userId,
        UUID lessonId,
        List<Map<String, Object>> sections,
        String token
    ) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(
                Map.of(
                    "select",
                    "id,status,progress_percentage,current_section,started_at,last_accessed_at,created_at",
                    "user_id",
                    "eq." + userId,
                    "lesson_id",
                    "eq." + lessonId,
                    "order",
                    "progress_percentage.desc,last_accessed_at.desc,created_at.asc",
                    "limit",
                    "1"
                )
            ),
            token,
            MAP_LIST
        );
        if (rows.isEmpty()) {
            return new LessonProgressState(null, 0, "not_started", null, 0, false, null);
        }
        Map<String, Object> row = rows.get(0);
        String progressRowId = row.get("id") == null ? null : row.get("id").toString();
        Integer parsedProgress = parseInteger(row.get("progress_percentage"));
        int progress = parsedProgress == null ? 0 : parsedProgress;
        int clampedProgress = Math.max(0, Math.min(progress, 100));
        String rawCurrentSection = stringValue(row.get("current_section"));
        int completedSections = computeCompletedSections(clampedProgress, rawCurrentSection, sections);
        String normalizedCurrentSection = completedSections > 0 ? sectionIdAt(sections, completedSections - 1) : null;
        int totalStops = sections.size() + (lessonQuizService.hasActiveLessonQuiz(lessonId) ? 1 : 0);
        int canonicalProgress = sections.isEmpty()
            ? clampedProgress
            : Math.max(clampedProgress, toProgressPercentage(completedSections, totalStops));
        String storedStatus = normalizeStoredStatus(stringValue(row.get("status")));
        String normalizedStatus = resolveProgressStatus(storedStatus, canonicalProgress, completedSections, sections.size(), false);
        OffsetDateTime startedAt = parseOffsetDateTime(row.get("started_at"));
        return new LessonProgressState(
            progressRowId,
            canonicalProgress,
            normalizedStatus,
            normalizedCurrentSection,
            completedSections,
            true,
            startedAt
        );
    }

    private LessonProgressResponse buildProgressResponse(
        LessonProgressState state,
        List<Map<String, Object>> sections,
        LessonQuizService.ProgressMetadata metadata
    ) {
        int totalSections = sections.size();
        int totalStops = Math.max(0, metadata.totalStops());
        int completedSections = Math.max(0, Math.min(state.completedSections(), totalSections));
        int completedStops = Math.max(0, Math.min(metadata.completedStops(), totalStops));
        int progress = totalStops == 0 ? 0 : toProgressPercentage(completedStops, totalStops);
        String currentSection = completedSections > 0 ? sectionIdAt(sections, completedSections - 1) : null;
        String nextSection = completedSections < totalSections ? sectionIdAt(sections, completedSections) : null;
        String status = resolveProgressStatus(
            state.status(),
            progress,
            completedSections,
            totalSections,
            LessonFlowConstants.QUIZ_STATUS_PASSED.equals(metadata.quizStatus())
        );

        return new LessonProgressResponse(
            status,
            progress,
            currentSection,
            completedSections,
            totalSections,
            nextSection,
            state.isEnrolled(),
            totalStops,
            completedStops,
            metadata.currentStopId(),
            metadata.remainingStops(),
            metadata.quizStatus(),
            metadata.heartsRemaining(),
            metadata.heartsRefillAt(),
            metadata.nextStopType()
        );
    }

    private int computeCompletedSections(int progressPercentage, String currentSection, List<Map<String, Object>> sections) {
        int totalSections = sections.size();
        if (totalSections == 0) {
            return 0;
        }
        int clampedProgress = Math.max(0, Math.min(progressPercentage, 100));
        int byProgress = (int) Math.floor((clampedProgress / 100.0) * totalSections);
        int byCurrent = 0;
        int currentIndex = sectionIndexOf(sections, currentSection);
        if (currentIndex >= 0) {
            byCurrent = currentIndex + 1;
        }
        return Math.max(0, Math.min(totalSections, Math.max(byProgress, byCurrent)));
    }

    private int toProgressPercentage(int completedSections, int totalSections) {
        if (totalSections <= 0 || completedSections <= 0) {
            return 0;
        }
        if (completedSections >= totalSections) {
            return 100;
        }
        return (int) Math.round((completedSections * 100.0) / totalSections);
    }

    private int sectionIndexOf(List<Map<String, Object>> sections, String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < sections.size(); i++) {
            String candidate = stringValue(sections.get(i).get("id"));
            if (sectionId.equals(candidate)) {
                return i;
            }
        }
        return -1;
    }

    private String sectionIdAt(List<Map<String, Object>> sections, int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return stringValue(sections.get(index).get("id"));
    }

    private String normalizeStoredStatus(String status) {
        if (status == null) {
            return "not_started";
        }
        String normalized = status.trim().toLowerCase();
        return Set.of("not_started", "in_progress", "completed").contains(normalized) ? normalized : "not_started";
    }

    private String resolveProgressStatus(String status, int progress, int completed, int totalSections, boolean quizPassed) {
        if (quizPassed) {
            return "completed";
        }
        if ("completed".equals(status) && progress >= 100) {
            return "completed";
        }
        if (progress > 0 || completed > 0 || "in_progress".equals(status) || "completed".equals(status)) {
            return "in_progress";
        }
        return "not_started";
    }

    private void patchProgressRowById(String progressRowId, Map<String, Object> patch, String token) {
        if (progressRowId == null || patch == null || patch.isEmpty()) {
            return;
        }
        supabaseRestClient.patchList(
            "user_lesson_progress",
            buildQuery(Map.of("id", "eq." + progressRowId)),
            patch,
            token,
            MAP_LIST
        );
    }

    private boolean isUniqueViolation(ResponseStatusException ex) {
        StringBuilder normalizedBuilder = new StringBuilder();
        if (ex.getReason() != null) {
            normalizedBuilder.append(ex.getReason().toLowerCase());
        }
        if (ex.getCause() instanceof RestClientResponseException responseException) {
            String body = responseException.getResponseBodyAsString();
            if (body != null) {
                normalizedBuilder.append(" ").append(body.toLowerCase());
            }
        }
        String normalized = normalizedBuilder.toString();
        return normalized.contains("duplicate key")
            || normalized.contains("already exists")
            || normalized.contains("unique constraint")
            || normalized.contains("on conflict");
    }

    private OffsetDateTime parseOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
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

    private void applyQueryFilter(String query, LinkedHashMap<String, String> params) {
        if (query == null || query.isBlank()) {
            return;
        }
        String safeQuery = escapeQuery(query.trim());
        params.put(
            "or",
            String.format(
                "(title.ilike.*%s*,description.ilike.*%s*,summary.ilike.*%s*)",
                safeQuery,
                safeQuery,
                safeQuery
            )
        );
    }

    private void applyDifficultyFilter(String difficulty, LinkedHashMap<String, String> params) {
        String normalized = difficulty == null ? "all" : difficulty.trim().toLowerCase();
        switch (normalized) {
            case "beginner", "1" -> params.put("difficulty_level", "eq.1");
            case "intermediate", "2" -> params.put("difficulty_level", "eq.2");
            case "advanced", "3" -> params.put("difficulty_level", "eq.3");
            case "all", "" -> {
                return;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid difficulty filter");
        }
    }

    private void applyDurationFilter(String duration, LinkedHashMap<String, String> params) {
        String normalized = duration == null ? "all" : duration.trim().toLowerCase();
        switch (normalized) {
            case "short" -> params.put("estimated_minutes", "lte.10");
            case "medium" -> params.put("and", "(estimated_minutes.gte.11,estimated_minutes.lte.20)");
            case "long" -> params.put("estimated_minutes", "gte.21");
            case "all", "" -> {
                return;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid duration filter");
        }
    }

    private String resolveSort(String sort) {
        String normalized = sort == null ? "popular" : sort.trim().toLowerCase();
        return switch (normalized) {
            case "popular", "" -> "completion_count.desc,created_at.desc";
            case "newest" -> "created_at.desc";
            case "shortest" -> "estimated_minutes.asc";
            case "highest_xp" -> "xp_reward.desc";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort option");
        };
    }

    private int normalizeLessonFeedPage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_LESSON_FEED_PAGE;
        }
        return page;
    }

    private int normalizeLessonFeedPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_LESSON_FEED_PAGE_SIZE;
        }
        return Math.min(MAX_LESSON_FEED_PAGE_SIZE, pageSize);
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
            String type = normalizeQuestionType(q.get("question_type"));
            if (!SUPPORTED_QUIZ_TYPES.contains(type)) {
                errors.add(prefix + "question_type");
                continue;
            }
            if (stringValue(q.get("question_text")) == null) {
                errors.add(prefix + "question_text");
            }
            if (stringValue(q.get("explanation")) == null) {
                errors.add(prefix + "explanation");
            }
            Integer points = parseInteger(q.get("points"));
            if (points == null || points < 1 || points > 100) {
                errors.add(prefix + "points");
            }

            Map<String, Object> options = extractOptions(q.get("options"));
            String correct = stringValue(q.get("correct_answer"));
            switch (type) {
                case "multiple_choice" -> {
                    Map<String, Object> choices = extractOptions(options.get("choices"));
                    if (choices.isEmpty()) {
                        choices = options;
                    }
                    if (choices.size() < 2) {
                        errors.add(prefix + "options.choices");
                    }
                    if (correct == null || !choices.containsKey(correct)) {
                        errors.add(prefix + "correct_answer");
                    }
                }
                case "true_false" -> {
                    if (correct == null || !(correct.equalsIgnoreCase("true") || correct.equalsIgnoreCase("false"))) {
                        errors.add(prefix + "correct_answer");
                    }
                }
                case "cloze", "conversation", "match_pairs" -> {
                    if (options.isEmpty()) {
                        errors.add(prefix + "options");
                    }
                    if (correct == null || !canParseJsonObject(correct)) {
                        errors.add(prefix + "correct_answer");
                    }
                }
                case "word_bank" -> {
                    Object tokensRaw = options.get("tokens");
                    if (!(tokensRaw instanceof List<?> tokens) || tokens.size() < 2) {
                        errors.add(prefix + "options.tokens");
                    }
                    if (correct == null || (!canParseJsonObject(correct) && !canParseJsonArray(correct))) {
                        errors.add(prefix + "correct_answer");
                    }
                }
                case "short_text" -> {
                    if (correct == null) {
                        errors.add(prefix + "correct_answer");
                    } else if (correct.startsWith("{")) {
                        if (!canParseJsonObject(correct)) {
                            errors.add(prefix + "correct_answer");
                        }
                    } else if (correct.startsWith("[")) {
                        if (!canParseJsonArray(correct)) {
                            errors.add(prefix + "correct_answer");
                        }
                    } else if (correct.isBlank()) {
                        errors.add(prefix + "correct_answer");
                    }
                }
                default -> errors.add(prefix + "question_type");
            }
        }
        return errors;
    }

    private String normalizeQuestionType(Object rawValue) {
        String value = stringValue(rawValue);
        return value == null ? "multiple_choice" : value.toLowerCase();
    }

    private boolean canParseJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> parsed = objectMapper.readValue(value, Map.class);
            return parsed != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean canParseJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            List<?> parsed = objectMapper.readValue(value, List.class);
            return parsed != null;
        } catch (Exception ex) {
            return false;
        }
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
            String questionType = normalizeQuestionType(rawMap.get("question_type"));
            String rawCorrectAnswer = stringValue(rawMap.get("correct_answer"));
            Map<String, Object> questionMap = new LinkedHashMap<>();
            questionMap.put("quiz_id", quizId);
            questionMap.put("question_text", stringValue(rawMap.get("question_text")));
            questionMap.put("question_type", questionType);
            questionMap.put("options", extractOptions(rawMap.get("options")));
            questionMap.put("correct_answer", rawCorrectAnswer);
            questionMap.put("answer_key", buildAnswerKey(questionType, rawCorrectAnswer));
            questionMap.put("ui_template", questionType);
            questionMap.put("template_version", 1);
            questionMap.put("explanation", stringValue(rawMap.get("explanation")));
            questionMap.put("points", parseInteger(rawMap.get("points")));
            questionMap.put("order_index", parseInteger(rawMap.get("order_index")) != null ? parseInteger(rawMap.get("order_index")) : i);
            supabaseAdminRestClient.postList("quiz_questions", questionMap, MAP_LIST);
        }

        return quiz;
    }

    private Map<String, Object> questionTypeMeta(String type, String label, String optionsExample, String answerExample) {
        return Map.of(
            "type", type,
            "label", label,
            "optionsExample", optionsExample,
            "answerExample", answerExample
        );
    }

    private Object buildAnswerKey(String questionType, String correctAnswerRaw) {
        if (correctAnswerRaw == null) {
            return new LinkedHashMap<>();
        }
        String normalizedType = questionType == null ? "multiple_choice" : questionType.trim().toLowerCase();
        return switch (normalizedType) {
            case "multiple_choice" -> Map.of("correctChoiceId", correctAnswerRaw.toUpperCase());
            case "true_false" -> Map.of("correctBoolean", "true".equalsIgnoreCase(correctAnswerRaw));
            case "cloze", "conversation", "match_pairs", "word_bank", "short_text" -> parseJsonOrTextAnswerKey(correctAnswerRaw);
            default -> new LinkedHashMap<>();
        };
    }

    private Object parseJsonOrTextAnswerKey(String rawValue) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception ignored) {
            return Map.of("accepted", List.of(trimmed));
        }
    }

    private void persistLessonStepPatch(UUID lessonId, AdminLessonWizardStep step, Map<String, Object> lessonPatch) {
        if (lessonPatch == null || lessonPatch.isEmpty()) {
            return;
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        if (step == AdminLessonWizardStep.BASICS) {
            copyIfPresent(lessonPatch, patch, "title");
            copyIfPresent(lessonPatch, patch, "summary");
            copyIfPresent(lessonPatch, patch, "description");
            copyIfPresent(lessonPatch, patch, "estimated_minutes");
            copyIfPresent(lessonPatch, patch, "xp_reward");
            copyIfPresent(lessonPatch, patch, "badge_name");
            copyIfPresent(lessonPatch, patch, "difficulty_level");
        } else if (step == AdminLessonWizardStep.CONTENT) {
            copyIfPresent(lessonPatch, patch, "learning_objectives");
            copyIfPresent(lessonPatch, patch, "origin_content");
            copyIfPresent(lessonPatch, patch, "definition_content");
            copyIfPresent(lessonPatch, patch, "usage_examples");
            copyIfPresent(lessonPatch, patch, "lore_content");
            copyIfPresent(lessonPatch, patch, "evolution_content");
            copyIfPresent(lessonPatch, patch, "comparison_content");
        } else if (step == AdminLessonWizardStep.REVIEW_PUBLISH) {
            applyEditableLessonFields(lessonPatch, patch);
        }
        if (patch.isEmpty()) {
            return;
        }
        patch.put("updated_at", OffsetDateTime.now());
        supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            patch,
            MAP_LIST
        );
    }

    private void applyEditableLessonFields(Map<String, Object> source, Map<String, Object> patch) {
        copyIfPresent(source, patch, "title");
        copyIfPresent(source, patch, "description");
        copyIfPresent(source, patch, "summary");
        copyIfPresent(source, patch, "learning_objectives");
        copyIfPresent(source, patch, "estimated_minutes");
        copyIfPresent(source, patch, "xp_reward");
        copyIfPresent(source, patch, "badge_name");
        copyIfPresent(source, patch, "difficulty_level");
        copyIfPresent(source, patch, "origin_content");
        copyIfPresent(source, patch, "definition_content");
        copyIfPresent(source, patch, "usage_examples");
        copyIfPresent(source, patch, "lore_content");
        copyIfPresent(source, patch, "evolution_content");
        copyIfPresent(source, patch, "comparison_content");
    }

    private List<AdminValidationError> validateStepDraft(
        AdminLessonWizardStep step,
        Map<String, Object> mergedLesson,
        List<Map<String, Object>> questions
    ) {
        if (stringValue(mergedLesson.get("title")) == null) {
            return List.of(
                new AdminValidationError(
                    step.key(),
                    "title",
                    "Lesson title is required before saving",
                    null
                )
            );
        }
        return List.of();
    }

    private List<AdminValidationError> validateBasicsDraft(Map<String, Object> lesson) {
        List<AdminValidationError> errors = new ArrayList<>();
        if (stringValue(lesson.get("title")) == null) {
            errors.add(new AdminValidationError("basics", "title", "Lesson title is required", null));
        }
        Integer difficulty = parseInteger(lesson.get("difficulty_level"));
        if (difficulty != null && (difficulty < 1 || difficulty > 3)) {
            errors.add(new AdminValidationError("basics", "difficulty_level", "Difficulty level must be between 1 and 3", null));
        }
        return errors;
    }

    private List<AdminValidationError> validateContentDraft(Map<String, Object> lesson) {
        List<AdminValidationError> errors = new ArrayList<>();
        Object objectives = lesson.get("learning_objectives");
        if (objectives != null && !(objectives instanceof List<?>)) {
            errors.add(new AdminValidationError("content", "learning_objectives", "Learning objectives must be a list", null));
        }
        Object examples = lesson.get("usage_examples");
        if (examples != null && !(examples instanceof List<?>)) {
            errors.add(new AdminValidationError("content", "usage_examples", "Usage examples must be a list", null));
        }
        return errors;
    }

    private List<AdminValidationError> validateQuizSetupDraft(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of(new AdminValidationError("quiz_setup", "questions", "At least one question is required", null));
        }
        List<AdminValidationError> errors = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            String type = normalizeQuestionType(questions.get(i).get("question_type"));
            if (!SUPPORTED_QUIZ_TYPES.contains(type)) {
                errors.add(new AdminValidationError("quiz_setup", "questions[" + i + "].question_type", "Unsupported question type", i));
            }
        }
        return errors;
    }

    private List<AdminValidationError> validateQuizBuilderDraft(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of(new AdminValidationError("quiz_builder", "questions", "At least one question is required", null));
        }
        List<AdminValidationError> errors = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            String prefix = "questions[" + i + "].";
            String type = normalizeQuestionType(question.get("question_type"));
            if (!SUPPORTED_QUIZ_TYPES.contains(type)) {
                errors.add(new AdminValidationError("quiz_builder", prefix + "question_type", "Unsupported question type", i));
                continue;
            }

            Object optionsRaw = question.get("options");
            if (optionsRaw != null && !(optionsRaw instanceof Map<?, ?>)) {
                errors.add(new AdminValidationError("quiz_builder", prefix + "options", "Options must be an object", i));
                continue;
            }
            Map<String, Object> options = extractOptions(optionsRaw);

            if ("multiple_choice".equals(type)) {
                Map<String, Object> choices = extractOptions(options.get("choices"));
                if (choices.isEmpty() && !options.isEmpty()) {
                    choices = options;
                }
                if (question.get("options") != null && !choices.isEmpty() && choices.size() < 2) {
                    errors.add(new AdminValidationError("quiz_builder", prefix + "options.choices", "Multiple choice requires at least two options", i));
                }
            } else if ("word_bank".equals(type)) {
                Object tokens = options.get("tokens");
                if (tokens != null && !(tokens instanceof List<?>)) {
                    errors.add(new AdminValidationError("quiz_builder", prefix + "options.tokens", "Word bank tokens must be a list", i));
                }
            } else if ("conversation".equals(type)) {
                Object turns = options.get("turns");
                if (turns != null && !(turns instanceof List<?>)) {
                    errors.add(new AdminValidationError("quiz_builder", prefix + "options.turns", "Conversation turns must be a list", i));
                }
            } else if ("match_pairs".equals(type)) {
                Object left = options.get("left");
                Object right = options.get("right");
                if (left != null && !(left instanceof List<?>)) {
                    errors.add(new AdminValidationError("quiz_builder", prefix + "options.left", "Left match list must be an array", i));
                }
                if (right != null && !(right instanceof List<?>)) {
                    errors.add(new AdminValidationError("quiz_builder", prefix + "options.right", "Right match list must be an array", i));
                }
            }
        }
        return errors;
    }

    private List<AdminValidationError> validatePublishFull(Map<String, Object> lesson, List<Map<String, Object>> questions) {
        List<AdminValidationError> errors = new ArrayList<>();
        List<String> lessonFields = collectLessonPublishErrors(lesson);
        for (String field : lessonFields) {
            String step = isBasicsField(field) ? "basics" : "content";
            errors.add(new AdminValidationError(step, field, "Missing or invalid value", null));
        }
        List<String> questionFields = collectQuestionErrors(questions, true);
        for (String field : questionFields) {
            String step = "quiz_builder";
            if ("questions".equals(field) || field.endsWith(".question_type")) {
                step = "quiz_setup";
            }
            errors.add(new AdminValidationError(step, field, "Missing or invalid value", parseQuestionIndex(field)));
        }
        return errors;
    }

    private String firstInvalidStep(List<AdminValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        List<String> order = List.of("basics", "content", "quiz_setup", "quiz_builder", "review_publish");
        for (String step : order) {
            boolean hasStepError = errors.stream().anyMatch(error -> step.equals(error.step()));
            if (hasStepError) {
                return step;
            }
        }
        return "review_publish";
    }

    private boolean isBasicsField(String field) {
        return Set.of(
            "title",
            "summary",
            "description",
            "estimated_minutes",
            "xp_reward",
            "difficulty_level",
            "badge_name"
        ).contains(field);
    }

    private Integer parseQuestionIndex(String fieldPath) {
        if (fieldPath == null) {
            return null;
        }
        int start = fieldPath.indexOf('[');
        int end = fieldPath.indexOf(']');
        if (start < 0 || end <= start + 1) {
            return null;
        }
        try {
            return Integer.parseInt(fieldPath.substring(start + 1, end));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, Boolean> computeWizardCompleteness(Map<String, Object> lesson, List<Map<String, Object>> questions) {
        boolean basics = validateBasicsDraft(lesson).isEmpty();
        boolean content = collectLessonPublishErrors(lesson).stream().noneMatch(field ->
            Set.of(
                "origin_content",
                "definition_content",
                "usage_examples",
                "lore_content",
                "evolution_content",
                "comparison_content",
                "learning_objectives"
            ).contains(field)
        );
        boolean quizSetup = questions != null && !questions.isEmpty();
        boolean quizBuilder = collectQuestionErrors(questions, true).isEmpty();
        boolean review = basics && content && quizSetup && quizBuilder;

        Map<String, Boolean> completeness = new LinkedHashMap<>();
        completeness.put("basics", basics);
        completeness.put("content", content);
        completeness.put("quiz_setup", quizSetup);
        completeness.put("quiz_builder", quizBuilder);
        completeness.put("review_publish", review);
        return completeness;
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

    private record LessonProgressState(
        String progressRowId,
        int progressPercentage,
        String status,
        String currentSection,
        int completedSections,
        boolean isEnrolled,
        OffsetDateTime startedAt
    ) {}

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
