package com.rotiprata.api.browsing.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveHistoryDTO {

    @JsonProperty("user_id")
    private String userId;

    private String query;

    @JsonProperty("searched_at")
    private Instant searchedAt;
}