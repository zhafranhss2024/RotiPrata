package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContentSearchDTO(
    String id,
    String title,
    @JsonProperty("content_type") String contentType,
    String description,
    String snippet
) {}
