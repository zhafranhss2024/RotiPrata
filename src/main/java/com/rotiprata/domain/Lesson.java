package com.rotiprata.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Lesson {

    private UUID id;

    private UUID createdBy;

    private String title;

    private String description;

    private String headerMediaUrl;

    private String summary;

    private String learningObjectives;

    private Integer estimatedMinutes = 10;

    private Integer xpReward = 100;

    private String badgeName;

    private String badgeIconUrl;

    private Integer difficultyLevel = 1;

    @JsonProperty("is_published")
    private Boolean published = false;

    private Integer completionCount = 0;

    private String originContent;

    private String definitionContent;

    private String usageExamples;

    private String loreContent;

    private String evolutionContent;

    private String comparisonContent;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
