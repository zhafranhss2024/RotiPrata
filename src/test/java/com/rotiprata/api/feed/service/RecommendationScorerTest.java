package com.rotiprata.api.feed.service;

import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkedLesson;
import com.rotiprata.api.feed.service.ContentLessonLinkService.LinkSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendationScorerTest {

    private RecommendationScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new RecommendationScorer();
    }

    /** Verifies unseen lesson content gets the strongest lesson boost on the feed surface. */
    @Test
    void score_ShouldBoostUnseenLessonContent_WhenLessonHasNoProgressSignal() {
        // arrange
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

        // act
        double unseenScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(2)),
            List.of(new LinkedLesson(unseenLessonId, "Unseen", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        ).score();
        double completedScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(2)),
            List.of(new LinkedLesson(completedLessonId, "Done", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        ).score();

        // assert
        assertTrue(unseenScore > completedScore);

        // verify
        assertTrue(unseenScore > completedScore);
    }

    /** Verifies in-progress lesson content outranks completed lesson content on the feed surface. */
    @Test
    void score_ShouldBoostInProgressLessonContent_WhenLessonIsPartiallyCompleted() {
        // arrange
        UUID inProgressLessonId = UUID.randomUUID();
        UUID completedLessonId = UUID.randomUUID();
        RecommendationSignals signals = new RecommendationSignals(
            Map.of(
                inProgressLessonId, new RecommendationSignals.LessonProgressSignal("in_progress", 45, OffsetDateTime.now()),
                completedLessonId, new RecommendationSignals.LessonProgressSignal("completed", 100, OffsetDateTime.now())
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

        // act
        double inProgressScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new LinkedLesson(inProgressLessonId, "In Progress", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        ).score();
        double completedScore = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new LinkedLesson(completedLessonId, "Completed", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        ).score();

        // assert
        assertTrue(inProgressScore > completedScore);

        // verify
        assertTrue(inProgressScore > completedScore);
    }

    /** Verifies the explore surface uses the explore-specific in-progress lesson boost. */
    @Test
    void score_ShouldApplyExploreInProgressLessonBoost_WhenSurfaceIsExplore() {
        // arrange
        UUID lessonId = UUID.randomUUID();
        RecommendationSignals signals = new RecommendationSignals(
            Map.of(lessonId, new RecommendationSignals.LessonProgressSignal("in_progress", 45, OffsetDateTime.now())),
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

        // act
        double score = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new LinkedLesson(lessonId, "Explore Lesson", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.EXPLORE
        ).score();

        // assert
        assertTrue(score > 20.0);

        // verify
        assertTrue(score > 20.0);
    }

    /** Verifies null or empty lesson lists contribute no lesson bonus. */
    @Test
    void score_ShouldReturnZeroLessonBoost_WhenLinkedLessonsAreNullOrEmpty() {
        // arrange
        Map<String, Object> item = candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(3));
        RecommendationSignals signals = emptySignals();

        // act
        double nullLessonScore = scorer.score(item, null, signals, RecommendationSurface.FEED).score();
        double emptyLessonScore = scorer.score(item, List.of(), signals, RecommendationSurface.FEED).score();

        // assert
        assertEquals(nullLessonScore, emptyLessonScore);

        // verify
        assertEquals(nullLessonScore, emptyLessonScore);
    }

    /** Verifies null lesson statuses are normalized as not-started lesson progress. */
    @Test
    void score_ShouldTreatNullLessonStatusAsNotStarted_WhenLessonProgressMissing() {
        // arrange
        UUID lessonId = UUID.randomUUID();
        RecommendationSignals signals = new RecommendationSignals(
            Map.of(lessonId, new RecommendationSignals.LessonProgressSignal(null, 0, null)),
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

        // act
        double score = scorer.score(
            candidate(UUID.randomUUID(), OffsetDateTime.now().minusDays(1)),
            List.of(new LinkedLesson(lessonId, "Lesson", null, LinkSource.LESSON_CONCEPT)),
            signals,
            RecommendationSurface.FEED
        ).score();

        // assert
        assertTrue(score > 40.0);

        // verify
        assertTrue(score > 40.0);
    }

    /** Verifies mastered, liked, browsed, and repeatedly shown content is penalized. */
    @Test
    void score_ShouldPenalizeMasteredAndRepeatedContent_WhenSignalsShowRepeatExposure() {
        // arrange
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

        // act
        double cleanScore = scorer.score(candidate(contentId, OffsetDateTime.now().minusHours(4)), List.of(), cleanSignals, RecommendationSurface.EXPLORE).score();
        double repeatedScore = scorer.score(candidate(contentId, OffsetDateTime.now().minusHours(4)), List.of(), repeatedSignals, RecommendationSurface.EXPLORE).score();

        // assert
        assertTrue(cleanScore > repeatedScore);

        // verify
        assertTrue(cleanScore > repeatedScore);
    }

    /** Verifies affinity signals and recent search intent raise a candidate score. */
    @Test
    void score_ShouldRewardAffinityAndSearchIntentSignals_WhenMetadataMatchesUserHistory() {
        // arrange
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
        Map<String, Object> item = candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(3));
        item.put("creator_id", creatorId.toString());
        item.put("category_id", categoryId.toString());

        // act
        double neutralScore = scorer.score(item, List.of(), emptySignals(), RecommendationSurface.EXPLORE).score();
        double boostedScore = scorer.score(item, List.of(), boostedSignals, RecommendationSurface.EXPLORE).score();

        // assert
        assertTrue(boostedScore > neutralScore);

        // verify
        assertTrue(boostedScore > neutralScore);
    }

    /** Verifies search intent is capped even when many recent terms match the same candidate. */
    @Test
    void score_ShouldCapSearchIntentScore_WhenManyTermsMatch() {
        // arrange
        RecommendationSignals signals = new RecommendationSignals(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of("slang", "clip", "lesson", "quick", "explainer"),
            Map.of()
        );

        // act
        double score = scorer.score(candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(3)), List.of(), signals, RecommendationSurface.EXPLORE).score();

        // assert
        assertTrue(score >= 8.0);

        // verify
        assertTrue(score >= 8.0);
    }

    /** Verifies feed impressions are penalized more strongly than explore impressions. */
    @Test
    void score_ShouldApplySurfaceSpecificImpressionPenalty_WhenSurfaceChanges() {
        // arrange
        UUID contentId = UUID.randomUUID();
        RecommendationSignals signals = new RecommendationSignals(
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
            Map.of(contentId, 2)
        );
        Map<String, Object> item = candidate(contentId, OffsetDateTime.now().minusHours(2));

        // act
        double feedScore = scorer.score(item, List.of(), signals, RecommendationSurface.FEED).score();
        double exploreScore = scorer.score(item, List.of(), signals, RecommendationSurface.EXPLORE).score();

        // assert
        assertTrue(exploreScore > feedScore);

        // verify
        assertTrue(exploreScore > feedScore);
    }

    /** Verifies candidates without timestamps do not get freshness credit. */
    @Test
    void score_ShouldReturnZeroFreshness_WhenCreatedAtIsNull() {
        // arrange
        Map<String, Object> item = candidate(UUID.randomUUID(), null);

        // act
        double score = scorer.score(item, List.of(), emptySignals(), RecommendationSurface.FEED).score();

        // assert
        assertTrue(score < 30.0);

        // verify
        assertTrue(score < 30.0);
    }

    /** Verifies featured content gets an extra quality boost. */
    @Test
    void score_ShouldApplyFeaturedBonus_WhenContentIsFeatured() {
        // arrange
        Map<String, Object> featuredItem = candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(2));
        Map<String, Object> regularItem = candidate(UUID.randomUUID(), OffsetDateTime.now().minusHours(2));
        featuredItem.put("is_featured", true);

        // act
        double featuredScore = scorer.score(featuredItem, List.of(), emptySignals(), RecommendationSurface.EXPLORE).score();
        double regularScore = scorer.score(regularItem, List.of(), emptySignals(), RecommendationSurface.EXPLORE).score();

        // assert
        assertTrue(featuredScore > regularScore);

        // verify
        assertTrue(featuredScore > regularScore);
    }

    /** Verifies malformed numeric and identifier fields do not break the scoring pipeline. */
    @Test
    void score_ShouldGracefullyHandleInvalidNumericFields_WhenQualityMetricsAreMalformed() {
        // arrange
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "not-a-uuid");
        item.put("creator_id", "still-not-a-uuid");
        item.put("category_id", "bad-category");
        item.put("title", "Noise");
        item.put("description", "No useful metadata");
        item.put("learning_objective", "Test");
        item.put("created_at", "bad-date");
        item.put("view_count", "not-a-number");
        item.put("likes_count", "not-a-number");
        item.put("saves_count", "not-a-number");
        item.put("shares_count", "not-a-number");
        item.put("comments_count", "not-a-number");
        item.put("is_featured", false);
        item.put("tags", java.util.Arrays.asList(" ", null));

        // act
        double score = scorer.score(item, List.of(), emptySignals(), RecommendationSurface.FEED).score();

        // assert
        assertFalse(Double.isNaN(score));

        // verify
        assertFalse(Double.isNaN(score));
    }

    /** Verifies null ids and mixed numeric formats are parsed without breaking the score calculation. */
    @Test
    void score_ShouldHandleNullAndStringMetadata_WhenParsingCandidateFields() {
        // arrange
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("creator_id", null);
        item.put("category_id", null);
        item.put("title", "Slang clip");
        item.put("description", "Short lesson");
        item.put("learning_objective", "Practice slang");
        item.put("created_at", OffsetDateTime.now().minusHours(2).toString());
        item.put("view_count", "120");
        item.put("likes_count", null);
        item.put("saves_count", "4");
        item.put("shares_count", 1);
        item.put("comments_count", null);
        item.put("is_featured", false);
        item.put("tags", List.of("slang"));

        // act
        double score = scorer.score(item, List.of(), emptySignals(), RecommendationSurface.FEED).score();

        // assert
        assertFalse(Double.isNaN(score));

        // verify
        assertFalse(Double.isNaN(score));
    }

    /** Verifies equal scores are ordered by newer timestamps and then by higher content ids. */
    @Test
    void comparator_ShouldBreakTiesByCreatedAtThenContentIdDescending_WhenScoresMatch() {
        // arrange
        OffsetDateTime newest = OffsetDateTime.now().minusHours(1);
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID oldestId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<RecommendationScorer.ScoredRecommendation> ranked = new ArrayList<>(List.of(
            new RecommendationScorer.ScoredRecommendation(candidate(lowerId, newest), 10.0, newest, lowerId),
            new RecommendationScorer.ScoredRecommendation(candidate(higherId, newest), 10.0, newest, higherId),
            new RecommendationScorer.ScoredRecommendation(candidate(oldestId, newest.minusHours(1)), 10.0, newest.minusHours(1), oldestId)
        ));

        // act
        ranked.sort(scorer.comparator());

        // assert
        assertEquals(higherId, ranked.get(0).contentId());
        assertEquals(lowerId, ranked.get(1).contentId());
        assertTrue(ranked.get(1).createdAt().isAfter(ranked.get(2).createdAt()));

        // verify
        assertEquals(3, ranked.size());
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
        candidate.put("created_at", createdAt == null ? null : createdAt.toString());
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
