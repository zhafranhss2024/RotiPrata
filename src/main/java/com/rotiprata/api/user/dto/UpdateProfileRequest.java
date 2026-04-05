package com.rotiprata.api.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @JsonProperty("display_name")
    @Size(min = 3, max = 30, message = "Display name must be between 3 and 30 characters")
    @JsonAlias("displayName")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Display name may only contain letters, numbers, '.', '_' or '-' characters")
    String displayName,

    @JsonProperty("is_gen_alpha")
    @JsonAlias("isGenAlpha")
    Boolean isGenAlpha
) {
}
