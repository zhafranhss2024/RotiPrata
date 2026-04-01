package com.rotiprata.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.dto.AdminContentUpdateRequest;
import com.rotiprata.api.dto.AdminStatsResponse;
import com.rotiprata.domain.AppRole;
import com.rotiprata.domain.Content;
import com.rotiprata.domain.ContentTag;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private static final int FLAG_REPORTS_PAGE_SIZE = 5;

    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;
    private final UserService userService;

    public AdminService(
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService,
        UserService userService
    ) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
        this.userService = userService;
    }

    public List<Map<String, Object>> getModerationQueue(UUID adminUserId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        return supabaseAdminRestClient.getList(
            "moderation_queue",
            buildQuery(Map.of(
                "select", "*,content:content_id!inner(*,content_tags(tag))",
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
        List<Map<String, Object>> flags = supabaseAdminRestClient.getList(
            "content_flags",
            buildQuery(Map.of(
                "select", "*,content:content_id(*,content_tags(tag))",
                "status", "eq.pending",
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
        List<Map<String, Object>> groupedFlags = groupOpenFlags(flags);
        attachFlagContentCreators(groupedFlags);
        return groupedFlags;
    }

    public void resolveFlag(UUID adminUserId, UUID flagId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        Map<String, Object> flag = getPendingFlag(flagId);
        UUID contentId = parseUuid(flag.get("content_id"));
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag content not found");
        }
        resolvePendingFlagsForContent(contentId, adminUserId);
    }

    public void takeDownFlag(UUID adminUserId, UUID flagId, String feedback, String accessToken) {
        String sanitized = sanitizeText(feedback, MAX_LONG_TEXT);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Takedown reason is required");
        }
        requireAdmin(adminUserId, accessToken);

        Map<String, Object> flag = getPendingFlag(flagId);
        UUID contentId = parseUuid(flag.get("content_id"));
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag content not found");
        }

        List<Map<String, Object>> updated = patchContentReview(
            contentId,
            List.of("rejected"),
            sanitized,
            adminUserId
        );
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Content not found");
        }

        resolvePendingFlagsForContent(contentId, adminUserId);
    }

    public Map<String, Object> getFlagReports(
        UUID adminUserId,
        UUID flagId,
        int page,
        String reporterQuery,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        Map<String, Object> flag = getPendingFlag(flagId);
        UUID contentId = parseUuid(flag.get("content_id"));
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag content not found");
        }

        int normalizedPage = Math.max(page, 1);
        int offset = (normalizedPage - 1) * FLAG_REPORTS_PAGE_SIZE;
        String normalizedReporterQuery = normalizeReporterQuery(reporterQuery);

        List<Map<String, Object>> rows = fetchFlagReportRows(
            contentId,
            normalizedReporterQuery,
            offset,
            FLAG_REPORTS_PAGE_SIZE + 1
        );
        boolean hasNext = rows.size() > FLAG_REPORTS_PAGE_SIZE;
        List<Map<String, Object>> pageRows = hasNext ? rows.subList(0, FLAG_REPORTS_PAGE_SIZE) : rows;
        Map<UUID, Map<String, Object>> profileByUserId = fetchProfilesByUserId(extractReportedUserIds(pageRows));

        List<Map<String, Object>> items = pageRows.stream()
            .map(row -> createFlagReport(row, profileByUserId.get(parseUuid(row.get("reported_by")))))
            .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", normalizedPage);
        response.put("page_size", FLAG_REPORTS_PAGE_SIZE);
        response.put("has_next", hasNext);
        response.put("query", normalizedReporterQuery == null ? "" : normalizedReporterQuery);
        return response;
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

    private Map<String, Object> getPendingFlag(UUID flagId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content_flags",
            buildQuery(Map.of(
                "select", "id,content_id,status",
                "id", "eq." + flagId,
                "limit", "1"
            )),
            MAP_LIST
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag not found");
        }
        Map<String, Object> flag = rows.get(0);
        String status = String.valueOf(flag.get("status"));
        if (!"pending".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Flag already resolved");
        }
        return flag;
    }

    private List<Map<String, Object>> groupOpenFlags(List<Map<String, Object>> flags) {
        if (flags == null || flags.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> flag : flags) {
            if (flag == null) {
                continue;
            }

            Object contentId = flag.get("content_id");
            if (contentId == null) {
                continue;
            }

            String groupKey = contentId.toString();
            Map<String, Object> group = grouped.computeIfAbsent(groupKey, ignored -> createFlagGroup(flag));
            int reportCount = toInt(group.get("report_count")) + 1;
            group.put("report_count", reportCount);

            String reason = normalizeNullableText(toStringOrNull(flag.get("reason")));
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) group.get("reasons");
            if (reason != null && !reasons.contains(reason)) {
                reasons.add(reason);
            }

            String description = normalizeNullableText(toStringOrNull(flag.get("description")));
            if (description != null) {
                group.put("notes_count", toInt(group.get("notes_count")) + 1);
            }

            OffsetDateTime existingCreatedAt = parseOffsetDateTime(group.get("created_at"));
            OffsetDateTime candidateCreatedAt = parseOffsetDateTime(flag.get("created_at"));
            if (candidateCreatedAt != null && (existingCreatedAt == null || candidateCreatedAt.isAfter(existingCreatedAt))) {
                group.put("id", flag.get("id"));
                group.put("status", flag.get("status"));
                group.put("created_at", flag.get("created_at"));
            }
        }

        return grouped.values().stream()
            .sorted(
                Comparator.comparing(
                    (Map<String, Object> group) -> parseOffsetDateTime(group.get("created_at")),
                    Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed()
            )
            .toList();
    }

    private Map<String, Object> createFlagGroup(Map<String, Object> flag) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("id", flag.get("id"));
        group.put("content_id", flag.get("content_id"));
        group.put("content", flag.get("content"));
        group.put("status", flag.get("status"));
        group.put("created_at", flag.get("created_at"));
        group.put("report_count", 0);
        group.put("notes_count", 0);
        group.put("reasons", new ArrayList<String>());
        return group;
    }

    private Map<String, Object> createFlagReport(Map<String, Object> flag, Map<String, Object> reporterProfile) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", flag.get("id"));
        report.put("reported_by", flag.get("reported_by"));
        report.put("reporter", buildReporterPayload(flag.get("reported_by"), reporterProfile));
        report.put("reason", flag.get("reason"));
        report.put("description", flag.get("description"));
        report.put("created_at", flag.get("created_at"));
        return report;
    }

    private Map<String, Object> buildReporterPayload(Object reportedBy, Map<String, Object> reporterProfile) {
        UUID userId = parseUuid(reportedBy);
        Map<String, Object> reporter = new LinkedHashMap<>();
        reporter.put("user_id", userId);
        reporter.put("display_name", normalizeNullableText(toStringOrNull(
            reporterProfile == null ? null : reporterProfile.get("display_name")
        )));
        reporter.put("avatar_url", normalizeNullableText(toStringOrNull(
            reporterProfile == null ? null : reporterProfile.get("avatar_url")
        )));
        return reporter;
    }

    private List<Map<String, Object>> fetchFlagReportRows(
        UUID contentId,
        String reporterQuery,
        int offset,
        int limit
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", "id,reported_by,reason,description,created_at");
        params.put("content_id", "eq." + contentId);
        params.put("status", "eq.pending");
        params.put("order", "created_at.desc");
        params.put("offset", String.valueOf(Math.max(offset, 0)));
        params.put("limit", String.valueOf(Math.max(limit, 1)));

        if (reporterQuery != null && !reporterQuery.isBlank()) {
            Set<UUID> userIds = searchProfileUserIds(reporterQuery);
            if (userIds.isEmpty()) {
                return List.of();
            }
            params.put("reported_by", "in.(" + joinUuids(userIds) + ")");
        }

        return supabaseAdminRestClient.getList("content_flags", buildQuery(params), MAP_LIST);
    }

    private Set<UUID> searchProfileUserIds(String reporterQuery) {
        String normalized = normalizeReporterQuery(reporterQuery);
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }

        List<Map<String, Object>> profiles = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of(
                "select", "user_id",
                "display_name", "ilike.*" + normalized + "*",
                "limit", "100"
            )),
            MAP_LIST
        );

        Set<UUID> userIds = new LinkedHashSet<>();
        UUID directUserId = parseUuid(normalized);
        if (directUserId != null) {
            userIds.add(directUserId);
        }
        for (Map<String, Object> profile : profiles) {
            UUID userId = parseUuid(profile.get("user_id"));
            if (userId != null) {
                userIds.add(userId);
            }
        }
        return userIds;
    }

    private Map<UUID, Map<String, Object>> fetchProfilesByUserId(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<Map<String, Object>> profiles = supabaseAdminRestClient.getList(
            "profiles",
            buildQuery(Map.of(
                "select", "user_id,display_name,avatar_url",
                "user_id", "in.(" + joinUuids(userIds) + ")"
            )),
            MAP_LIST
        );

        Map<UUID, Map<String, Object>> byUserId = new LinkedHashMap<>();
        for (Map<String, Object> profile : profiles) {
            UUID userId = parseUuid(profile.get("user_id"));
            if (userId == null) {
                continue;
            }
            byUserId.put(userId, profile);
        }
        return byUserId;
    }

    private Set<UUID> extractReportedUserIds(List<Map<String, Object>> rows) {
        Set<UUID> userIds = new HashSet<>();
        for (Map<String, Object> row : rows) {
            UUID userId = parseUuid(row.get("reported_by"));
            if (userId != null) {
                userIds.add(userId);
            }
        }
        return userIds;
    }

    private String joinUuids(Set<UUID> userIds) {
        return userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private List<Map<String, Object>> resolvePendingFlagsForContent(UUID contentId, UUID adminUserId) {
        OffsetDateTime resolvedAt = OffsetDateTime.now();
        return supabaseAdminRestClient.patchList(
            "content_flags",
            buildQuery(Map.of(
                "content_id", "eq." + contentId,
                "status", "eq.pending"
            )),
            Map.of(
                "status", "resolved",
                "resolved_by", adminUserId,
                "resolved_at", resolvedAt
            ),
            MAP_LIST
        );
    }

    private void attachFlagContentCreators(List<Map<String, Object>> flags) {
        if (flags == null || flags.isEmpty()) {
            return;
        }

        List<Map<String, Object>> contentItems = new ArrayList<>();
        for (Map<String, Object> flag : flags) {
            if (flag == null) {
                continue;
            }
            Object content = flag.get("content");
            if (content instanceof Map<?, ?> contentMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedContent = (Map<String, Object>) contentMap;
                contentItems.add(typedContent);
            }
        }

        if (!contentItems.isEmpty()) {
            contentCreatorEnrichmentService.enrichWithCreatorProfiles(contentItems);
        }
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

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private OffsetDateTime parseOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private String normalizeReporterQuery(String value) {
        String sanitized = sanitizeText(value, 80);
        if (sanitized == null) {
            return null;
        }
        String normalized = sanitized.startsWith("@") ? sanitized.substring(1) : sanitized;
        return normalized.isBlank() ? null : normalized;
    }

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
