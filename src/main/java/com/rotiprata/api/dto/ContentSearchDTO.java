package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContentSearchDTO(
    String title,
    String description,
    @JsonProperty("content_type") String contentType
) {}
