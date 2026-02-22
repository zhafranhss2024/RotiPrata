package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ModerationQueue {

    private UUID id;

    private Content content;

    private OffsetDateTime submittedAt = OffsetDateTime.now();

    private Integer priority = 0;

    private UUID assignedTo;

    private String notes;
}
