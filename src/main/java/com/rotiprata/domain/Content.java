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
public class Content {

    private UUID id;

    private UUID creatorId;

    private String title;

    private String description;

    private ContentType contentType;

    private String mediaUrl;

    private String thumbnailUrl;

    private String mediaStatus;

    private java.util.UUID categoryId;

    private Category category;

    private ContentStatus status = ContentStatus.PENDING;

    private String learningObjective;

    private String originExplanation;

    private String definitionLiteral;

    private String definitionUsed;

    private String olderVersionReference;

    private Integer educationalValueVotes = 0;

    private Integer viewCount = 0;

    @JsonProperty("is_featured")
    private Boolean featured = false;

    private UUID reviewedBy;

    private OffsetDateTime reviewedAt;

    private String reviewFeedback;

    private Boolean isSubmitted;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
