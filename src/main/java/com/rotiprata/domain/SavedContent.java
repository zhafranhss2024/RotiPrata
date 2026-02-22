package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SavedContent {

    private UUID id;

    private UUID userId;

    private Content content;

    private Lesson lesson;

    private OffsetDateTime savedAt = OffsetDateTime.now();
}
