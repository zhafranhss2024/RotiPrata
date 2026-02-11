package com.rotiprata.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

@Service
public class BrowsingService {
    
    private final SuperbaseRestClient superbaseRestClient;

    public BrowsingService(SuperbaseClient superbaseClient) {
        this.superbaseRestClient = superbaseClient;
    }

    // To change the object type after implementing parent class of content and lessons?
    public List<Content> search(String query, String[] filter) {

        List<Content> results = new ArrayList<>();
        
        // 1. Get Contents
        List<Content> contents = searchContent(query, filters);
        
        // 2. Get Lessons
        List<Lesson> lessons = searchLessons(query, filters);

        return results;

    }
}
