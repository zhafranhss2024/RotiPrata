package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.AdminContentUpdateRequest;
import com.rotiprata.api.dto.AdminStatsResponse;
import com.rotiprata.domain.AppRole;
import com.rotiprata.domain.Content;
import com.rotiprata.domain.ContentTag;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AdminService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Content>> CONTENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ContentTag>> TAG_LIST = new TypeReference<>() {};
    private static final int MAX_TITLE = 80;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_OBJECTIVE = 160;
    private static final int MAX_LONG_TEXT = 500;
    private static final int MAX_OLDER_REFERENCE = 160;
    private static final int MAX_TAG = 30;

    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final UserService userService;

    public AdminService(SupabaseAdminRestClient supabaseAdminRestClient, UserService userService) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.userService = userService;
    }

    public List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        return supabaseAdminRestClient.getList(
            "moderation_queue",
            buildQuery(Map.of(
                "select", "*,content:content_id!inner(*)",
                "content.status", "eq.pending",
                "content.is_submitted", "eq.true",
                "order", "submitted_at.asc"
            )),
            MAP_LIST
        );
    }

    public void approveContent(UUID adminUserId, UUID contentId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            List.of("approved", "accepted"),
            null,
            adminUserId
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
    }

    public void rejectContent(UUID adminUserId, UUID contentId, String feedback, String accessToken) {
        String sanitized = sanitizeText(feedback, MAX_LONG_TEXT);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            List.of("rejected"),
            sanitized,
            adminUserId
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
    }

    public Content updateContentMetadata(
        UUID adminUserId,
        UUID contentId,
        AdminContentUpdateRequest request,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        Map<String, Object> patch = new java.util.HashMap<>();
        patch.put("title", sanitizeRequired(request.title(), MAX_TITLE, "title"));
        patch.put("description", sanitizeRequired(request.description(), MAX_DESCRIPTION, "description"));
        patch.put("learning_objective", sanitizeRequired(request.learningObjective(), MAX_OBJECTIVE, "learning objective"));
        patch.put("origin_explanation", sanitizeRequired(request.originExplanation(), MAX_LONG_TEXT, "origin explanation"));
        patch.put("definition_literal", sanitizeRequired(request.definitionLiteral(), MAX_LONG_TEXT, "definition literal"));
        patch.put("definition_used", sanitizeRequired(request.definitionUsed(), MAX_LONG_TEXT, "definition used"));
        patch.put("older_version_reference", sanitizeRequired(request.olderVersionReference(), MAX_OLDER_REFERENCE, "older version reference"));
        patch.put("category_id", request.categoryId());
        patch.put("updated_at", OffsetDateTime.now());

        List<Content> updated = supabaseAdminRestClient.patchList(
            "content",
            buildQuery(Map.of("id", "eq." + contentId)),
            patch,
            CONTENT_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

        replaceTags(contentId, request.tags());
        return updated.get(0);
    }

    public List<Map<String, Object>> getOpenFlags(UUID adminUserId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        return supabaseAdminRestClient.getList(
            "content_flags",
            buildQuery(Map.of(
                "select", "*",
                "status", "eq.pending",
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
    }

    public void resolveFlag(UUID adminUserId, UUID flagId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = supabaseAdminRestClient.patchList(
            "content_flags",
            buildQuery(Map.of("id", "eq." + flagId)),
            Map.of(
                "status", "resolved",
                "resolved_by", adminUserId,
                "resolved_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag not found");
        }
    }

    public AdminStatsResponse getStats(UUID adminUserId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        int totalUsers = count("profiles", buildQuery(Map.of("select", "id")));
        int activeUsers = count(
            "profiles",
            buildQuery(Map.of("select", "id", "last_activity_date", "eq." + LocalDate.now()))
        );
        List<Map<String, Object>> contentStatuses = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of("select", "status")),
            MAP_LIST
        );
        int totalContent = contentStatuses.size();
        int pendingModeration = (int) contentStatuses.stream()
            .filter(row -> "pending".equalsIgnoreCase(String.valueOf(row.get("status"))))
            .count();
        int approved = (int) contentStatuses.stream()
            .filter(row -> {
                String status = String.valueOf(row.get("status"));
                return "approved".equalsIgnoreCase(status) || "accepted".equalsIgnoreCase(status);
            })
            .count();
        int rejected = (int) contentStatuses.stream()
            .filter(row -> "rejected".equalsIgnoreCase(String.valueOf(row.get("status"))))
            .count();
        int reviewed = approved + rejected;
        int approvalRate = reviewed == 0 ? 0 : Math.toIntExact(Math.round((approved * 100.0) / reviewed));
        int totalLessons = count("lessons", buildQuery(Map.of("select", "id")));
        return new AdminStatsResponse(
            totalUsers,
            activeUsers,
            totalContent,
            pendingModeration,
            totalLessons,
            approvalRate
        );
    }

    private int count(String table, String query) {
        return supabaseAdminRestClient.getList(table, query, MAP_LIST).size();
    }

    private List<Map<String, Object>> patchContentReview(
        UUID contentId,
        List<String> candidateStatuses,
        String reviewFeedback,
        UUID adminUserId
    ) {
        ResponseStatusException lastEnumError = null;
        for (String status : candidateStatuses) {
            java.util.HashMap<String, Object> patch = new java.util.HashMap<>();
            patch.put("status", status);
            patch.put("reviewed_by", adminUserId);
            patch.put("reviewed_at", OffsetDateTime.now());
            patch.put("review_feedback", reviewFeedback);
            try {
                return supabaseAdminRestClient.patchList(
                    "content",
                    buildQuery(Map.of("id", "eq." + contentId)),
                    patch,
                    MAP_LIST
                );
            } catch (ResponseStatusException ex) {
                String reason = ex.getReason() == null ? "" : ex.getReason().toLowerCase();
                boolean isEnumValueError = ex.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                    && reason.contains("invalid input value for enum");
                if (!isEnumValueError) {
                    throw ex;
                }
                lastEnumError = ex;
            }
        }
        if (lastEnumError != null) {
            throw lastEnumError;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No content status candidate provided");
    }

    private String requireAdmin(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
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

    private void replaceTags(UUID contentId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags are required");
        }
        adminRestClientDeleteTags(contentId);
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String sanitized = sanitizeTag(tag);
            if (sanitized != null && !sanitized.isBlank()) {
                normalized.add(sanitized);
            }
        }
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags are required");
        }
        List<Map<String, Object>> rows = normalized.stream()
            .map(tag -> {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("content_id", contentId);
                row.put("tag", tag);
                return row;
            })
            .toList();
        supabaseAdminRestClient.postList("content_tags", rows, TAG_LIST);
    }

    private void adminRestClientDeleteTags(UUID contentId) {
        supabaseAdminRestClient.deleteList(
            "content_tags",
            buildQuery(Map.of("content_id", "eq." + contentId)),
            TAG_LIST
        );
    }

    private String sanitizeRequired(String value, int maxLength, String fieldName) {
        String sanitized = sanitizeText(value, maxLength);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return sanitized;
    }

    private String sanitizeTag(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        return sanitizeText(trimmed, MAX_TAG);
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\x00-\\x1F\\x7F]", "");
        String collapsed = cleaned.replaceAll("\\s+", " ").trim();
        if (maxLength > 0 && collapsed.length() > maxLength) {
            return collapsed.substring(0, maxLength);
        }
        return collapsed;
    }
}
