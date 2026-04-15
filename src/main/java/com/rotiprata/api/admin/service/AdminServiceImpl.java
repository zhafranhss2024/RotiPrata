package com.rotiprata.api.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.admin.dto.AdminContentUpdateRequest;
import com.rotiprata.api.admin.dto.AdminStatsResponse;
import com.rotiprata.api.admin.dto.AdminUserActivityStatsResponse;
import com.rotiprata.api.admin.dto.AdminUserBrowsingHistoryResponse;
import com.rotiprata.api.admin.dto.AdminUserCommentResponse;
import com.rotiprata.api.admin.dto.AdminUserDetailResponse;
import com.rotiprata.api.admin.dto.AdminUserLessonProgressResponse;
import com.rotiprata.api.admin.dto.AdminUserSearchHistoryResponse;
import com.rotiprata.api.admin.dto.AdminUserSummaryResponse;
import com.rotiprata.api.admin.service.AdminLoggingService.AdminAction;
import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.content.domain.ContentTag;
import com.rotiprata.api.content.service.ContentCreatorEnrichmentService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.feed.service.ContentLessonLinkService;
import com.rotiprata.api.user.domain.Profile;
import com.rotiprata.api.user.domain.UserRole;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Implements the admin service workflows and persistence coordination used by the API layer.
 */
