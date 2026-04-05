package com.rotiprata.api.browsing.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.rotiprata.api.content.domain.Content;
import com.rotiprata.api.lesson.domain.Lesson;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BrowsingHistory {
    private UUID id;
    private UUID userId;
    private Content content;
    private Lesson lesson;
    private OffsetDateTime viewedAt = OffsetDateTime.now();
}
