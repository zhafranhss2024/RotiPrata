package com.rotiprata.application;

import org.springframework.stereotype.Service;

import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class BrowsingHistoryService {

    private final SupabaseRestClient supabaseRestClient;

    public BrowsingHistoryService(SupabaseRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    
}