@Service
public class AdminServiceImpl implements AdminService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Content>> CONTENT_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ContentTag>> TAG_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Profile>> PROFILE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<UserRole>> USER_ROLE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ChatbotMessageDTO>> CHAT_HISTORY_LIST = new TypeReference<>() {};
    private static final int MAX_TITLE = 80;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_OBJECTIVE = 160;
    private static final int MAX_LONG_TEXT = 500;
    private static final int MAX_OLDER_REFERENCE = 160;
    private static final int MAX_TAG = 30;
    private static final int FLAG_REPORTS_PAGE_SIZE = 5;
    private static final String ACTIVE_STATUS = "active";
    private static final String SUSPENDED_STATUS = "suspended";
    private static final String PERMANENT_BAN_DURATION = "876000h";

    private final SupabaseAdminClient supabaseAdminClient;
    private final SupabaseAdminRestClient supabaseAdminRestClient;
    private final ContentCreatorEnrichmentService contentCreatorEnrichmentService;
    private final ContentService contentService;
    private final ContentLessonLinkService contentLessonLinkService;
    private final UserService userService;
    private final AdminLoggingService adminLoggingService;

    /**
     * Creates a admin service impl instance with its collaborators.
     */
    @Autowired
    public AdminServiceImpl(
        SupabaseAdminClient supabaseAdminClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService,
        ContentService contentService,
        UserService userService,
        AdminLoggingService adminLoggingService
    ) {
        this(
            supabaseAdminClient,
            supabaseAdminRestClient,
            contentCreatorEnrichmentService,
            contentService,
            new ContentLessonLinkService(supabaseAdminRestClient),
            userService,
            adminLoggingService
        );
    }

    /**
     * Creates a admin service impl instance with its collaborators.
     */
    public AdminServiceImpl(
        SupabaseAdminClient supabaseAdminClient,
        SupabaseAdminRestClient supabaseAdminRestClient,
        ContentCreatorEnrichmentService contentCreatorEnrichmentService,
        ContentService contentService,
        ContentLessonLinkService contentLessonLinkService,
        UserService userService,
        AdminLoggingService adminLoggingService
    ) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.supabaseAdminRestClient = supabaseAdminRestClient;
        this.contentCreatorEnrichmentService = contentCreatorEnrichmentService;
        this.contentService = contentService;
        this.contentLessonLinkService = contentLessonLinkService;
        this.userService = userService;
        this.adminLoggingService = adminLoggingService;
    }

    /**
     * Returns the moderation queue.
     */
    @Override
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

    /**
     * Handles approve content.
     */
    @Override
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

        adminLoggingService.logAdminAction(adminUserId, AdminAction.APPROVE_CONTENT, contentId, AdminLoggingService.TargetType.CONTENT, "Approved content");
    }

    /**
     * Handles reject content.
     */
    @Override
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

        adminLoggingService.logAdminAction(adminUserId, AdminAction.REJECT_CONTENT, contentId, AdminLoggingService.TargetType.CONTENT, "Rejected content");
    }

    /**
     * Updates the content metadata.
     */
    @Override
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
        contentLessonLinkService.replaceContentLessonLinks(contentId, request.lessonIds());
        adminLoggingService.logAdminAction(adminUserId, AdminAction.UPDATE_CONTENT, contentId, AdminLoggingService.TargetType.CONTENT, "Updated content metadata");

        return updated.get(0);
    }

    /**
     * Returns the open flags.
     */
    @Override
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

    /**
     * Resolves the flag.
     */
    @Override
    public void resolveFlag(UUID adminUserId, UUID flagId, String accessToken) {
        requireAdmin(adminUserId, accessToken);
        Map<String, Object> flag = getPendingFlag(flagId);
        UUID contentId = parseUuid(flag.get("content_id"));
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag content not found");
        }
        resolvePendingFlagsForContent(contentId, adminUserId);

        adminLoggingService.logAdminAction(adminUserId, AdminAction.RESOLVE_FLAG, flagId, AdminLoggingService.TargetType.FLAG, "Resolved pending flag");
    }

    /**
     * Handles take down flag.
     */
    @Override
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
        adminLoggingService.logAdminAction(adminUserId, AdminAction.TAKE_DOWN_CONTENT, contentId, AdminLoggingService.TargetType.CONTENT, "Took down flagged content");
    }

    /**
     * Returns the flag reports.
     */
    @Override
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

    /**
     * Returns the flag review by content.
     */
    @Override
    public Map<String, Object> getFlagReviewByContent(
        UUID adminUserId,
        UUID contentId,
        Integer month,
        Integer year,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        FlagReviewPeriod period = normalizeFlagReviewPeriod(month, year);
        List<Map<String, Object>> allRows = fetchAllFlagRowsForContent(contentId, true);
        List<Map<String, Object>> scopedRows = filterFlagRowsForReview(allRows, period);
        if (scopedRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag review not found");
        }

        Map<String, Object> latestScopedRow = scopedRows.get(0);
        UUID actionableFlagId = findLatestPendingFlagId(scopedRows);

        Map<String, Object> review = new LinkedHashMap<>();
        review.put("contentId", contentId);
        review.put("content", latestScopedRow.get("content"));
        review.put(
            "status",
            actionableFlagId != null
                ? "pending"
                : normalizeNullableText(toStringOrNull(latestScopedRow.get("status")))
        );
        review.put("reportCount", scopedRows.size());
        review.put(
            "notesCount",
            Math.toIntExact(
                scopedRows.stream()
                    .filter(row -> normalizeNullableText(toStringOrNull(row.get("description"))) != null)
                    .count()
            )
        );
        review.put(
            "reasons",
            scopedRows.stream()
                .map(row -> normalizeNullableText(toStringOrNull(row.get("reason"))))
                .filter(Objects::nonNull)
                .distinct()
                .toList()
        );
        review.put("latestReportAt", latestScopedRow.get("created_at"));
        review.put("actionableFlagId", actionableFlagId == null ? null : actionableFlagId.toString());
        review.put("canResolve", actionableFlagId != null);
        review.put("canTakeDown", actionableFlagId != null);
        attachFlagContentCreators(List.of(review));
        return review;
    }

    /**
     * Returns the flag reports by content.
     */
    @Override
    public Map<String, Object> getFlagReportsByContent(
        UUID adminUserId,
        UUID contentId,
        int page,
        String reporterQuery,
        Integer month,
        Integer year,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        FlagReviewPeriod period = normalizeFlagReviewPeriod(month, year);
        int normalizedPage = Math.max(page, 1);
        int offset = (normalizedPage - 1) * FLAG_REPORTS_PAGE_SIZE;
        String normalizedReporterQuery = normalizeReporterQuery(reporterQuery);

        List<Map<String, Object>> allRows = fetchAllFlagRowsForContent(contentId, false);
        List<Map<String, Object>> scopedRows = filterFlagRowsForReview(allRows, period);
        if (scopedRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag review not found");
        }

        List<Map<String, Object>> filteredRows = filterFlagRowsByReporter(scopedRows, normalizedReporterQuery);
        boolean hasNext = filteredRows.size() > offset + FLAG_REPORTS_PAGE_SIZE;
        List<Map<String, Object>> pageRows = filteredRows.stream()
            .skip(offset)
            .limit(FLAG_REPORTS_PAGE_SIZE)
            .toList();
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

    /**
     * Returns the stats.
     */
    @Override
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

    /**
     * Returns the users.
     */
    @Override
    public List<AdminUserSummaryResponse> getUsers(UUID adminUserId, String searchQuery, String accessToken) {
        requireAdmin(adminUserId, accessToken);

        Map<UUID, Profile> profilesByUserId = fetchProfilesForUsers(null);
        Map<UUID, List<AppRole>> rolesByUserId = fetchRolesForUsers(null);
        String normalizedQuery = normalizeUserQuery(searchQuery);

        return supabaseAdminClient.listUsers().stream()
            .map(this::toAuthUserData)
            .filter(Objects::nonNull)
            .map(authUser -> buildUserSummary(authUser, profilesByUserId.get(authUser.userId()), rolesByUserId.get(authUser.userId())))
            .filter(summary -> matchesUserQuery(summary, normalizedQuery))
            .sorted(
                Comparator.comparing(
                    AdminUserSummaryResponse::lastActivityDate,
                    Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(
                    AdminUserSummaryResponse::createdAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
                )
            )
            .toList();
    }

    /**
     * Returns the user detail.
     */
    @Override
    public AdminUserDetailResponse getUserDetail(UUID adminUserId, UUID targetUserId, String accessToken) {
        String token = requireAdmin(adminUserId, accessToken);
        AuthUserData authUser = toAuthUserData(supabaseAdminClient.getUser(targetUserId));
        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Profile profile = fetchProfileByUserId(targetUserId);
        List<AppRole> roles = fetchRolesForUsers(Set.of(targetUserId)).getOrDefault(targetUserId, List.of(AppRole.USER));
        AdminUserSummaryResponse summary = buildUserSummary(authUser, profile, roles);

        List<Map<String, Object>> postedContent = contentService.getProfileContentCollection(targetUserId, token, "posted");
        List<Map<String, Object>> likedContent = contentService.getProfileContentCollection(targetUserId, token, "liked");
        List<Map<String, Object>> savedContent = contentService.getProfileContentCollection(targetUserId, token, "saved");
        List<AdminUserCommentResponse> comments = fetchUserComments(targetUserId);
        List<AdminUserLessonProgressResponse> lessonProgress = fetchUserLessonProgressDetails(targetUserId);
        List<com.rotiprata.api.user.dto.UserBadgeResponse> badges = userService.getUserBadges(targetUserId, token);
        List<AdminUserBrowsingHistoryResponse> browsingHistory = fetchUserBrowsingHistory(targetUserId);
        List<AdminUserSearchHistoryResponse> searchHistory = fetchUserSearchHistory(targetUserId);
        List<ChatbotMessageDTO> chatHistory = fetchUserChatHistory(targetUserId);

        int completedLessons = (int) lessonProgress.stream()
            .filter(progress -> "completed".equalsIgnoreCase(progress.status()) || progress.progressPercentage() >= 100)
            .count();
        int earnedBadges = (int) badges.stream().filter(com.rotiprata.api.user.dto.UserBadgeResponse::earned).count();

        AdminUserActivityStatsResponse activity = new AdminUserActivityStatsResponse(
            postedContent.size(),
            likedContent.size(),
            savedContent.size(),
            comments.size(),
            lessonProgress.size(),
            completedLessons,
            earnedBadges,
            browsingHistory.size(),
            searchHistory.size(),
            chatHistory.size()
        );

        return new AdminUserDetailResponse(
            summary,
            authUser.suspendedUntil(),
            activity,
            postedContent,
            likedContent,
            savedContent,
            comments,
            lessonProgress,
            badges,
            browsingHistory,
            searchHistory,
            chatHistory
        );
    }

    /**
     * Updates the user role.
     */
    @Override
    public AdminUserSummaryResponse updateUserRole(
        UUID adminUserId,
        UUID targetUserId,
        AppRole role,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        if (adminUserId.equals(targetUserId) && role != AppRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin role");
        }

        List<AppRole> existingRoles = fetchRolesForUsers(Set.of(targetUserId)).getOrDefault(targetUserId, List.of());
        long adminCount = fetchRolesForUsers(null).values().stream()
            .flatMap(List::stream)
            .filter(existingRole -> existingRole == AppRole.ADMIN)
            .count();
        if (existingRoles.contains(AppRole.ADMIN) && role != AppRole.ADMIN && adminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one admin is required");
        }

        supabaseAdminRestClient.deleteList(
            "user_roles",
            buildQuery(Map.of("user_id", "eq." + targetUserId)),
            USER_ROLE_LIST
        );

        Map<String, Object> insert = new LinkedHashMap<>();
        insert.put("user_id", targetUserId);
        insert.put("role", role.toJson());
        insert.put("assigned_at", OffsetDateTime.now());
        insert.put("assigned_by", adminUserId);
        supabaseAdminRestClient.postList("user_roles", insert, USER_ROLE_LIST);

        adminLoggingService.logAdminAction(adminUserId, AdminAction.UPDATE_USER_ROLE, targetUserId, AdminLoggingService.TargetType.USER, "Updated user role to " + role.name());

        return loadUserSummary(targetUserId);
    }

    /**
     * Updates the user status.
     */
    @Override
    public AdminUserSummaryResponse updateUserStatus(
        UUID adminUserId,
        UUID targetUserId,
        String status,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);
        String normalizedStatus = normalizeAccountStatus(status);
        if (adminUserId.equals(targetUserId) && SUSPENDED_STATUS.equals(normalizedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot suspend your own account");
        }

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("ban_duration", SUSPENDED_STATUS.equals(normalizedStatus) ? PERMANENT_BAN_DURATION : "none");
        supabaseAdminClient.updateUser(targetUserId, update);

        adminLoggingService.logAdminAction(adminUserId, AdminAction.UPDATE_USER_STATUS, targetUserId, AdminLoggingService.TargetType.USER, "Updated user status to " + normalizedStatus);

        return loadUserSummary(targetUserId);
    }

    /**
     * Handles reset user lesson progress.
     */
    @Override
    public void resetUserLessonProgress(
        UUID adminUserId,
        UUID targetUserId,
        UUID lessonId,
        String accessToken
    ) {
        requireAdmin(adminUserId, accessToken);

        List<Map<String, Object>> rewards = supabaseAdminRestClient.getList(
            "user_lesson_rewards",
            buildQuery(Map.of(
                "select", "xp_awarded,badge_name",
                "user_id", "eq." + targetUserId,
                "lesson_id", "eq." + lessonId
            )),
            MAP_LIST
        );

        supabaseAdminRestClient.deleteList(
            "user_lesson_progress",
            buildQuery(Map.of(
                "user_id", "eq." + targetUserId,
                "lesson_id", "eq." + lessonId
            )),
            MAP_LIST
        );
        supabaseAdminRestClient.deleteList(
            "user_lesson_quiz_attempts",
            buildQuery(Map.of(
                "user_id", "eq." + targetUserId,
                "lesson_id", "eq." + lessonId
            )),
            MAP_LIST
        );
        supabaseAdminRestClient.deleteList(
            "user_lesson_rewards",
            buildQuery(Map.of(
                "user_id", "eq." + targetUserId,
                "lesson_id", "eq." + lessonId
            )),
            MAP_LIST
        );

        List<Map<String, Object>> quizzes = supabaseAdminRestClient.getList(
            "quizzes",
            buildQuery(Map.of(
                "select", "id",
                "lesson_id", "eq." + lessonId
            )),
            MAP_LIST
        );
        Set<String> quizIds = quizzes.stream()
            .map(row -> toStringOrNull(row.get("id")))
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!quizIds.isEmpty()) {
            supabaseAdminRestClient.deleteList(
                "user_quiz_results",
                buildQuery(Map.of(
                    "user_id", "eq." + targetUserId,
                    "quiz_id", "in.(" + String.join(",", quizIds) + ")"
                )),
                MAP_LIST
            );
        }

        int xpToRemove = rewards.stream()
            .map(row -> parseInteger(row.get("xp_awarded")))
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        if (xpToRemove > 0) {
            List<Profile> profiles = supabaseAdminRestClient.getList(
                "profiles",
                buildQuery(Map.of(
                    "user_id", "eq." + targetUserId,
                    "select", "id,reputation_points"
                )),
                PROFILE_LIST
            );
            if (!profiles.isEmpty()) {
                Profile profile = profiles.get(0);
                int currentXp = profile.getReputationPoints() == null ? 0 : profile.getReputationPoints();
                supabaseAdminRestClient.patchList(
                    "profiles",
                    buildQuery(Map.of("user_id", "eq." + targetUserId)),
                    Map.of(
                        "reputation_points", Math.max(0, currentXp - xpToRemove),
                        "updated_at", OffsetDateTime.now()
                    ),
                    PROFILE_LIST
                );
            }
        }

        if (!rewards.isEmpty()) {
            decrementLessonCompletionCount(lessonId);
        }

        Set<String> badgeNames = rewards.stream()
            .map(row -> normalizeNullableText(toStringOrNull(row.get("badge_name"))))
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String badgeName : badgeNames) {
            if (userStillHasBadgeReward(targetUserId, badgeName)) {
                continue;
            }
            supabaseAdminRestClient.deleteList(
                "user_achievements",
                buildQuery(Map.of(
                    "user_id", "eq." + targetUserId,
                    "achievement_type", "eq.lesson_badge",
                    "achievement_name", "eq." + badgeName
                )),
                MAP_LIST
            );
        }

        adminLoggingService.logAdminAction(
            adminUserId,
            AdminAction.RESET_USER_LESSON_PROGRESS,
            targetUserId,
            AdminLoggingService.TargetType.USER,
            "Reset lesson progress for lesson " + lessonId
        );
    }

    /**
     * Handles count.
     */
    // Private Helps 

    private int count(String table, String query) {
        return supabaseAdminRestClient.getList(table, query, MAP_LIST).size();
    }

    /**
     * Fetches the profiles for users.
     */
    private Map<UUID, Profile> fetchProfilesForUsers(Set<UUID> userIds) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(
            "select",
            "user_id,display_name,avatar_url,reputation_points,current_streak,longest_streak,last_activity_date,total_hours_learned"
        );
        if (userIds != null && !userIds.isEmpty()) {
            params.put("user_id", "in.(" + joinUuids(userIds) + ")");
        }
        List<Profile> profiles = supabaseAdminRestClient.getList("profiles", buildQuery(params), PROFILE_LIST);
        Map<UUID, Profile> byUserId = new LinkedHashMap<>();
        for (Profile profile : profiles) {
            if (profile != null && profile.getUserId() != null) {
                byUserId.put(profile.getUserId(), profile);
            }
        }
        return byUserId;
    }

    /**
     * Fetches the profile by user id.
     */
    private Profile fetchProfileByUserId(UUID userId) {
        return fetchProfilesForUsers(Set.of(userId)).get(userId);
    }

    /**
     * Fetches the roles for users.
     */
    private Map<UUID, List<AppRole>> fetchRolesForUsers(Set<UUID> userIds) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("select", "user_id,role");
        if (userIds != null && !userIds.isEmpty()) {
            params.put("user_id", "in.(" + joinUuids(userIds) + ")");
        }
        List<UserRole> roles = supabaseAdminRestClient.getList("user_roles", buildQuery(params), USER_ROLE_LIST);
        Map<UUID, List<AppRole>> byUserId = new LinkedHashMap<>();
        for (UserRole userRole : roles) {
            if (userRole == null || userRole.getUserId() == null || userRole.getRole() == null) {
                continue;
            }
            byUserId.computeIfAbsent(userRole.getUserId(), ignored -> new ArrayList<>()).add(userRole.getRole());
        }
        byUserId.replaceAll((ignored, value) -> value.stream().distinct().toList());
        return byUserId;
    }

    /**
     * Loads the user summary.
     */
    private AdminUserSummaryResponse loadUserSummary(UUID userId) {
        AuthUserData authUser = toAuthUserData(supabaseAdminClient.getUser(userId));
        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        Profile profile = fetchProfileByUserId(userId);
        List<AppRole> roles = fetchRolesForUsers(Set.of(userId)).getOrDefault(userId, List.of(AppRole.USER));
        return buildUserSummary(authUser, profile, roles);
    }

    /**
     * Converts the value into auth user data.
     */
    private AuthUserData toAuthUserData(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if ((node.get("id") == null || node.get("id").isNull()) && node.get("user") != null && !node.get("user").isNull()) {
            node = node.get("user");
        }
        UUID userId = parseUuid(jsonText(node, "id"));
        if (userId == null) {
            return null;
        }
        return new AuthUserData(
            userId,
            normalizeNullableText(jsonText(node, "email")),
            parseOffsetDateTime(jsonText(node, "created_at")),
            parseOffsetDateTime(jsonText(node, "last_sign_in_at")),
            parseOffsetDateTime(jsonText(node, "banned_until"))
        );
    }

    /**
     * Builds the user summary.
     */
    private AdminUserSummaryResponse buildUserSummary(AuthUserData authUser, Profile profile, List<AppRole> roles) {
        List<AppRole> normalizedRoles = roles == null || roles.isEmpty() ? List.of(AppRole.USER) : roles;
        String fallbackName = authUser.email() != null && authUser.email().contains("@")
            ? authUser.email().substring(0, authUser.email().indexOf('@'))
            : authUser.userId().toString().substring(0, 8);
        String displayName = profile == null ? fallbackName : normalizeNullableText(profile.getDisplayName());
        if (displayName == null || displayName.isBlank()) {
            displayName = fallbackName;
        }
        return new AdminUserSummaryResponse(
            authUser.userId(),
            displayName,
            authUser.email(),
            profile == null ? null : profile.getAvatarUrl(),
            profile == null || profile.getReputationPoints() == null ? 0 : profile.getReputationPoints(),
            profile == null || profile.getCurrentStreak() == null ? 0 : profile.getCurrentStreak(),
            profile == null || profile.getLongestStreak() == null ? 0 : profile.getLongestStreak(),
            profile == null ? null : profile.getLastActivityDate(),
            profile == null || profile.getTotalHoursLearned() == null ? 0 : profile.getTotalHoursLearned(),
            normalizedRoles,
            isSuspended(authUser.suspendedUntil()) ? SUSPENDED_STATUS : ACTIVE_STATUS,
            authUser.createdAt(),
            authUser.lastSignInAt()
        );
    }

    /**
     * Handles matches user query.
     */
    private boolean matchesUserQuery(AdminUserSummaryResponse summary, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        return containsIgnoreCase(summary.displayName(), normalizedQuery)
            || containsIgnoreCase(summary.email(), normalizedQuery)
            || containsIgnoreCase(summary.userId() == null ? null : summary.userId().toString(), normalizedQuery);
    }

    /**
     * Handles contains ignore case.
     */
    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    /**
     * Normalizes the user query.
     */
    private String normalizeUserQuery(String value) {
        String sanitized = sanitizeText(value, 120);
        return sanitized == null ? null : sanitized.toLowerCase();
    }

    /**
     * Normalizes the account status.
     */
    private String normalizeAccountStatus(String value) {
        String normalized = sanitizeText(value, 40);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        String lowered = normalized.toLowerCase();
        if (!ACTIVE_STATUS.equals(lowered) && !SUSPENDED_STATUS.equals(lowered)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported account status");
        }
        return lowered;
    }

    /**
     * Checks whether suspended.
     */
    private boolean isSuspended(OffsetDateTime suspendedUntil) {
        return suspendedUntil != null && suspendedUntil.isAfter(OffsetDateTime.now());
    }

    /**
     * Fetches the user comments.
     */
    private List<AdminUserCommentResponse> fetchUserComments(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content_comments",
            buildQuery(Map.of(
                "select", "id,content_id,body,created_at,updated_at",
                "user_id", "eq." + userId,
                "is_deleted", "eq.false",
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
        Set<UUID> contentIds = rows.stream()
            .map(row -> parseUuid(row.get("content_id")))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> contentTitles = fetchContentTitles(contentIds);
        Profile profile = fetchProfileByUserId(userId);
        String author = profile == null ? "anonymous" : normalizeNullableText(profile.getDisplayName());
        if (author == null || author.isBlank()) {
            author = "anonymous";
        }
        final String commentAuthor = author;
        return rows.stream()
            .map(row -> {
                UUID contentId = parseUuid(row.get("content_id"));
                return new AdminUserCommentResponse(
                    parseUuid(row.get("id")),
                    contentId,
                    contentTitles.get(contentId),
                    normalizeNullableText(toStringOrNull(row.get("body"))),
                    commentAuthor,
                    parseOffsetDateTime(row.get("created_at")),
                    parseOffsetDateTime(row.get("updated_at"))
                );
            })
            .toList();
    }

    /**
     * Fetches the content titles.
     */
    private Map<UUID, String> fetchContentTitles(Set<UUID> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "content",
            buildQuery(Map.of(
                "select", "id,title",
                "id", "in.(" + joinUuids(contentIds) + ")"
            )),
            MAP_LIST
        );
        Map<UUID, String> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID contentId = parseUuid(row.get("id"));
            if (contentId != null) {
                byId.put(contentId, normalizeNullableText(toStringOrNull(row.get("title"))));
            }
        }
        return byId;
    }

    /**
     * Fetches the user lesson progress details.
     */
    private List<AdminUserLessonProgressResponse> fetchUserLessonProgressDetails(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "user_lesson_progress",
            buildQuery(Map.of(
                "select",
                "id,lesson_id,status,progress_percentage,current_section,started_at,completed_at,last_accessed_at,created_at",
                "user_id", "eq." + userId,
                "order", "last_accessed_at.desc,created_at.desc"
            )),
            MAP_LIST
        );

        Map<String, Map<String, Object>> latestByLessonId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String lessonId = toStringOrNull(row.get("lesson_id"));
            if (lessonId == null || latestByLessonId.containsKey(lessonId)) {
                continue;
            }
            latestByLessonId.put(lessonId, row);
        }

        Set<UUID> lessonIds = latestByLessonId.keySet().stream()
            .map(this::parseUuid)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> lessonTitles = fetchLessonTitles(lessonIds);

        return latestByLessonId.values().stream()
            .map(row -> {
                UUID lessonId = parseUuid(row.get("lesson_id"));
                return new AdminUserLessonProgressResponse(
                    toStringOrNull(row.get("id")),
                    lessonId,
                    lessonTitles.get(lessonId),
                    normalizeNullableText(toStringOrNull(row.get("status"))),
                    toInt(row.get("progress_percentage")),
                    normalizeNullableText(toStringOrNull(row.get("current_section"))),
                    parseOffsetDateTime(row.get("started_at")),
                    parseOffsetDateTime(row.get("completed_at")),
                    parseOffsetDateTime(row.get("last_accessed_at"))
                );
            })
            .toList();
    }

    /**
     * Fetches the lesson titles.
     */
    private Map<UUID, String> fetchLessonTitles(Set<UUID> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id,title",
                "id", "in.(" + joinUuids(lessonIds) + ")"
            )),
            MAP_LIST
        );
        Map<UUID, String> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID lessonId = parseUuid(row.get("id"));
            if (lessonId != null) {
                byId.put(lessonId, normalizeNullableText(toStringOrNull(row.get("title"))));
            }
        }
        return byId;
    }

    /**
     * Fetches the user search history.
     */
    private List<AdminUserSearchHistoryResponse> fetchUserSearchHistory(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "search_history",
            buildQuery(Map.of(
                "select", "id,query,searched_at",
                "user_id", "eq." + userId,
                "order", "searched_at.desc",
                "limit", "50"
            )),
            MAP_LIST
        );
        return rows.stream()
            .map(row -> new AdminUserSearchHistoryResponse(
                toStringOrNull(row.get("id")),
                normalizeNullableText(toStringOrNull(row.get("query"))),
                parseLocalDateTime(row.get("searched_at"))
            ))
            .toList();
    }

    /**
     * Fetches the user browsing history.
     */
    private List<AdminUserBrowsingHistoryResponse> fetchUserBrowsingHistory(UUID userId) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "browsing_history",
            buildQuery(Map.of(
                "select", "id,content_id,lesson_id,item_id,title,viewed_at",
                "user_id", "eq." + userId,
                "order", "viewed_at.desc",
                "limit", "50"
            )),
            MAP_LIST
        );
        return rows.stream()
            .map(row -> new AdminUserBrowsingHistoryResponse(
                parseUuid(row.get("id")),
                parseUuid(row.get("content_id")),
                parseUuid(row.get("lesson_id")),
                parseUuid(row.get("item_id")),
                normalizeNullableText(toStringOrNull(row.get("title"))),
                parseOffsetDateTime(row.get("viewed_at"))
            ))
            .toList();
    }

    /**
     * Handles user still has badge reward.
     */
    private boolean userStillHasBadgeReward(UUID userId, String badgeName) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "user_lesson_rewards",
            buildQuery(Map.of(
                "select", "id",
                "user_id", "eq." + userId,
                "badge_name", "eq." + badgeName,
                "limit", "1"
            )),
            MAP_LIST
        );
        return !rows.isEmpty();
    }

    /**
     * Handles decrement lesson completion count.
     */
    private void decrementLessonCompletionCount(UUID lessonId) {
        List<Map<String, Object>> lessons = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id,completion_count",
                "id", "eq." + lessonId,
                "limit", "1"
            )),
            MAP_LIST
        );
        if (lessons.isEmpty()) {
            return;
        }
        int currentCount = Math.max(0, toInt(lessons.get(0).get("completion_count")));
        supabaseAdminRestClient.patchList(
            "lessons",
            buildQuery(Map.of("id", "eq." + lessonId)),
            Map.of(
                "completion_count", Math.max(0, currentCount - 1),
                "updated_at", OffsetDateTime.now()
            ),
            MAP_LIST
        );
    }

    /**
     * Fetches the user chat history.
     */
    private List<ChatbotMessageDTO> fetchUserChatHistory(UUID userId) {
        return supabaseAdminRestClient.getList(
            "user_chatbot_history",
            buildQuery(Map.of(
                "user_id", "eq." + userId,
                "order", "timestamp.asc",
                "limit", "100"
            )),
            CHAT_HISTORY_LIST
        );
    }

    /**
     * Returns the pending flag.
     */
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

    /**
     * Fetches the all flag rows for content.
     */
    private List<Map<String, Object>> fetchAllFlagRowsForContent(UUID contentId, boolean includeContent) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(
            "select",
            includeContent
                ? "id,content_id,status,reported_by,reason,description,created_at,content:content_id(*,content_tags(tag))"
                : "id,content_id,status,reported_by,reason,description,created_at"
        );
        params.put("content_id", "eq." + contentId);
        params.put("order", "created_at.desc");
        return supabaseAdminRestClient.getList("content_flags", buildQuery(params), MAP_LIST);
    }

    /**
     * Handles group open flags.
     */
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

    /**
     * Creates the flag group.
     */
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

    /**
     * Creates the flag report.
     */
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

    /**
     * Builds the reporter payload.
     */
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

    /**
     * Fetches the flag report rows.
     */
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

    /**
     * Handles filter flag rows for review.
     */
    private List<Map<String, Object>> filterFlagRowsForReview(
        List<Map<String, Object>> rows,
        FlagReviewPeriod period
    ) {
        return rows.stream()
            .filter(row -> {
                if (period != null) {
                    return matchesFlagReviewPeriod(row, period);
                }
                return "pending".equalsIgnoreCase(toStringOrNull(row.get("status")));
            })
            .toList();
    }

    /**
     * Handles matches flag review period.
     */
    private boolean matchesFlagReviewPeriod(Map<String, Object> row, FlagReviewPeriod period) {
        OffsetDateTime createdAt = parseOffsetDateTime(row.get("created_at"));
        return createdAt != null
            && createdAt.getYear() == period.year()
            && createdAt.getMonthValue() == period.month();
    }

    /**
     * Handles filter flag rows by reporter.
     */
    private List<Map<String, Object>> filterFlagRowsByReporter(
        List<Map<String, Object>> rows,
        String normalizedReporterQuery
    ) {
        if (normalizedReporterQuery == null || normalizedReporterQuery.isBlank()) {
            return rows;
        }
        Set<UUID> userIds = searchProfileUserIds(normalizedReporterQuery);
        if (userIds.isEmpty()) {
            return List.of();
        }
        return rows.stream()
            .filter(row -> {
                UUID reportedBy = parseUuid(row.get("reported_by"));
                return reportedBy != null && userIds.contains(reportedBy);
            })
            .toList();
    }

    /**
     * Finds the latest pending flag id.
     */
    private UUID findLatestPendingFlagId(List<Map<String, Object>> rows) {
        return rows.stream()
            .filter(row -> "pending".equalsIgnoreCase(toStringOrNull(row.get("status"))))
            .map(row -> parseUuid(row.get("id")))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    /**
     * Searches the profile user ids.
     */
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

    /**
     * Fetches the profiles by user id.
     */
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

    /**
     * Extracts the reported user ids.
     */
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

    /**
     * Handles join uuids.
     */
    private String joinUuids(Set<UUID> userIds) {
        return userIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    /**
     * Resolves the pending flags for content.
     */
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

    /**
     * Attaches the flag content creators.
     */
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

    /**
     * Patches the content review.
     */
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

    /**
     * Requires the admin.
     */
    private String requireAdmin(UUID userId, String accessToken) {
        String token = requireAccessToken(accessToken);
        List<AppRole> roles = userService.getRoles(userId, token);
        if (!roles.contains(AppRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return token;
    }

    /**
     * Requires the access token.
     */
    private String requireAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing access token");
        }
        return accessToken;
    }

    /**
     * Builds the query.
     */
    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    /**
     * Replaces the tags.
     */
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

    /**
     * Handles admin rest client delete tags.
     */
    private void adminRestClientDeleteTags(UUID contentId) {
        supabaseAdminRestClient.deleteList(
            "content_tags",
            buildQuery(Map.of("content_id", "eq." + contentId)),
            TAG_LIST
        );
    }

    /**
     * Handles sanitize required.
     */
    private String sanitizeRequired(String value, int maxLength, String fieldName) {
        String sanitized = sanitizeText(value, maxLength);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return sanitized;
    }

    /**
     * Handles sanitize tag.
     */
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

    /**
     * Handles sanitize text.
     */
    private String sanitizeText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String collapsed = value.replaceAll("\\s+", " ").trim();
        if (maxLength > 0 && collapsed.length() > maxLength) {
            return collapsed.substring(0, maxLength);
        }
        return collapsed;
    }
    
    /**
     * Parses the uuid.
     */
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

    /**
     * Parses the offset date time.
     */
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

    /**
     * Converts the value into int.
     */
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

    /**
     * Normalizes the reporter query.
     */
    private String normalizeReporterQuery(String value) {
        String sanitized = sanitizeText(value, 80);
        if (sanitized == null) {
            return null;
        }
        String normalized = sanitized.startsWith("@") ? sanitized.substring(1) : sanitized;
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * Converts the value into string or null.
     */
    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Normalizes the nullable text.
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Parses the integer.
     */
    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Parses the local date time.
     */
    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Handles json text.
     */
    private String jsonText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String text = field.asText();
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * Normalizes the flag review period.
     */
    private FlagReviewPeriod normalizeFlagReviewPeriod(Integer month, Integer year) {
        if (month == null && year == null) {
            return null;
        }
        if (month == null || year == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month and year are required together");
        }
        if (month < 1 || month > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must be between 1 and 12");
        }
        return new FlagReviewPeriod(month, year);
    }

    private record AuthUserData(
        UUID userId,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime lastSignInAt,
        OffsetDateTime suspendedUntil
    ) {}

    private record FlagReviewPeriod(
        int month,
        int year
    ) {}
}
