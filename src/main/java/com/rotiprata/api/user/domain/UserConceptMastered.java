package com.rotiprata.api.user.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.rotiprata.api.content.domain.Content;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserConceptMastered {

    private UUID id;

    private UUID userId;

    private Content content;

    private OffsetDateTime masteredAt = OffsetDateTime.now();
}
