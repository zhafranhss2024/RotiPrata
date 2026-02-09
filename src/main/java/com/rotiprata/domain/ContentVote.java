package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContentVote {

    private UUID id;

    private Content content;

    private UUID userId;

    private String voteType;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
