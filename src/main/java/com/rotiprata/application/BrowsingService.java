package com.rotiprata.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.rotiprata.domain.Content;

@Service
public class BrowsingService {

    private final ContentService contentService;

    public BrowsingService(ContentService contentService) {
        this.contentService = contentService;
    }

    public List<Content> search(String query, String[] filter, String accessToken) {

        List<Content> results = new ArrayList<>();

        List<Content> contents = contentService.getFilteredContent(query, filter, accessToken);
        results.addAll(contents);

        // TODO: Get lessons if Lesson extends Content
        // List<Lesson> lessons = lessonService.getFilteredLessons(query, filter, accessToken);
        // results.addAll(lessons);

        return results;
    }
}
