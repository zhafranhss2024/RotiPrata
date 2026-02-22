package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContentFlag {

    private UUID id;

    private Content content;

    private UUID reportedBy;

    private String reason;

    private String description;

    private String status = "pending";

    private UUID resolvedBy;

    private OffsetDateTime resolvedAt;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
