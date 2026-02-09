package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContentTag {

    private UUID id;

    private Content content;

    private String tag;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
