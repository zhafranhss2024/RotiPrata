package com.rotiprata.api.feed.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers recommendation surface scenarios and regression behavior for the current branch changes.
 */
class RecommendationSurfaceTest {

    /**
     * Verifies that values should contain feed and explore when enumerated.
     */
    /** Verifies RecommendationSurface exposes both supported recommendation surfaces. */
    @Test
    void values_ShouldContainFeedAndExplore_WhenEnumerated() {
        // arrange

        // act
        Set<String> names = Arrays.stream(RecommendationSurface.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

        // assert
        assertEquals(Set.of("FEED", "EXPLORE"), names);

        // verify
        assertTrue(names.contains("FEED"));
    }

    /** Verifies valueOf resolves exact enum names for request-to-surface translation. */
    @Test
    void valueOf_ShouldReturnSurface_WhenNameMatchesEnumConstant() {
        // arrange

        // act
        RecommendationSurface feed = RecommendationSurface.valueOf("FEED");
        RecommendationSurface explore = RecommendationSurface.valueOf("EXPLORE");

        // assert
        assertSame(RecommendationSurface.FEED, feed);
        assertSame(RecommendationSurface.EXPLORE, explore);

        // verify
        assertEquals("FEED", feed.name());
    }
}
