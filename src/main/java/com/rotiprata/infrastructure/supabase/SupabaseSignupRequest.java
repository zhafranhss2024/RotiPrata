package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SupabaseSignupRequest(String email, String password, Map<String, Object> data) {}
