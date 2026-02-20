package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContentMedia {
    private UUID id;
    private UUID contentId;
    private String sourceType;
    private String sourceUrl;
    private String status;
    private String hlsUrl;
    private String thumbnailUrl;
    private Integer durationMs;
    private Integer width;
    private Integer height;
    private Long sizeBytes;
    private String errorMessage;
    private OffsetDateTime claimedAt;
    private String claimedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
