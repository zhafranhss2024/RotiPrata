package com.rotiprata.api;

import com.rotiprata.api.dto.LessonFeedRequest;
import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.application.LessonService;
import com.rotiprata.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {
    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping
    public LessonFeedResponse feed(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "all") String difficulty,
        @RequestParam(defaultValue = "all") String duration,
        @RequestParam(defaultValue = "popular") String sort,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "12") Integer pageSize
    ) {
        LessonFeedRequest request = new LessonFeedRequest(query, difficulty, duration, sort, page, pageSize);
        return lessonService.getLessonFeed(SecurityUtils.getAccessToken(), request);
    }
}
