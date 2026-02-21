package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.AdminStatsResponse;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
public class AdminService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final UserService userService;

    public AdminService(SupabaseAdminRestClient supabaseAdminRestClient, UserService userService) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.userService = userService;
    }

    public List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> pendingContent = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "*",
                "status", "eq.pending",
                "order", "created_at.asc"
            )),
            MAP_LIST
        );
        if (pendingContent.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> queueByContentId = new HashMap<>();
        List<Map<String, Object>> queueRows = supabaseAdminRestClient.getList(
            "moderation_queue",
            buildQuery(Map.of("select", "id,content_id,submitted_at,priority,assigned_to,notes")),
            MAP_LIST
        );
        for (Map<String, Object> row : queueRows) {
            Object contentId = row.get("content_id");
            if (contentId != null) {
                queueByContentId.put(String.valueOf(contentId), row);
            }
        }

        java.util.ArrayList<Map<String, Object>> merged = new java.util.ArrayList<>(pendingContent.size());
        for (Map<String, Object> content : pendingContent) {
            String contentId = String.valueOf(content.get("id"));
            Map<String, Object> queue = queueByContentId.get(contentId);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", queue != null ? queue.get("id") : contentId);
            item.put("content_id", contentId);
            item.put("submitted_at", queue != null ? queue.get("submitted_at") : content.get("created_at"));
            item.put("priority", queue != null ? queue.getOrDefault("priority", 0) : 0);
            item.put("assigned_to", queue != null ? queue.get("assigned_to") : null);
            item.put("notes", queue != null ? queue.get("notes") : null);
            item.put("content", content);
            merged.add(item);
        }

        return merged;
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
        if (feedback == null || feedback.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            List.of("rejected"),
            feedback.trim(),
            adminUserId
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
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
}
