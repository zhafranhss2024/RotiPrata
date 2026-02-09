package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupabaseRecoveryRequest(
    String email,
    @JsonProperty("redirect_to") String redirectTo
) {}
