package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.AdminStatsResponse;
import com.rotiprata.domain.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
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

    private final SupabaseRestClient supabaseRestClient;
    private final UserService userService;

    public AdminService(SupabaseRestClient supabaseRestClient, UserService userService) {
        this.supabaseRestClient = supabaseRestClient;
        this.userService = userService;
    }

    public List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        syncPendingContentIntoModerationQueue(token);
        return supabaseRestClient.getList(
            "moderation_queue",
            buildQuery(Map.of(
                "select", "*,content:content_id!inner(*)",
                "content.status", "eq.pending",
                "order", "submitted_at.asc"
            )),
            token,
            MAP_LIST
        );
    }

    public void approveContent(UUID adminUserId, UUID contentId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            token,
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
        String token = requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            token,
            List.of("rejected"),
            feedback.trim(),
            adminUserId
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }
    }

    public List<Map<String, Object>> getOpenFlags(UUID adminUserId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        return supabaseRestClient.getList(
            "content_flags",
            buildQuery(Map.of(
                "select", "*",
                "status", "eq.pending",
                "order", "created_at.desc"
            )),
            token,
            MAP_LIST
        );
    }

    public void resolveFlag(UUID adminUserId, UUID flagId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        List<Map<String, Object>> updated = supabaseRestClient.patchList(
            "content_flags",
            buildQuery(Map.of("id", "eq." + flagId)),
            Map.of(
                "status", "resolved",
                "resolved_by", adminUserId,
                "resolved_at", OffsetDateTime.now()
            ),
            token,
            MAP_LIST
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag not found");
        }
    }

    public AdminStatsResponse getStats(UUID adminUserId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        int totalUsers = count("profiles", buildQuery(Map.of("select", "id")), token);
        int activeUsers = count(
            "profiles",
            buildQuery(Map.of("select", "id", "last_activity_date", "eq." + LocalDate.now())),
            token
        );
        List<Map<String, Object>> contentStatuses = supabaseRestClient.getList(
            "content",
            buildQuery(Map.of("select", "status")),
            token,
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
        int totalLessons = count("lessons", buildQuery(Map.of("select", "id")), token);
        return new AdminStatsResponse(
            totalUsers,
            activeUsers,
            totalContent,
            pendingModeration,
            totalLessons,
            approvalRate
        );
    }

    private void syncPendingContentIntoModerationQueue(String accessToken) {
        List<Map<String, Object>> pendingContent = supabaseRestClient.getList(
            "content",
            buildQuery(Map.of("select", "id", "status", "eq.pending")),
            accessToken,
            MAP_LIST
        );
        if (pendingContent.isEmpty()) {
            return;
        }
        List<Map<String, Object>> existingQueue = supabaseRestClient.getList(
            "moderation_queue",
            buildQuery(Map.of("select", "content_id")),
            accessToken,
            MAP_LIST
        );
        Set<String> existingContentIds = new HashSet<>();
        for (Map<String, Object> row : existingQueue) {
            Object contentId = row.get("content_id");
            if (contentId != null) {
                existingContentIds.add(String.valueOf(contentId));
            }
        }

        for (Map<String, Object> row : pendingContent) {
            Object contentId = row.get("id");
            if (contentId == null) {
                continue;
            }
            String contentIdText = String.valueOf(contentId);
            if (existingContentIds.contains(contentIdText)) {
                continue;
            }
            try {
                supabaseRestClient.postList(
                    "moderation_queue",
                    Map.of("content_id", contentIdText),
                    accessToken,
                    MAP_LIST
                );
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                    throw ex;
                }
            }
        }
    }

    private int count(String table, String query, String accessToken) {
        return supabaseRestClient.getList(table, query, accessToken, MAP_LIST).size();
    }

    private List<Map<String, Object>> patchContentReview(
        UUID contentId,
        String accessToken,
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
                return supabaseRestClient.patchList(
                    "content",
                    buildQuery(Map.of("id", "eq." + contentId)),
                    patch,
                    accessToken,
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
