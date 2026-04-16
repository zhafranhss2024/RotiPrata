package com.rotiprata.api.moderation.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.rotiprata.api.content.domain.Content;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the moderation queue state used by the feature layer.
 */
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
