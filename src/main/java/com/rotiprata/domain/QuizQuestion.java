package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class QuizQuestion {

    private UUID id;

    private Quiz quiz;

    private String questionText;

    private String questionType = "multiple_choice";

    private String mediaUrl;

    private String options;

    private String correctAnswer;

    private String explanation;

    private Integer points = 10;

    private Integer orderIndex = 0;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
