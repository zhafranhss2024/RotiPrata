package com.rotiprata.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Profile {

    private UUID id;

    private UUID userId;

    private String displayName;

    private String avatarUrl;

    private String bio;

    private LocalDate dateOfBirth;

    @JsonProperty("is_gen_alpha")
    private Boolean genAlpha = false;

    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @JsonProperty("is_verified")
    private Boolean verified = false;

    private Integer reputationPoints = 0;

    private Integer currentStreak = 0;

    private Integer longestStreak = 0;

    private LocalDate lastActivityDate;

    private Double totalHoursLearned = 0.0;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
