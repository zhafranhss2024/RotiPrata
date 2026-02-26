package com.rotiprata.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ContentQuizSubmitRequest(
    @JsonProperty("answers") Map<String, String> answers,
    @JsonProperty("timeTakenSeconds") Integer timeTakenSeconds
) {}
