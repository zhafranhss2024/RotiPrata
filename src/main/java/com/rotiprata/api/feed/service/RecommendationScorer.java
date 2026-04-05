package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkedLesson;
import com.rotiprata.api.feed.service.RecommendationSignals.LessonProgressSignal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScorer {
    // These weights deliberately prefer learning progression over generic popularity.
    private static final double FEED_UNSEEN_LESSON_BOOST = 42.0;
    private static final double EXPLORE_UNSEEN_LESSON_BOOST = 34.0;
    private static final double FEED_IN_PROGRESS_LESSON_BOOST = 30.0;
    private static final double EXPLORE_IN_PROGRESS_LESSON_BOOST = 24.0;
    private static final double COMPLETED_LESSON_PENALTY = -10.0;
    private static final double MASTERED_CONTENT_PENALTY = -20.0;
    private static final double POSITIVE_INTERACTION_REPEAT_PENALTY = -12.0;
    private static final double BROWSED_REPEAT_PENALTY = -8.0;
    private static final double IMPRESSION_REPEAT_PENALTY = -4.0;

    public ScoredRecommendation score(
        Map<String, Object> candidate,
        List<LinkedLesson> linkedLessons,
        RecommendationSignals signals,
        RecommendationSurface surface
    ) {
        UUID contentId = parseUuid(candidate.get("id"));
        OffsetDateTime createdAt = parseOffsetDateTime(candidate.get("created_at"));
        double score = 0.0;

        score += scoreLessonLinks(linkedLessons, signals, surface);
        score += scoreAffinity(candidate, signals);
        score += scoreSearchIntent(candidate, signals);
        score += scoreFreshness(createdAt, surface);
        score += scoreQuality(candidate);
        score += scoreRepeatPenalties(contentId, signals, surface);

        return new ScoredRecommendation(candidate, round(score), createdAt, contentId);
    }

    public Comparator<ScoredRecommendation> comparator() {
        return Comparator
            .comparingDouble(ScoredRecommendation::score).reversed()
            .thenComparing(ScoredRecommendation::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ScoredRecommendation::contentId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private double scoreLessonLinks(
        List<LinkedLesson> linkedLessons,
        RecommendationSignals signals,
        RecommendationSurface surface
    ) {
        if (linkedLessons == null || linkedLessons.isEmpty()) {
            return 0.0;
        }
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LinkedLesson linkedLesson : linkedLessons) {
            LessonProgressSignal progress = signals.lessonProgressByLessonId().get(linkedLesson.lessonId());
            double lessonScore = switch (normalizeStatus(progress == null ? null : progress.status())) {
                case "in_progress" -> surface == RecommendationSurface.FEED
                    ? FEED_IN_PROGRESS_LESSON_BOOST
                    : EXPLORE_IN_PROGRESS_LESSON_BOOST;
                case "completed" -> COMPLETED_LESSON_PENALTY;
                default -> surface == RecommendationSurface.FEED
                    ? FEED_UNSEEN_LESSON_BOOST
                    : EXPLORE_UNSEEN_LESSON_BOOST;
            };
            bestScore = Math.max(bestScore, lessonScore);
        }
        return bestScore == Double.NEGATIVE_INFINITY ? 0.0 : bestScore;
    }

    private double scoreAffinity(Map<String, Object> candidate, RecommendationSignals signals) {
        double score = 0.0;

        Object rawTags = candidate.get("tags");
        if (rawTags instanceof List<?> tags) {
            int tagScore = 0;
            for (Object tag : tags) {
                String normalizedTag = normalizeText(tag);
                if (normalizedTag != null) {
                    tagScore += signals.tagAffinity().getOrDefault(normalizedTag, 0);
                }
            }
            score += Math.min(18.0, tagScore * 1.5);
        }

        UUID categoryId = parseUuid(candidate.get("category_id"));
        if (categoryId != null) {
            score += Math.min(10.0, signals.categoryAffinity().getOrDefault(categoryId, 0) * 1.5);
        }

        UUID creatorId = parseUuid(candidate.get("creator_id"));
        if (creatorId != null) {
            score += Math.min(8.0, signals.creatorAffinity().getOrDefault(creatorId, 0) * 1.25);
        }
        return score;
    }

    private double scoreSearchIntent(Map<String, Object> candidate, RecommendationSignals signals) {
        String haystack = String.join(
            " ",
            safeString(candidate.get("title")),
            safeString(candidate.get("description")),
            safeString(candidate.get("learning_objective"))
        ).toLowerCase(Locale.ROOT);

        double score = 0.0;
        for (String term : signals.recentSearchTerms()) {
            if (haystack.contains(term)) {
                score += 2.5;
            }
        }
        return Math.min(8.0, score);
    }

    private double scoreFreshness(OffsetDateTime createdAt, RecommendationSurface surface) {
        if (createdAt == null) {
            return 0.0;
        }
        long ageDays = Math.max(0L, ChronoUnit.DAYS.between(createdAt, OffsetDateTime.now()));
        double base = surface == RecommendationSurface.FEED ? 10.0 : 12.0;
        return Math.max(0.0, base - (ageDays / 7.0));
    }

    private double scoreQuality(Map<String, Object> candidate) {
        double likes = numeric(candidate.get("likes_count"));
        double saves = numeric(candidate.get("saves_count"));
        double shares = numeric(candidate.get("shares_count"));
        double comments = numeric(candidate.get("comments_count"));
        double views = numeric(candidate.get("view_count"));
        double featuredBonus = Boolean.TRUE.equals(candidate.get("is_featured")) ? 4.0 : 0.0;

        double engagement = Math.log1p((likes * 2.0) + (saves * 3.0) + (shares * 4.0) + comments);
        double viewsScore = Math.log1p(views) * 1.5;
        return Math.min(14.0, engagement + viewsScore + featuredBonus);
    }

    private double scoreRepeatPenalties(UUID contentId, RecommendationSignals signals, RecommendationSurface surface) {
        if (contentId == null) {
            return 0.0;
        }
        double penalty = 0.0;
        if (signals.masteredContentIds().contains(contentId)) {
            penalty += MASTERED_CONTENT_PENALTY;
        }
        if (signals.likedContentIds().contains(contentId)
            || signals.savedContentIds().contains(contentId)
            || signals.sharedContentIds().contains(contentId)) {
            penalty += POSITIVE_INTERACTION_REPEAT_PENALTY;
        }
        if (signals.browsedContentIds().contains(contentId)) {
            penalty += BROWSED_REPEAT_PENALTY;
        }
        int impressionCount = signals.recentImpressionCounts().getOrDefault(contentId, 0);
        if (impressionCount > 0) {
            double multiplier = surface == RecommendationSurface.FEED ? 1.0 : 0.75;
            penalty += Math.min(12.0, impressionCount * IMPRESSION_REPEAT_PENALTY * multiplier);
        }
        return penalty;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return "not_started";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private double numeric(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (RuntimeException ex) {
            return 0.0;
        }
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

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return text.isBlank() ? null : text;
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    record ScoredRecommendation(Map<String, Object> item, double score, OffsetDateTime createdAt, UUID contentId) {}
}
