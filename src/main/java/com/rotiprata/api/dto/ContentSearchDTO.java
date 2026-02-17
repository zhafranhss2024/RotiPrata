package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ContentSearchDTO(
    String id,
    String content_type,
    String title,
    String description,
    String snippet
) {}
