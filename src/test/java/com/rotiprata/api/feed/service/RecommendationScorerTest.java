package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationScorerTest {

    private RecommendationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new RecommendationScorer();
    }

    @Test
    void score_shouldBoostUnseenLessonContentAboveCompletedLessonContent() {
        UUID unseenLessonId = UUID.randomUUID();
        UUID completedLessonId = UUID.randomUUID();

        RecommendationSignals signals = new RecommendationSignals(
            Map.of(
                completedLessonId,
                new RecommendationSignals.LessonProgressSignal("completed", 100, OffsetDateTime.now())
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of()
        );

        var unseenScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(2)),
            List.of(new ContentLessonLinkService.LinkedLesson(unseenLessonId, "Unseen", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        );
        var completedScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(2)),
            List.of(new ContentLessonLinkService.LinkedLesson(completedLessonId, "Done", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        );

        assertTrue(unseenScore.score() > completedScore.score());
    }

    @Test
    void score_shouldBoostInProgressLessonContentAboveCompletedLessonContent() {
        UUID inProgressLessonId = UUID.randomUUID();
        UUID completedLessonId = UUID.randomUUID();

        RecommendationSignals signals = new RecommendationSignals(
            Map.of(
                inProgressLessonId,
                new RecommendationSignals.LessonProgressSignal("in_progress", 45, OffsetDateTime.now()),
                completedLessonId,
                new RecommendationSignals.LessonProgressSignal("completed", 100, OffsetDateTime.now())
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of()
        );

        var inProgressScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new ContentLessonLinkService.LinkedLesson(inProgressLessonId, "In Progress", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        );
        var completedScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new ContentLessonLinkService.LinkedLesson(completedLessonId, "Completed", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        );

        assertTrue(inProgressScore.score() > completedScore.score());
    }

    @Test
    void score_shouldPenalizeMasteredAndRepeatedContent() {
        UUID contentId = UUID.randomUUID();

        RecommendationSignals cleanSignals = new RecommendationSignals(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of("slang", 3),
            Map.of(),
            Map.of(),
            List.of("slang"),
            Map.of()
        );

        RecommendationSignals repeatedSignals = new RecommendationSignals(
            Map.of(),
            Set.of(contentId),
            Set.of(),
            Set.of(),
            Set.of(contentId),
            Set.of(contentId),
            Map.of("slang", 3),
            Map.of(),
            Map.of(),
            List.of("slang"),
            Map.of(contentId, 2)
        );

        var cleanScore = scorer.score(
            candidate(contentId, OffsetDateTime.now().minusHours(4)),
            List.of(),
            cleanSignals,
            RecommendationSurface.EXPLORE
        );
        var repeatedScore = scorer.score(
            candidate(contentId, OffsetDateTime.now().minusHours(4)),
            List.of(),
            repeatedSignals,
            RecommendationSurface.EXPLORE
        );

        assertTrue(cleanScore.score() > repeatedScore.score());
    }

    @Test
    void score_shouldRewardAffinityAndSearchIntentSignals() {
        UUID creatorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        RecommendationSignals boostedSignals = new RecommendationSignals(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of("slang", 6),
            Map.of(categoryId, 4),
            Map.of(creatorId, 3),
            List.of("slang", "clip"),
            Map.of()
        );

        Map<String, Object> candidate = candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(3));
        candidate.put("creator_id", creatorId.toString());
        candidate.put("category_id", categoryId.toString());

        double neutralScore = scorer.score(candidate, List.of(), emptySignals(), RecommendationSurface.EXPLORE).score();
        double boostedScore = scorer.score(candidate, List.of(), boostedSignals, RecommendationSurface.EXPLORE).score();

        assertTrue(boostedScore > neutralScore);
    }

    @Test
    void comparator_shouldBreakTiesByCreatedAtThenContentIdDescending() {
        OffsetDateTime newest = OffsetDateTime.now().minusHours(1);
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID oldestId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        List<RecommendationScorer.ScoredRecommendation> ranked = new ArrayList<>(List.of(
            new RecommendationScorer.ScoredRecommendation(candidate(lowerId, newest), 10.0, newest, lowerId),
            new RecommendationScorer.ScoredRecommendation(candidate(higherId, newest), 10.0, newest, higherId),
            new RecommendationScorer.ScoredRecommendation(candidate(oldestId, newest.minusHours(1)), 10.0, newest.minusHours(1), oldestId)
        ));

        ranked.sort(scorer.comparator());

        assertEquals(higherId, ranked.get(0).contentId());
        assertEquals(lowerId, ranked.get(1).contentId());
        assertTrue(ranked.get(1).createdAt().isAfter(ranked.get(2).createdAt()));
    }

    @Test
    void score_shouldHandleInvalidCandidateMetadataGracefully() {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("id", "not-a-uuid");
        candidate.put("creator_id", "still-not-a-uuid");
        candidate.put("category_id", "bad-category");
        candidate.put("title", "Noise");
        candidate.put("description", "No useful metadata");
        candidate.put("learning_objective", "Test");
        candidate.put("created_at", "bad-date");
        candidate.put("view_count", "not-a-number");
        candidate.put("likes_count", "not-a-number");
        candidate.put("saves_count", "not-a-number");
        candidate.put("shares_count", "not-a-number");
        candidate.put("comments_count", "not-a-number");
        candidate.put("is_featured", false);
        candidate.put("tags", Arrays.asList(" ", null));

        double score = scorer.score(candidate, List.of(), emptySignals(), RecommendationSurface.FEED).score();

        assertFalse(Double.isNaN(score));
    }

    private RecommendationSignals emptySignals() {
        return new RecommendationSignals(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            Map.of()
        );
    }

    private Map<String, Object> candidate(UUID contentId, OffsetDateTime createdAt) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("id", contentId.toString());
        candidate.put("creator_id", UUID.randomUUID().toString());
        candidate.put("category_id", UUID.randomUUID().toString());
        candidate.put("title", "Slang lesson clip");
        candidate.put("description", "A quick slang explainer");
        candidate.put("learning_objective", "Learn slang");
        candidate.put("created_at", createdAt.toString());
        candidate.put("view_count", 30);
        candidate.put("likes_count", 5);
        candidate.put("saves_count", 1);
        candidate.put("shares_count", 0);
        candidate.put("comments_count", 0);
        candidate.put("is_featured", false);
        candidate.put("tags", List.of("slang"));
        return candidate;
    }
}
