package com.rotiprata.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.rotiprata.api.dto.AdminContentQuizQuestionRequest;
import com.rotiprata.api.dto.AdminContentQuizRequest;
import com.rotiprata.api.dto.ContentQuizQuestionResponse;
import com.rotiprata.api.dto.ContentQuizResponse;
import com.rotiprata.api.dto.ContentQuizSubmitRequest;
import com.rotiprata.api.dto.ContentQuizSubmitResponse;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ContentQuizService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final int DEFAULT_POINTS = 10;
    private static final int DEFAULT_PASSING_SCORE = 70;

    private final SupabaseRestClient supabaseRestClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .findAndRegisterModules();

    public ContentQuizService(
        SupabaseRestClient supabaseRestClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        UserService userService
    ) {
        this.supabaseRestClient = supabaseRestClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.userService = userService;
    }

    public ContentQuizResponse getContentQuiz(UUID userId, UUID contentId, String accessToken) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        Map<String, Object> quiz = findActiveContentQuiz(contentId, token);
        if (quiz == null) {
            return null;
        }
        String quizId = stringValue(quiz.get("id"));
        List<Map<String, Object>> questions = fetchQuizQuestions(quizId, token);
        return toQuizResponse(quiz, questions);
    }

    public ContentQuizSubmitResponse submitContentQuiz(
        UUID userId,
        UUID contentId,
        ContentQuizSubmitRequest request,
        String accessToken
    ) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        Map<String, Object> quiz = findActiveContentQuiz(contentId, token);
        if (quiz == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found");
        }
        String quizId = stringValue(quiz.get("id"));
        List<Map<String, Object>> questions = fetchQuizQuestions(quizId, token);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quiz has no questions");
        }

        Map<String, String> answers = request == null ? Map.of() : request.answers();
        int maxScore = 0;
        int earnedScore = 0;
        for (Map<String, Object> question : questions) {
            String questionId = stringValue(question.get("id"));
            String correctAnswer = stringValue(question.get("correct_answer"));
            int points = parseInteger(question.get("points"), DEFAULT_POINTS);
            maxScore += points;
            if (questionId == null || correctAnswer == null) {
                continue;
            }
            String provided = answers != null ? answers.get(questionId) : null;
            if (provided != null && correctAnswer.trim().equalsIgnoreCase(provided.trim())) {
                earnedScore += points;
            }
        }

        double percentage = maxScore == 0 ? 0 : (earnedScore * 100.0) / maxScore;
        int passingScore = parseInteger(quiz.get("passing_score"), DEFAULT_PASSING_SCORE);
        boolean passed = percentage >= passingScore;

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("quiz_id", quizId);
        insert.put("user_id", userId);
        insert.put("score", earnedScore);
        insert.put("max_score", maxScore);
        insert.put("percentage", percentage);
        insert.put("passed", passed);
        insert.put("answers", serializeAnswers(answers));
        insert.put("time_taken_seconds", request == null ? null : request.timeTakenSeconds());
        insert.put("attempted_at", OffsetDateTime.now());

        supabaseRestClient.postList("user_quiz_results", insert, token, MAP_LIST);

        return new ContentQuizSubmitResponse(earnedScore, maxScore, percentage, passed);
    }

    public List<ContentQuizQuestionResponse> getAdminContentQuiz(
        UUID adminUserId,
        UUID contentId,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        Map<String, Object> quiz = findActiveContentQuizAdmin(contentId);
        if (quiz == null) {
            return List.of();
        }
        String quizId = stringValue(quiz.get("id"));
        List<Map<String, Object>> questions = fetchQuizQuestionsAdmin(quizId);
        return toQuestionResponses(questions);
    }

    public List<ContentQuizQuestionResponse> replaceAdminContentQuiz(
        UUID adminUserId,
        UUID contentId,
        AdminContentQuizRequest request,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }

        List<AdminContentQuizQuestionRequest> questions = request == null ? List.of() : request.questions();
        if (questions == null || questions.isEmpty()) {
            archiveActiveContentQuiz(contentId);
            return List.of();
        }

        archiveActiveContentQuiz(contentId);
        Map<String, Object> content = fetchContentMetadata(contentId);
        String title = content == null ? "Content Quiz" : stringValue(content.get("title"));
        Map<String, Object> quizInsert = new LinkedHashMap<>();
        quizInsert.put("content_id", contentId);
        quizInsert.put("title", title == null || title.isBlank() ? "Content Quiz" : title + " Quiz");
        quizInsert.put("description", "Quick quiz for this video");
        quizInsert.put("quiz_type", "multiple_choice");
        quizInsert.put("is_active", true);
        quizInsert.put("archived_at", null);
        quizInsert.put("passing_score", DEFAULT_PASSING_SCORE);
        quizInsert.put("created_by", adminUserId);
        quizInsert.put("updated_at", OffsetDateTime.now());

        List<Map<String, Object>> createdQuiz = supabaseAdminRestClient.postList("quizzes", quizInsert, MAP_LIST);
        if (createdQuiz.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to create quiz");
        }

        String quizId = stringValue(createdQuiz.get(0).get("id"));
        for (int i = 0; i < questions.size(); i++) {
            AdminContentQuizQuestionRequest question = questions.get(i);
            validateQuestion(question);

            String correctAnswer = normalizeCorrectAnswer(question.correctAnswer());
            Map<String, Object> questionMap = new LinkedHashMap<>();
            questionMap.put("quiz_id", quizId);
            questionMap.put("question_text", normalizeRequired(question.questionText(), "question_text"));
            questionMap.put("question_type", "multiple_choice");
            questionMap.put("options", extractOptions(question.options()));
            questionMap.put("correct_answer", correctAnswer);
            questionMap.put("answer_key", Map.of("correctChoiceId", correctAnswer.toUpperCase()));
            questionMap.put("ui_template", "multiple_choice");
            questionMap.put("template_version", 1);
            questionMap.put("explanation", normalizeOptional(question.explanation()));
            questionMap.put("points", normalizePoints(question.points()));
            questionMap.put("order_index", question.orderIndex() != null ? question.orderIndex() : i);
            supabaseAdminRestClient.postList("quiz_questions", questionMap, MAP_LIST);
        }

        List<Map<String, Object>> inserted = fetchQuizQuestionsAdmin(quizId);
        return toQuestionResponses(inserted);
    }

    private Map<String, Object> findActiveContentQuiz(UUID contentId, String token) {
        List<Map<String, Object>> quizzes = supabaseRestClient.getList(
            "quizzes",
            buildQuery(Map.of(
                "select", "*",
                "content_id", "eq." + contentId,
                "is_active", "eq.true",
                "archived_at", "is.null",
                "order", "created_at.desc",
                "limit", "1"
            )),
            token,
            MAP_LIST
        );
        return quizzes.isEmpty() ? null : quizzes.get(0);
    }

    private Map<String, Object> findActiveContentQuizAdmin(UUID contentId) {
        List<Map<String, Object>> quizzes = supabaseAdminRestClient.getList(
            "quizzes",
            buildQuery(Map.of(
                "select", "*",
                "content_id", "eq." + contentId,
                "is_active", "eq.true",
                "archived_at", "is.null",
                "order", "created_at.desc",
                "limit", "1"
            )),
            MAP_LIST
        );
        return quizzes.isEmpty() ? null : quizzes.get(0);
    }

    private void archiveActiveContentQuiz(UUID contentId) {
        Map<String, Object> quiz = findActiveContentQuizAdmin(contentId);
        if (quiz == null) {
            return;
        }
        String quizId = stringValue(quiz.get("id"));
        if (quizId == null) {
            return;
        }
        supabaseAdminRestClient.patchList(
            "quizzes",
            buildQuery(Map.of("id", "eq." + quizId)),
            Map.of(
                "is_active", false,
                "archived_at", OffsetDateTime.now(),
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );
    }

    private Map<String, Object> fetchContentMetadata(UUID contentId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,title",
                "id", "eq." + contentId,
                "limit", "1"
            )),
            MAP_LIST
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> fetchQuizQuestions(String quizId, String token) {
        if (quizId == null) {
            return List.of();
        }
        return supabaseRestClient.getList(
            "quiz_questions",
            buildQuery(Map.of(
                "select", "id,quiz_id,question_text,question_type,media_url,options,correct_answer,explanation,points,order_index,created_at",
                "quiz_id", "eq." + quizId,
                "order", "order_index.asc"
            )),
            token,
            MAP_LIST
        );
    }

    private List<Map<String, Object>> fetchQuizQuestionsAdmin(String quizId) {
        if (quizId == null) {
            return List.of();
        }
        return supabaseAdminRestClient.getList(
            "quiz_questions",
            buildQuery(Map.of(
                "select", "id,quiz_id,question_text,question_type,media_url,options,correct_answer,explanation,points,order_index,created_at",
                "quiz_id", "eq." + quizId,
                "order", "order_index.asc"
            )),
            MAP_LIST
        );
    }

    private ContentQuizResponse toQuizResponse(Map<String, Object> quiz, List<Map<String, Object>> questions) {
        List<ContentQuizQuestionResponse> questionResponses = toQuestionResponses(questions);
        return new ContentQuizResponse(
            stringValue(quiz.get("id")),
            stringValue(quiz.get("lesson_id")),
            stringValue(quiz.get("content_id")),
            stringValue(quiz.get("title")),
            stringValue(quiz.get("description")),
            stringValue(quiz.get("quiz_type")),
            parseIntegerOrNull(quiz.get("time_limit_seconds")),
            parseIntegerOrNull(quiz.get("passing_score")),
            parseBooleanOrNull(quiz.get("is_active")),
            stringValue(quiz.get("archived_at")),
            stringValue(quiz.get("created_by")),
            stringValue(quiz.get("created_at")),
            stringValue(quiz.get("updated_at")),
            questionResponses
        );
    }

    private List<ContentQuizQuestionResponse> toQuestionResponses(List<Map<String, Object>> questions) {
        List<ContentQuizQuestionResponse> responses = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            if (question == null) {
                continue;
            }
            responses.add(new ContentQuizQuestionResponse(
                stringValue(question.get("id")),
                stringValue(question.get("quiz_id")),
                stringValue(question.get("question_text")),
                stringValue(question.get("question_type")),
                stringValue(question.get("media_url")),
                extractOptions(question.get("options")),
                stringValue(question.get("correct_answer")),
                stringValue(question.get("explanation")),
                parseIntegerOrNull(question.get("points")),
                parseIntegerOrNull(question.get("order_index")),
                stringValue(question.get("created_at"))
            ));
        }
        return responses;
    }

    private Map<String, Object> extractOptions(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> options = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString().trim();
                if (key != null && !key.isBlank()) {
                    options.put(key.toUpperCase(), entry.getValue());
                }
            }
            return options;
        }
        if (value instanceof String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return new LinkedHashMap<>();
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(trimmed, Map.class);
                return parsed == null ? new LinkedHashMap<>() : parsed;
            } catch (JsonProcessingException ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private void validateQuestion(AdminContentQuizQuestionRequest question) {
        if (question == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
        String text = normalizeRequired(question.questionText(), "question_text");
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question text is required");
        }
        Map<String, Object> options = extractOptions(question.options());
        if (options.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two options are required");
        }
        String correctAnswer = normalizeCorrectAnswer(question.correctAnswer());
        if (!options.containsKey(correctAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correct answer must exist in options");
        }
    }

    private String normalizeCorrectAnswer(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correct answer is required");
        }
        return trimmed.toUpperCase();
    }

    private Integer normalizePoints(Integer points) {
        if (points == null || points < 1) {
            return DEFAULT_POINTS;
        }
        return points;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String serializeAnswers(Map<String, String> answers) {
        if (answers == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize answers");
        }
    }

    private String requireAdmin(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing user");
        }
        List<AppRole> roles = userService.getRoles(userId, token);
        if (!roles.contains(AppRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return token;
    }

    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Integer parseIntegerOrNull(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int parseInteger(Object value, int fallback) {
        Integer parsed = parseIntegerOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private Boolean parseBooleanOrNull(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
