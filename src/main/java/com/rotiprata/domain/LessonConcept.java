package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LessonConcept {

    private UUID id;

    private Lesson lesson;

    private Content content;

    private Integer orderIndex = 0;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
