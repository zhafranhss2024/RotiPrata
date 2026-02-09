package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserAchievement {

    private UUID id;

    private UUID userId;

    private String achievementName;

    private String achievementType;

    private String iconUrl;

    private String description;

    private OffsetDateTime earnedAt = OffsetDateTime.now();
}
