package com.rotiprata.application;

import java.util.List;

public final class LessonFlowConstants {
    private LessonFlowConstants() {}

    public static final String SECTION_INTRO = "intro";
    public static final String SECTION_DEFINITION = "definition";
    public static final String SECTION_USAGE = "usage";
    public static final String SECTION_LORE = "lore";
    public static final String SECTION_EVOLUTION = "evolution";
    public static final String SECTION_COMPARISON = "comparison";
    public static final String STOP_QUIZ = "quiz";

    public static final String QUIZ_STATUS_LOCKED = "locked";
    public static final String QUIZ_STATUS_AVAILABLE = "available";
    public static final String QUIZ_STATUS_IN_PROGRESS = "in_progress";
    public static final String QUIZ_STATUS_PASSED = "passed";
    public static final String QUIZ_STATUS_BLOCKED_HEARTS = "blocked_hearts";
    public static final String QUIZ_STATUS_FAILED = "failed";

    public static final String NEXT_STOP_SECTION = "section";
    public static final String NEXT_STOP_QUIZ = "quiz";
    public static final String NEXT_STOP_DONE = "done";

    public static final int MAX_HEARTS = 5;

    public static final List<String> CONTENT_SECTION_IDS = List.of(
        SECTION_INTRO,
        SECTION_DEFINITION,
        SECTION_USAGE,
        SECTION_LORE,
        SECTION_EVOLUTION,
        SECTION_COMPARISON
    );
}
