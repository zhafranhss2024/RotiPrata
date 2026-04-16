package com.rotiprata.api.feed.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.ArrayList;
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

/**
 * Implements the content lesson link workflows and persistence coordination used by the API layer.
 */
@Service
public class ContentLessonLinkServiceImpl implements ContentLessonLinkService {
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseAdminRestClient;

    public ContentLessonLinkServiceImpl(SupabaseAdminRestClient supabaseAdminRestClient) {
        this.supabaseAdminRestClient = supabaseAdminRestClient;
    }

    public Map<UUID, List<LinkedLesson>> resolveLinkedLessons(Set<UUID> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, List<LessonLinkRow>> directLinks = fetchLessonConceptLinks(contentIds);
        Map<UUID, List<LinkedLesson>> resolved = new LinkedHashMap<>(hydrateLinks(directLinks));

        Set<UUID> remainingContentIds = new LinkedHashSet<>(contentIds);
        remainingContentIds.removeAll(resolved.keySet());
        if (remainingContentIds.isEmpty()) {
            return resolved;
        }

        // lesson_concepts is the canonical source; quiz linkage only fills genuine gaps.
        Map<UUID, List<LessonLinkRow>> fallbackLinks = fetchQuizFallbackLinks(remainingContentIds);
        if (!fallbackLinks.isEmpty()) {
            resolved.putAll(hydrateLinks(fallbackLinks));
        }
        return resolved;
    }

    public void replaceContentLessonLinks(UUID contentId, List<UUID> lessonIds) {
        if (contentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content id is required");
        }
        if (lessonIds == null) {
            return;
        }

        List<UUID> normalizedLessonIds = lessonIds.stream()
            .filter(id -> id != null)
            .distinct()
            .toList();

        validateLessonsExist(normalizedLessonIds);

        supabaseAdminRestClient.deleteList(
            "lesson_concepts",
            buildQuery(Map.of("content_id", "eq." + contentId)),
            MAP_LIST
        );

        if (normalizedLessonIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < normalizedLessonIds.size(); index++) {
            UUID lessonId = normalizedLessonIds.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("content_id", contentId);
            row.put("lesson_id", lessonId);
            row.put("order_index", index);
            rows.add(row);
        }
        supabaseAdminRestClient.postList("lesson_concepts", rows, MAP_LIST);
    }

    private Map<UUID, List<LessonLinkRow>> fetchLessonConceptLinks(Set<UUID> contentIds) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "lesson_concepts",
            buildQuery(Map.of(
                "select", "content_id,lesson_id,order_index",
                "content_id", "in.(" + joinUuids(contentIds) + ")"
            )),
            MAP_LIST
        );
        return groupRows(rows, LinkSource.LESSON_CONCEPT);
    }

    private Map<UUID, List<LessonLinkRow>> fetchQuizFallbackLinks(Set<UUID> contentIds) {
        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "quizzes",
            buildQuery(Map.of(
                "select", "content_id,lesson_id,created_at",
                "content_id", "in.(" + joinUuids(contentIds) + ")",
                "lesson_id", "not.is.null",
                "is_active", "eq.true",
                "order", "created_at.desc"
            )),
            MAP_LIST
        );
        return groupRows(rows, LinkSource.QUIZ_FALLBACK);
    }

    private Map<UUID, List<LessonLinkRow>> groupRows(List<Map<String, Object>> rows, LinkSource source) {
        Map<UUID, List<LessonLinkRow>> linksByContentId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            UUID contentId = parseUuid(row.get("content_id"));
            UUID lessonId = parseUuid(row.get("lesson_id"));
            if (contentId == null || lessonId == null) {
                continue;
            }
            int orderIndex = parseInt(row.get("order_index"), 0);
            linksByContentId.computeIfAbsent(contentId, ignored -> new ArrayList<>())
                .add(new LessonLinkRow(contentId, lessonId, orderIndex, source));
        }
        return linksByContentId;
    }

    private Map<UUID, List<LinkedLesson>> hydrateLinks(Map<UUID, List<LessonLinkRow>> rowsByContentId) {
        if (rowsByContentId.isEmpty()) {
            return Map.of();
        }

        Set<UUID> lessonIds = rowsByContentId.values().stream()
            .flatMap(List::stream)
            .map(LessonLinkRow::lessonId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Map<String, Object>> lessons = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id,title,category_id,is_published,is_active",
                "id", "in.(" + joinUuids(lessonIds) + ")"
            )),
            MAP_LIST
        );

        Map<UUID, Map<String, Object>> lessonById = new LinkedHashMap<>();
        for (Map<String, Object> lesson : lessons) {
            UUID lessonId = parseUuid(lesson.get("id"));
            if (lessonId == null) {
                continue;
            }
            boolean isPublished = Boolean.TRUE.equals(lesson.get("is_published"));
            boolean isActive = !Boolean.FALSE.equals(lesson.get("is_active"));
            if (!isPublished || !isActive) {
                continue;
            }
            lessonById.put(lessonId, lesson);
        }

        Map<UUID, List<LinkedLesson>> linkedLessonsByContent = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<LessonLinkRow>> entry : rowsByContentId.entrySet()) {
            List<LinkedLesson> linkedLessons = entry.getValue().stream()
                .sorted((left, right) -> Integer.compare(left.orderIndex(), right.orderIndex()))
                .map(row -> toLinkedLesson(row, lessonById.get(row.lessonId())))
                .filter(link -> link != null)
                .toList();
            if (!linkedLessons.isEmpty()) {
                linkedLessonsByContent.put(entry.getKey(), linkedLessons);
            }
        }
        return linkedLessonsByContent;
    }

    private LinkedLesson toLinkedLesson(LessonLinkRow row, Map<String, Object> lesson) {
        if (row == null || lesson == null) {
            return null;
        }
        return new LinkedLesson(
            row.lessonId(),
            stringValue(lesson.get("title")),
            parseUuid(lesson.get("category_id")),
            row.source()
        );
    }

    private void validateLessonsExist(List<UUID> lessonIds) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> rows = supabaseAdminRestClient.getList(
            "lessons",
            buildQuery(Map.of(
                "select", "id",
                "id", "in.(" + joinUuids(new LinkedHashSet<>(lessonIds)) + ")"
            )),
            MAP_LIST
        );
        Set<UUID> existingIds = rows.stream()
            .map(row -> parseUuid(row.get("id")))
            .filter(id -> id != null)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UUID> missingIds = lessonIds.stream()
            .filter(id -> !existingIds.contains(id))
            .toList();
        if (!missingIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown lesson ids: " + missingIds);
        }
    }

    private String buildQuery(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        params.forEach(builder::queryParam);
        String uri = builder.build().encode().toUriString();
        return uri.startsWith("?") ? uri.substring(1) : uri;
    }

    private String joinUuids(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
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

    private int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
    private record LessonLinkRow(UUID contentId, UUID lessonId, int orderIndex, LinkSource source) {}
}
