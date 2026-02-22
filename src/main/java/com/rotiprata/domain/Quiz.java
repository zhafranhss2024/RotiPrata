package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Quiz {

    private UUID id;

    private Lesson lesson;

    private Content content;

    private String title;

    private String description;

    private String quizType = "multiple_choice";

    private Integer timeLimitSeconds;

    private Integer passingScore = 70;

    private UUID createdBy;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
