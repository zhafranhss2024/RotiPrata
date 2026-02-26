package com.rotiprata.api.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveHistoryDTO {

    @JsonProperty("user_id")
    private String userId;

    private String query;

    @JsonProperty("searched_at")
    private Instant searchedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Instant getSearchedAt() { return searchedAt; }
    public void setSearchedAt(Instant searchedAt) { this.searchedAt = searchedAt; }

}
