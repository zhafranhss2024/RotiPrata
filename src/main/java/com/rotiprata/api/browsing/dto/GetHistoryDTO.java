package com.rotiprata.api.browsing.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Documents the get history dto type.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetHistoryDTO {

    private String id;
    private String query;

    @JsonProperty("searched_at")
    private LocalDateTime searchedAt;
}
