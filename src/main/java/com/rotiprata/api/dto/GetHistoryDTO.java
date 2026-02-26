package com.rotiprata.api.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GetHistoryDTO {

    private String id;

    private String query;

    @JsonProperty("searched_at")
    private LocalDateTime  searchedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public LocalDateTime  getSearchedAt() { return searchedAt; }
    public void setSearchedAt(LocalDateTime  searchedAt) { this.searchedAt = searchedAt; }
    
}
