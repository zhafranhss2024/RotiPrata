package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Category {

    private UUID id;

    private String name;

    private CategoryType type;

    private String description;

    private String iconUrl;

    private String color;

    private OffsetDateTime createdAt = OffsetDateTime.now();
}
