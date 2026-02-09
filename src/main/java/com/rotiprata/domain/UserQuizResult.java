package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserQuizResult {

    private UUID id;

    private UUID userId;

    private Quiz quiz;

    private Integer score;

    private Integer maxScore;

    private Double percentage;

    private Boolean passed = false;

    private String answers;

    private Integer timeTakenSeconds;

    private OffsetDateTime attemptedAt = OffsetDateTime.now();
}
