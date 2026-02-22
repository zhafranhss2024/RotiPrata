package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserLessonProgress {

    private UUID id;

    private UUID userId;

    private Lesson lesson;

    private String status = "not_started";

    private Integer progressPercentage = 0;

    private String currentSection;

    private OffsetDateTime startedAt;

    private OffsetDateTime completedAt;

    private OffsetDateTime lastAccessedAt = OffsetDateTime.now();

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
