package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.LessonHeartsStatusResponse;
import com.rotiprata.api.dto.LessonQuizAnswerRequest;
import com.rotiprata.api.dto.LessonQuizAnswerResponse;
import com.rotiprata.api.dto.LessonQuizQuestionResponse;
import com.rotiprata.api.dto.LessonQuizStateResponse;
import com.rotiprata.application.quiz.LessonQuizGradeResult;
import com.rotiprata.application.quiz.LessonQuizGraderRegistry;
import com.rotiprata.application.quiz.LessonQuizQuestionGrader;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LessonQuizService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final String ATTEMPT_META_WRONG_QUESTION_IDS = "__wrong_question_ids";
    private static final String ATTEMPT_META_QUESTION_IDS = "__question_ids";
    private static final Logger log = LoggerFactory.getLogger(LessonQuizService.class);

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final LessonQuizGraderRegistry graderRegistry;

    public LessonQuizService(
        SupabaseRestClient supabaseRestClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        LessonQuizGraderRegistry graderRegistry
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.graderRegistry = graderRegistry;
    }

    public ProgressMetadata getProgressMetadata(
        UUID userId,
        UUID lessonId,
        List<Map<String, Object>> sections,
        int completedSections,
        boolean isEnrolled,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        HeartsState hearts = ensureHeartsState(userId, token);
        Map<String, Object> quiz = findActiveLessonQuiz(lessonId);
        boolean hasQuiz = quiz != null;
        int boundedCompletedSections = Math.max(0, Math.min(completedSections, sections.size()));
        int totalStops = sections.size() + (hasQuiz ? 1 : 0);
        int completedStops = boundedCompletedSections;
        String currentStopId = boundedCompletedSections > 0 ? sectionIdAt(sections, boundedCompletedSections - 1) : null;
        String quizStatus = LessonFlowConstants.QUIZ_STATUS_LOCKED;
        String nextStopType = sections.isEmpty()
            ? (hasQuiz ? LessonFlowConstants.NEXT_STOP_QUIZ : LessonFlowConstants.NEXT_STOP_DONE)
            : LessonFlowConstants.NEXT_STOP_SECTION;

        if (!isEnrolled) {
            int remainingStops = Math.max(0, totalStops - completedStops);
            return new ProgressMetadata(
                totalStops,
                completedStops,
                currentStopId,
                remainingStops,
                quizStatus,
                hearts.heartsRemaining(),
                hearts.refillAt(),
                nextStopType
            );
        }

        if (boundedCompletedSections < sections.size()) {
            int remainingStops = Math.max(0, totalStops - completedStops);
            return new ProgressMetadata(
                totalStops,
                completedStops,
                currentStopId,
                remainingStops,
                quizStatus,
                hearts.heartsRemaining(),
                hearts.refillAt(),
                LessonFlowConstants.NEXT_STOP_SECTION
            );
        }

        if (!hasQuiz) {
            completedStops = totalStops;
            currentStopId = sections.isEmpty() ? null : sectionIdAt(sections, sections.size() - 1);
            return new ProgressMetadata(
                totalStops,
                completedStops,
                currentStopId,
                0,
                LessonFlowConstants.QUIZ_STATUS_LOCKED,
                hearts.heartsRemaining(),
                hearts.refillAt(),
                LessonFlowConstants.NEXT_STOP_DONE
            );
        }

        Map<String, Object> latestAttempt = findLatestAttempt(userId, lessonId, token);
        String latestStatus = latestAttempt == null ? null : stringValue(latestAttempt.get("status"));
        if ("passed".equals(latestStatus)) {
            return new ProgressMetadata(
                totalStops,
                totalStops,
                LessonFlowConstants.STOP_QUIZ,
                0,
                LessonFlowConstants.QUIZ_STATUS_PASSED,
                hearts.heartsRemaining(),
                hearts.refillAt(),
                LessonFlowConstants.NEXT_STOP_DONE
            );
        }

        if (hearts.heartsRemaining() <= 0) {
            return new ProgressMetadata(
                totalStops,
                sections.size(),
                LessonFlowConstants.STOP_QUIZ,
                1,
                LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS,
                hearts.heartsRemaining(),
                hearts.refillAt(),
                LessonFlowConstants.NEXT_STOP_QUIZ
            );
        }

        String resolvedQuizStatus = LessonFlowConstants.QUIZ_STATUS_AVAILABLE;
        if ("in_progress".equals(latestStatus) || "paused_no_hearts".equals(latestStatus)) {
            resolvedQuizStatus = LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS;
        }
        return new ProgressMetadata(
            totalStops,
            sections.size(),
            LessonFlowConstants.STOP_QUIZ,
            1,
            resolvedQuizStatus,
            hearts.heartsRemaining(),
            hearts.refillAt(),
            LessonFlowConstants.NEXT_STOP_QUIZ
        );
    }

    public LessonQuizStateResponse getQuizState(UUID userId, UUID lessonId, String accessToken) {
        String token = requireAccessToken(accessToken);
        QuizContext context = loadQuizContext(userId, lessonId, token);
        HeartsState hearts = ensureHeartsState(userId, token);
        Map<String, Object> activeAttempt = findActiveAttempt(userId, lessonId, token);
        Map<String, Object> latestAttempt = activeAttempt != null ? activeAttempt : findLatestAttempt(userId, lessonId, token);
        List<Map<String, Object>> orderedQuestions = latestAttempt == null
            ? context.questions()
            : resolveQuestionsForAttempt(context.questions(), latestAttempt);

        if (latestAttempt != null && "passed".equals(stringValue(latestAttempt.get("status")))) {
            return buildStateResponse(
                latestAttempt,
                orderedQuestions,
                hearts,
                LessonFlowConstants.QUIZ_STATUS_PASSED,
                false,
                false
            );
        }

        if (activeAttempt == null) {
            if (latestAttempt != null && "failed".equals(stringValue(latestAttempt.get("status")))) {
                List<String> wrongQuestionIds = wrongQuestionIdsFromAttempt(latestAttempt);
                if (hearts.heartsRemaining() > 0 && !wrongQuestionIds.isEmpty()) {
                    List<Map<String, Object>> retryQuestions = filterQuestionsByIds(context.questions(), wrongQuestionIds);
                    if (!retryQuestions.isEmpty()) {
                        int retryMaxScore = computeMaxScore(retryQuestions);
                        activeAttempt = createAttempt(
                            userId,
                            lessonId,
                            stringValue(context.quiz().get("id")),
                            retryMaxScore,
                            wrongQuestionIds,
                            token
                        );
                        orderedQuestions = resolveQuestionsForAttempt(context.questions(), activeAttempt);
                        return buildStateResponse(
                            activeAttempt,
                            orderedQuestions,
                            hearts,
                            LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS,
                            true,
                            false
                        );
                    }
                }
                String status = hearts.heartsRemaining() <= 0
                    ? LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS
                    : LessonFlowConstants.QUIZ_STATUS_FAILED;
                return buildStateResponse(
                    latestAttempt,
                    orderedQuestions,
                    hearts,
                    status,
                    false,
                    hearts.heartsRemaining() > 0
                );
            }
            if (hearts.heartsRemaining() <= 0) {
                return new LessonQuizStateResponse(
                    null,
                    LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS,
                    0,
                    orderedQuestions.size(),
                    0,
                    0,
                    context.maxScore(),
                    null,
                    new LessonHeartsStatusResponse(hearts.heartsRemaining(), hearts.refillAt()),
                    false,
                    false,
                    List.of()
                );
            }
            activeAttempt = createAttempt(
                userId,
                lessonId,
                stringValue(context.quiz().get("id")),
                context.maxScore(),
                null,
                token
            );
            orderedQuestions = resolveQuestionsForAttempt(context.questions(), activeAttempt);
        } else {
            String status = stringValue(activeAttempt.get("status"));
            if ("paused_no_hearts".equals(status) && hearts.heartsRemaining() > 0) {
                activeAttempt = patchAttempt(
                    activeAttempt,
                    Map.of(
                        "status", "in_progress",
                        "updated_at", OffsetDateTime.now()
                    ),
                    token
                );
            } else if ("in_progress".equals(status) && hearts.heartsRemaining() <= 0) {
                activeAttempt = patchAttempt(
                    activeAttempt,
                    Map.of(
                        "status", "paused_no_hearts",
                        "updated_at", OffsetDateTime.now()
                    ),
                    token
                );
            }
            orderedQuestions = resolveQuestionsForAttempt(context.questions(), activeAttempt);
        }

        boolean blocked = hearts.heartsRemaining() <= 0;
        String status = blocked
            ? LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS
            : LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS;
        return buildStateResponse(activeAttempt, orderedQuestions, hearts, status, !blocked, false);
    }

    public boolean hasActiveLessonQuiz(UUID lessonId) {
        return findActiveLessonQuiz(lessonId) != null;
    }

    public LessonHeartsStatusResponse getHeartsStatus(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        HeartsState hearts = ensureHeartsState(userId, token);
        return new LessonHeartsStatusResponse(hearts.heartsRemaining(), hearts.refillAt());
    }

    public LessonQuizAnswerResponse answerQuestion(
        UUID userId,
        UUID lessonId,
        LessonQuizAnswerRequest request,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        QuizContext context = loadQuizContext(userId, lessonId, token);
        HeartsState hearts = ensureHeartsState(userId, token);
        Map<String, Object> attempt = findAttemptById(userId, lessonId, request.attemptId(), token);
        if (attempt == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz attempt not found");
        }
        List<Map<String, Object>> orderedQuestions = resolveQuestionsForAttempt(context.questions(), attempt);
        String currentStatus = stringValue(attempt.get("status"));
        if ("passed".equals(currentStatus) || "failed".equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz attempt already completed");
        }
        if (hearts.heartsRemaining() <= 0) {
            patchAttempt(
                attempt,
                Map.of(
                    "status", "paused_no_hearts",
                    "updated_at", OffsetDateTime.now()
                ),
                token
            );
            return new LessonQuizAnswerResponse(
                request.attemptId(),
                LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS,
                false,
                null,
                parseInteger(attempt.get("current_question_index")),
                orderedQuestions.size(),
                parseInteger(attempt.get("correct_count")),
                parseInteger(attempt.get("earned_score")),
                parseInteger(attempt.get("max_score")),
                false,
                false,
                true,
                null,
                new LessonHeartsStatusResponse(hearts.heartsRemaining(), hearts.refillAt()),
                wrongQuestionIdsFromAttempt(attempt)
            );
        }

        int index = parseInteger(attempt.get("current_question_index")) == null ? 0 : parseInteger(attempt.get("current_question_index"));
        if (index < 0 || index >= orderedQuestions.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz attempt is already at the end");
        }

        Map<String, Object> question = orderedQuestions.get(index);
        String expectedQuestionId = stringValue(question.get("id"));
        if (!expectedQuestionId.equals(request.questionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer must be submitted for the current question");
        }

        LessonQuizQuestionGrader grader = graderRegistry.require(questionTypeOf(question));
        LessonQuizGradeResult gradeResult = grader.grade(question, request.response());
        Map<String, Object> answers = normalizeAnswers(attempt.get("answers"));
        if (answers.containsKey(expectedQuestionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Question already answered");
        }
        answers.put(expectedQuestionId, gradeResult.normalizedResponse());

        boolean correct = gradeResult.correct();
        if (!correct) {
            addWrongQuestionId(answers, expectedQuestionId);
        }
        List<String> wrongQuestionIds = wrongQuestionIdsFromAnswers(answers);
        int points = parseInteger(question.get("points")) == null ? 10 : parseInteger(question.get("points"));
        int correctCount = parseInteger(attempt.get("correct_count")) == null ? 0 : parseInteger(attempt.get("correct_count"));
        int earnedScore = parseInteger(attempt.get("earned_score")) == null ? 0 : parseInteger(attempt.get("earned_score"));
        int maxScore = parseInteger(attempt.get("max_score")) == null ? context.maxScore() : parseInteger(attempt.get("max_score"));
        if (correct) {
            correctCount += 1;
            earnedScore += points;
        }
        int projectedHearts = correct ? hearts.heartsRemaining() : Math.max(0, hearts.heartsRemaining() - 1);

        int nextIndex = index + 1;
        boolean quizCompleted = nextIndex >= orderedQuestions.size();
        boolean blockedByHearts = !quizCompleted && projectedHearts <= 0;
        boolean passed = false;
        String attemptStatus;
        if (quizCompleted) {
            passed = correctCount >= orderedQuestions.size();
            attemptStatus = passed ? "passed" : "failed";
        } else if (blockedByHearts) {
            attemptStatus = "paused_no_hearts";
        } else {
            attemptStatus = "in_progress";
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("answers", answers);
        patch.put("current_question_index", nextIndex);
        patch.put("correct_count", correctCount);
        patch.put("earned_score", earnedScore);
        patch.put("max_score", maxScore);
        patch.put("status", attemptStatus);
        patch.put("updated_at", now);
        patch.put("completed_at", quizCompleted ? now : null);
        Map<String, Object> patched = patchAttemptForAnswer(attempt, index, currentStatus, patch, token);
        if (patched == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz answer is stale. Refresh and try again.");
        }

        if (!correct) {
            hearts = consumeHeart(userId, hearts, token);
        }

        if (quizCompleted) {
            saveQuizResult(userId, context, earnedScore, maxScore, passed, stripAttemptMetadata(answers), token);
            if (passed) {
                completeLessonAndGrantRewards(userId, lessonId, context, token);
            }
        }

        LessonQuizQuestionResponse nextQuestion = null;
        if (!quizCompleted && !blockedByHearts) {
            nextQuestion = toQuestionResponse(orderedQuestions.get(nextIndex));
        }

        String responseStatus;
        if (passed) {
            responseStatus = LessonFlowConstants.QUIZ_STATUS_PASSED;
        } else if (quizCompleted) {
            responseStatus = LessonFlowConstants.QUIZ_STATUS_FAILED;
        } else if (blockedByHearts) {
            responseStatus = LessonFlowConstants.QUIZ_STATUS_BLOCKED_HEARTS;
        } else {
            responseStatus = LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS;
        }

        return new LessonQuizAnswerResponse(
            request.attemptId(),
            responseStatus,
            correct,
            stringValue(question.get("explanation")),
            nextIndex,
            orderedQuestions.size(),
            correctCount,
            earnedScore,
            maxScore,
            passed,
            quizCompleted,
            blockedByHearts,
            nextQuestion,
            new LessonHeartsStatusResponse(hearts.heartsRemaining(), hearts.refillAt()),
            wrongQuestionIds
        );
    }

    public LessonQuizStateResponse restartQuiz(UUID userId, UUID lessonId, String mode, String accessToken) {
        String token = requireAccessToken(accessToken);
        QuizContext context = loadQuizContext(userId, lessonId, token);
        HeartsState hearts = ensureHeartsState(userId, token);
        if (hearts.heartsRemaining() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are out of hearts");
        }

        Map<String, Object> activeAttempt = findActiveAttempt(userId, lessonId, token);
        if (activeAttempt != null) {
            List<Map<String, Object>> orderedQuestions = resolveQuestionsForAttempt(context.questions(), activeAttempt);
            return buildStateResponse(
                activeAttempt,
                orderedQuestions,
                hearts,
                LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS,
                true,
                false
            );
        }

        Map<String, Object> latestAttempt = findLatestAttempt(userId, lessonId, token);
        if (latestAttempt != null && "passed".equals(stringValue(latestAttempt.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz already passed");
        }

        boolean wrongOnly = mode == null || mode.isBlank() || "wrong_only".equalsIgnoreCase(mode);
        List<String> questionIds = null;
        int maxScore = context.maxScore();
        if (wrongOnly && latestAttempt != null) {
            List<String> wrongQuestionIds = wrongQuestionIdsFromAttempt(latestAttempt);
            if (!wrongQuestionIds.isEmpty()) {
                List<Map<String, Object>> retryQuestions = filterQuestionsByIds(context.questions(), wrongQuestionIds);
                if (!retryQuestions.isEmpty()) {
                    questionIds = wrongQuestionIds;
                    maxScore = computeMaxScore(retryQuestions);
                }
            }
        }

        Map<String, Object> newAttempt = createAttempt(
            userId,
            lessonId,
            stringValue(context.quiz().get("id")),
            maxScore,
            questionIds,
            token
        );
        List<Map<String, Object>> orderedQuestions = resolveQuestionsForAttempt(context.questions(), newAttempt);
        return buildStateResponse(
            newAttempt,
            orderedQuestions,
            hearts,
            LessonFlowConstants.QUIZ_STATUS_IN_PROGRESS,
            true,
            false
        );
    }

    private QuizContext loadQuizContext(UUID userId, UUID lessonId, String token) {
        Map<String, Object> lesson = getLearnerLesson(lessonId, token);
        List<Map<String, Object>> sections = buildLessonSections(lesson);
        LessonProgressState progress = loadProgressState(userId, lessonId, sections, token);
        if (!progress.isEnrolled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enroll before starting the quiz");
        }
        if (progress.completedSections() < sections.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete all lesson sections before the quiz");
        }
        Map<String, Object> quiz = findActiveLessonQuiz(lessonId);
        if (quiz == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not available for this lesson");
        }
        List<Map<String, Object>> questions = getQuizQuestions(stringValue(quiz.get("id")));
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz questions not available");
        }
        for (Map<String, Object> question : questions) {
            graderRegistry.require(questionTypeOf(question));
        }
        int maxScore = questions.stream()
            .map(q -> parseInteger(q.get("points")))
            .mapToInt(points -> points == null ? 10 : points)
            .sum();
        return new QuizContext(lesson, sections, quiz, questions, maxScore);
    }

    private Map<String, Object> getLearnerLesson(UUID lessonId, String token) {
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
        return quizzes.isEmpty() ? null : quizzes.get(0);
    }

    private List<Map<String, Object>> getQuizQuestions(String quizId) {
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

    private HeartsState ensureHeartsState(UUID userId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_quiz_hearts",
            buildQuery(Map.of("select", "*", "user_id", "eq." + userId)),
            token,
            MAP_LIST
        );
        OffsetDateTime now = OffsetDateTime.now();
        if (rows.isEmpty()) {
            Map<String, Object> insert = new LinkedHashMap<>();
            insert.put("user_id", userId);
            insert.put("hearts_remaining", LessonFlowConstants.MAX_HEARTS);
            insert.put("refill_at", now.plusHours(24));
            insert.put("updated_at", now);
            List<Map<String, Object>> created = supabaseRestClient.postList("user_quiz_hearts", insert, token, MAP_LIST);
            if (created.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to initialize hearts");
            }
            Map<String, Object> row = created.get(0);
            return new HeartsState(
                parseInteger(row.get("hearts_remaining")) == null ? LessonFlowConstants.MAX_HEARTS : parseInteger(row.get("hearts_remaining")),
                parseOffsetDateTime(row.get("refill_at"))
            );
        }

        Map<String, Object> row = rows.get(0);
        int hearts = parseInteger(row.get("hearts_remaining")) == null ? LessonFlowConstants.MAX_HEARTS : parseInteger(row.get("hearts_remaining"));
        OffsetDateTime refillAt = parseOffsetDateTime(row.get("refill_at"));
        if (hearts < LessonFlowConstants.MAX_HEARTS && refillAt != null && !now.isBefore(refillAt)) {
            hearts = LessonFlowConstants.MAX_HEARTS;
            refillAt = now.plusHours(24);
            supabaseRestClient.patchList(
                "user_quiz_hearts",
                buildQuery(Map.of("user_id", "eq." + userId)),
                Map.of(
                    "hearts_remaining", hearts,
                    "refill_at", refillAt,
                    "updated_at", now
                ),
                token,
                MAP_LIST
            );
        }
        return new HeartsState(hearts, refillAt);
    }

    private HeartsState consumeHeart(UUID userId, HeartsState hearts, String token) {
        OffsetDateTime now = OffsetDateTime.now();
        int nextHearts = Math.max(0, hearts.heartsRemaining() - 1);
        OffsetDateTime refillAt = hearts.refillAt();
        if (nextHearts == 0) {
            refillAt = now.plusHours(24);
        }
        supabaseRestClient.patchList(
            "user_quiz_hearts",
            buildQuery(Map.of("user_id", "eq." + userId)),
            Map.of(
                "hearts_remaining", nextHearts,
                "refill_at", refillAt,
                "updated_at", now
            ),
            token,
            MAP_LIST
        );
        return new HeartsState(nextHearts, refillAt);
    }

    private Map<String, Object> createAttempt(
        UUID userId,
        UUID lessonId,
        String quizId,
        int maxScore,
        List<String> questionIds,
        String token
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("lesson_id", lessonId);
        insert.put("quiz_id", quizId);
        insert.put("status", "in_progress");
        insert.put("current_question_index", 0);
        Map<String, Object> seededAnswers = new LinkedHashMap<>();
        if (questionIds != null && !questionIds.isEmpty()) {
            seededAnswers.put(ATTEMPT_META_QUESTION_IDS, new ArrayList<>(questionIds));
        }
        insert.put("answers", seededAnswers);
        insert.put("correct_count", 0);
        insert.put("max_score", maxScore);
        insert.put("earned_score", 0);
        insert.put("started_at", now);
        insert.put("updated_at", now);
        insert.put("completed_at", null);
        try {
            List<Map<String, Object>> created = supabaseRestClient.postList("user_lesson_quiz_attempts", insert, token, MAP_LIST);
            if (created.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create quiz attempt");
            }
            return created.get(0);
        } catch (ResponseStatusException ex) {
            if (!isUniqueViolation(ex)) {
                throw ex;
            }
            Map<String, Object> active = findActiveAttempt(userId, lessonId, token);
            if (active == null) {
                throw ex;
            }
            return active;
        }
    }

    private Map<String, Object> findAttemptById(UUID userId, UUID lessonId, String attemptId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_quiz_attempts",
            buildQuery(Map.of(
                "select", "*",
                "id", "eq." + attemptId,
                "user_id", "eq." + userId,
                "lesson_id", "eq." + lessonId,
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findActiveAttempt(UUID userId, UUID lessonId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_quiz_attempts",
            buildQuery(Map.of(
                "select", "*",
                "user_id", "eq." + userId,
                "lesson_id", "eq." + lessonId,
                "status", "in.(in_progress,paused_no_hearts)",
                "order", "updated_at.desc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findLatestAttempt(UUID userId, UUID lessonId, String token) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_quiz_attempts",
            buildQuery(Map.of(
                "select", "*",
                "user_id", "eq." + userId,
                "lesson_id", "eq." + lessonId,
                "order", "updated_at.desc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> patchAttempt(Map<String, Object> attempt, Map<String, Object> patch, String token) {
        String attemptId = stringValue(attempt.get("id"));
        List<Map<String, Object>> updated = supabaseRestClient.patchList(
            "user_lesson_quiz_attempts",
            buildQuery(Map.of("id", "eq." + attemptId)),
            patch,
            token,
            MAP_LIST
        );
        return updated.isEmpty() ? attempt : updated.get(0);
    }

    private Map<String, Object> patchAttemptForAnswer(
        Map<String, Object> attempt,
        int expectedQuestionIndex,
        String expectedStatus,
        Map<String, Object> patch,
        String token
    ) {
        String attemptId = stringValue(attempt.get("id"));
        if (attemptId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiz attempt is invalid");
        }
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("id", "eq." + attemptId);
        filters.put("current_question_index", "eq." + expectedQuestionIndex);
        if (expectedStatus != null && !expectedStatus.isBlank()) {
            filters.put("status", "eq." + expectedStatus);
        }
        List<Map<String, Object>> updated = supabaseRestClient.patchList(
            "user_lesson_quiz_attempts",
            buildQuery(filters),
            patch,
            token,
            MAP_LIST
        );
        return updated.isEmpty() ? null : updated.get(0);
    }

    private void saveQuizResult(
        UUID userId,
        QuizContext context,
        int earnedScore,
        int maxScore,
        boolean passed,
        Map<String, Object> answers,
        String token
    ) {
        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("quiz_id", context.quiz().get("id"));
        insert.put("score", earnedScore);
        insert.put("max_score", maxScore);
        insert.put("percentage", maxScore <= 0 ? 0 : (earnedScore * 100.0 / maxScore));
        insert.put("passed", passed);
        insert.put("answers", answers);
        insert.put("attempted_at", OffsetDateTime.now());
        supabaseRestClient.postList("user_quiz_results", insert, token, MAP_LIST);
    }

    private void completeLessonAndGrantRewards(UUID userId, UUID lessonId, QuizContext context, String token) {
        OffsetDateTime now = OffsetDateTime.now();
        String lastSection = context.sections().isEmpty() ? null : sectionIdAt(context.sections(), context.sections().size() - 1);
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of(
                "select", "id,started_at",
                "user_id", "eq." + userId,
                "lesson_id", "eq." + lessonId,
                "order", "progress_percentage.desc,last_accessed_at.desc,created_at.asc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (rows.isEmpty()) {
            Map<String, Object> insert = new LinkedHashMap<>();
            insert.put("user_id", userId);
            insert.put("lesson_id", lessonId);
            insert.put("status", "completed");
            insert.put("progress_percentage", 100);
            insert.put("current_section", lastSection);
            insert.put("started_at", now);
            insert.put("completed_at", now);
            insert.put("last_accessed_at", now);
            supabaseRestClient.postList("user_lesson_progress", insert, token, MAP_LIST);
        } else {
            String rowId = stringValue(rows.get(0).get("id"));
            OffsetDateTime startedAt = parseOffsetDateTime(rows.get(0).get("started_at"));
            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("status", "completed");
            patch.put("progress_percentage", 100);
            patch.put("current_section", lastSection);
            patch.put("started_at", startedAt == null ? now : startedAt);
            patch.put("completed_at", now);
            patch.put("last_accessed_at", now);
            supabaseRestClient.patchList(
                "user_lesson_progress",
                buildQuery(Map.of("id", "eq." + rowId)),
                patch,
                token,
                MAP_LIST
            );
        }

        int xpReward = parseInteger(context.lesson().get("xp_reward")) == null ? 0 : parseInteger(context.lesson().get("xp_reward"));
        String badgeName = stringValue(context.lesson().get("badge_name"));
        boolean newlyAwarded = awardLessonReward(userId, lessonId, xpReward, badgeName, token);
        if (!newlyAwarded) {
            return;
        }
        incrementProfileXp(userId, xpReward, token);
        updateProfileStreak(userId, token);
        if (badgeName != null) {
            insertBadgeAchievement(userId, badgeName, token);
        }
        incrementLessonCompletionCount(lessonId);
    }

    private boolean awardLessonReward(UUID userId, UUID lessonId, int xpReward, String badgeName, String token) {
        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", userId);
        insert.put("lesson_id", lessonId);
        insert.put("xp_awarded", xpReward);
        insert.put("badge_name", badgeName);
        insert.put("awarded_at", OffsetDateTime.now());
        try {
            List<Map<String, Object>> created = supabaseRestClient.postList("user_lesson_rewards", insert, token, MAP_LIST);
            return !created.isEmpty();
        } catch (ResponseStatusException ex) {
            if (isUniqueViolation(ex)) {
                return false;
            }
            if (!isRowLevelSecurityViolation(ex)) {
                throw ex;
            }
        }

        try {
            List<Map<String, Object>> created = supabaseAdminRestClient.postList("user_lesson_rewards", insert, MAP_LIST);
            return !created.isEmpty();
        } catch (ResponseStatusException ex) {
            if (isUniqueViolation(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private void incrementProfileXp(UUID userId, int xpReward, String token) {
        List<Map<String, Object>> profiles = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("select", "id,reputation_points", "user_id", "eq." + userId, "limit", "1")),
            token,
            MAP_LIST
        );
        if (profiles.isEmpty()) {
            return;
        }
        Map<String, Object> profile = profiles.get(0);
        int currentXp = parseInteger(profile.get("reputation_points")) == null ? 0 : parseInteger(profile.get("reputation_points"));
        int nextXp = currentXp + Math.max(0, xpReward);
        supabaseRestClient.patchList(
            "profiles",
            buildQuery(Map.of("id", "eq." + stringValue(profile.get("id")))),
            Map.of(
                "reputation_points", nextXp,
                "updated_at", OffsetDateTime.now()
            ),
            token,
            MAP_LIST
        );
    }

    private void updateProfileStreak(UUID userId, String token) {
        List<Map<String, Object>> profiles = supabaseRestClient.getList(
            "profiles",
            buildQuery(Map.of("select", "id,current_streak,longest_streak,last_activity_date", "user_id", "eq." + userId, "limit", "1")),
            token,
            MAP_LIST
        );
        if (profiles.isEmpty()) {
            return;
        }

        Map<String, Object> profile = profiles.get(0);
        String profileId = stringValue(profile.get("id"));
        if (profileId == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate lastActivityDate = parseLocalDate(profile.get("last_activity_date"));
        int currentStreak = parseInteger(profile.get("current_streak")) == null ? 0 : parseInteger(profile.get("current_streak"));
        int longestStreak = parseInteger(profile.get("longest_streak")) == null ? 0 : parseInteger(profile.get("longest_streak"));

        if (lastActivityDate != null && lastActivityDate.equals(today)) {
            return;
        }

        int nextCurrentStreak;
        if (lastActivityDate != null && lastActivityDate.plusDays(1).equals(today)) {
            nextCurrentStreak = Math.max(1, currentStreak + 1);
        } else {
            nextCurrentStreak = 1;
        }
        int nextLongestStreak = Math.max(longestStreak, nextCurrentStreak);

        try {
            supabaseRestClient.patchList(
                "profiles",
                buildQuery(Map.of("id", "eq." + profileId)),
                Map.of(
                    "current_streak", nextCurrentStreak,
                    "longest_streak", nextLongestStreak,
                    "last_activity_date", today.toString(),
                    "updated_at", OffsetDateTime.now()
                ),
                token,
                MAP_LIST
            );
        } catch (ResponseStatusException ex) {
            log.warn("Unable to update streak for user {}", userId, ex);
        }
    }

    private void insertBadgeAchievement(UUID userId, String badgeName, String token) {
        supabaseRestClient.postList(
            "user_achievements",
            Map.of(
                "user_id", userId,
                "achievement_name", badgeName,
                "achievement_type", "lesson_badge",
                "description", "Earned by passing lesson quiz",
                "earned_at", OffsetDateTime.now()
            ),
            token,
            MAP_LIST
        );
    }

    private void incrementLessonCompletionCount(UUID lessonId) {
        List<Map<String, Object>> lessons = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of("select", "id,completion_count", "id", "eq." + lessonId, "limit", "1")),
            MAP_LIST
        );
        if (lessons.isEmpty()) {
            return;
        }
        int currentCount = parseInteger(lessons.get(0).get("completion_count")) == null ? 0 : parseInteger(lessons.get(0).get("completion_count"));
        supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            Map.of(
                "completion_count", currentCount + 1,
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );
    }

    private LessonQuizStateResponse buildStateResponse(
        Map<String, Object> attempt,
        List<Map<String, Object>> questions,
        HeartsState hearts,
        String status,
        boolean canAnswer,
        boolean canRestart
    ) {
        Integer questionIndex = parseInteger(attempt.get("current_question_index"));
        if (questionIndex == null) {
            questionIndex = 0;
        }
        LessonQuizQuestionResponse currentQuestion = null;
        if (questionIndex >= 0 && questionIndex < questions.size() && canAnswer) {
            currentQuestion = toQuestionResponse(questions.get(questionIndex));
        }
        return new LessonQuizStateResponse(
            stringValue(attempt.get("id")),
            status,
            questionIndex,
            questions.size(),
            parseInteger(attempt.get("correct_count")),
            parseInteger(attempt.get("earned_score")),
            parseInteger(attempt.get("max_score")),
            currentQuestion,
            new LessonHeartsStatusResponse(hearts.heartsRemaining(), hearts.refillAt()),
            canAnswer,
            canRestart,
            wrongQuestionIdsFromAttempt(attempt)
        );
    }

    private LessonQuizQuestionResponse toQuestionResponse(Map<String, Object> question) {
        String questionType = questionTypeOf(question);
        LessonQuizQuestionGrader grader = graderRegistry.require(questionType);
        return new LessonQuizQuestionResponse(
            stringValue(question.get("id")),
            questionType,
            stringValue(question.get("question_text")),
            stringValue(question.get("question_text")),
            grader.buildPayload(question),
            stringValue(question.get("explanation")),
            parseInteger(question.get("points")),
            parseInteger(question.get("order_index")),
            stringValue(question.get("media_url")),
            parseInteger(question.get("template_version")) == null ? 1 : parseInteger(question.get("template_version"))
        );
    }

    private List<Map<String, Object>> resolveQuestionsForAttempt(
        List<Map<String, Object>> allQuestions,
        Map<String, Object> attempt
    ) {
        if (attempt == null) {
            return new ArrayList<>(allQuestions);
        }
        List<String> questionIds = questionIdsFromAttempt(attempt);
        if (questionIds.isEmpty()) {
            return orderQuestionsForAttempt(allQuestions, stringValue(attempt.get("id")));
        }
        List<Map<String, Object>> filtered = filterQuestionsByIds(allQuestions, questionIds);
        if (filtered.isEmpty()) {
            return orderQuestionsForAttempt(allQuestions, stringValue(attempt.get("id")));
        }
        return filtered;
    }

    private List<Map<String, Object>> filterQuestionsByIds(List<Map<String, Object>> allQuestions, List<String> questionIds) {
        Map<String, Map<String, Object>> questionById = new LinkedHashMap<>();
        for (Map<String, Object> question : allQuestions) {
            String id = stringValue(question.get("id"));
            if (id != null) {
                questionById.put(id, question);
            }
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (String questionId : questionIds) {
            Map<String, Object> question = questionById.get(questionId);
            if (question != null) {
                filtered.add(question);
            }
        }
        return filtered;
    }

    private int computeMaxScore(List<Map<String, Object>> questions) {
        return questions.stream()
            .map(question -> parseInteger(question.get("points")))
            .mapToInt(points -> points == null ? 10 : points)
            .sum();
    }

    private List<String> questionIdsFromAttempt(Map<String, Object> attempt) {
        Map<String, Object> answers = normalizeAnswers(attempt.get("answers"));
        return metadataIdsFromAnswers(answers, ATTEMPT_META_QUESTION_IDS);
    }

    private List<String> wrongQuestionIdsFromAttempt(Map<String, Object> attempt) {
        Map<String, Object> answers = normalizeAnswers(attempt.get("answers"));
        return wrongQuestionIdsFromAnswers(answers);
    }

    private List<String> wrongQuestionIdsFromAnswers(Map<String, Object> answers) {
        return metadataIdsFromAnswers(answers, ATTEMPT_META_WRONG_QUESTION_IDS);
    }

    private void addWrongQuestionId(Map<String, Object> answers, String questionId) {
        if (questionId == null) {
            return;
        }
        List<String> wrongIds = new ArrayList<>(wrongQuestionIdsFromAnswers(answers));
        if (!wrongIds.contains(questionId)) {
            wrongIds.add(questionId);
        }
        answers.put(ATTEMPT_META_WRONG_QUESTION_IDS, wrongIds);
    }

    private List<String> metadataIdsFromAnswers(Map<String, Object> answers, String key) {
        Object value = answers.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (Object item : list) {
            String id = stringValue(item);
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<String, Object> stripAttemptMetadata(Map<String, Object> answers) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String key = stringValue(entry.getKey());
            if (key == null) {
                continue;
            }
            if (ATTEMPT_META_QUESTION_IDS.equals(key) || ATTEMPT_META_WRONG_QUESTION_IDS.equals(key)) {
                continue;
            }
            sanitized.put(key, entry.getValue());
        }
        return sanitized;
    }

    private List<Map<String, Object>> orderQuestionsForAttempt(List<Map<String, Object>> questions, String attemptId) {
        List<Map<String, Object>> ordered = new ArrayList<>(questions);
        if (ordered.size() <= 1 || attemptId == null || attemptId.isBlank()) {
            return ordered;
        }
        ordered.sort(
            Comparator.comparingLong((Map<String, Object> question) -> stableQuestionSeed(attemptId, question))
                .thenComparing(question -> stringValue(question.get("id")), Comparator.nullsLast(String::compareTo))
        );
        return ordered;
    }

    private long stableQuestionSeed(String attemptId, Map<String, Object> question) {
        String questionId = stringValue(question.get("id"));
        String material = attemptId + "|" + (questionId == null ? "" : questionId);
        long hash = 1469598103934665603L; // FNV-1a offset basis
        for (int i = 0; i < material.length(); i++) {
            hash ^= material.charAt(i);
            hash *= 1099511628211L;
        }
        return hash;
    }

    private LessonProgressState loadProgressState(
        UUID userId,
        UUID lessonId,
        List<Map<String, Object>> sections,
        String token
    ) {
        List<Map<String, Object>> rows = supabaseRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of(
                "select", "id,status,progress_percentage,current_section,last_accessed_at,created_at",
                "user_id", "eq." + userId,
                "lesson_id", "eq." + lessonId,
                "order", "progress_percentage.desc,last_accessed_at.desc,created_at.asc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        if (rows.isEmpty()) {
            return new LessonProgressState(false, 0);
        }
        Map<String, Object> row = rows.get(0);
        int progress = parseInteger(row.get("progress_percentage")) == null ? 0 : parseInteger(row.get("progress_percentage"));
        String currentSection = stringValue(row.get("current_section"));
        int completed = computeCompletedSections(progress, currentSection, sections);
        return new LessonProgressState(true, completed);
    }

    private int computeCompletedSections(int progressPercentage, String currentSection, List<Map<String, Object>> sections) {
        if (sections.isEmpty()) {
            return 0;
        }
        int clampedProgress = Math.max(0, Math.min(progressPercentage, 100));
        int byProgress = (int) Math.floor((clampedProgress / 100.0) * sections.size());
        int byCurrent = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (sectionIdAt(sections, i).equals(currentSection)) {
                byCurrent = i + 1;
                break;
            }
        }
        return Math.max(0, Math.min(sections.size(), Math.max(byProgress, byCurrent)));
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

    private String questionTypeOf(Map<String, Object> question) {
        String type = stringValue(question.get("question_type"));
        return type == null ? "multiple_choice" : type.toLowerCase();
    }

    private Map<String, Object> normalizeOptions(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = entry.getKey().toString().trim().toUpperCase();
            if (key.isBlank()) {
                continue;
            }
            normalized.put(key, entry.getValue());
        }
        return normalized;
    }

    private Map<String, Object> normalizeAnswers(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().toString(), entry.getValue());
        }
        return normalized;
    }

    private String sectionIdAt(List<Map<String, Object>> sections, int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return stringValue(sections.get(index).get("id"));
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
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isUniqueViolation(ResponseStatusException ex) {
        String normalized = normalizeSupabaseError(ex);
        return normalized.contains("duplicate key")
            || normalized.contains("already exists")
            || normalized.contains("unique constraint")
            || normalized.contains("on conflict");
    }

    private boolean isRowLevelSecurityViolation(ResponseStatusException ex) {
        String normalized = normalizeSupabaseError(ex);
        return normalized.contains("row-level security")
            || normalized.contains("violates row-level security policy")
            || normalized.contains("\"code\":\"42501\"");
    }

    private String normalizeSupabaseError(ResponseStatusException ex) {
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
        return normalizedBuilder.toString();
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

    public record ProgressMetadata(
        int totalStops,
        int completedStops,
        String currentStopId,
        int remainingStops,
        String quizStatus,
        int heartsRemaining,
        OffsetDateTime heartsRefillAt,
        String nextStopType
    ) {}

    private record HeartsState(int heartsRemaining, OffsetDateTime refillAt) {}
    private record LessonProgressState(boolean isEnrolled, int completedSections) {}
    private record QuizContext(
        Map<String, Object> lesson,
        List<Map<String, Object>> sections,
        Map<String, Object> quiz,
        List<Map<String, Object>> questions,
        int maxScore
    ) {}
}
