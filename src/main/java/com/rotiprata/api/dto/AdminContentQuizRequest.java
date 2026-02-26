package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AdminContentQuizRequest(
    @JsonProperty("questions") List<AdminContentQuizQuestionRequest> questions
) {}
