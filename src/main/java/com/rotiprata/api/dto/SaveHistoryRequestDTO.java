package com.rotiprata.api.dto;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SaveHistoryRequestDTO {

    private String query;

    @JsonProperty("searched_at")
    private Instant searchedAt;

    private String title;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public Instant getSearchedAt() { return searchedAt; }
    public void setSearchedAt(Instant searchedAt) { this.searchedAt = searchedAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}