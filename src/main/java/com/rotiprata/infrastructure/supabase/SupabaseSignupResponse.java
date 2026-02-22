package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SupabaseSignupResponse {
    @JsonProperty("user")
    private SupabaseUser user;

    @JsonProperty("session")
    private SupabaseSessionResponse session;

    public SupabaseUser getUser() {
        return user;
    }

    public void setUser(SupabaseUser user) {
        this.user = user;
    }

    public SupabaseSessionResponse getSession() {
        return session;
    }

    public void setSession(SupabaseSessionResponse session) {
        this.session = session;
    }
}
