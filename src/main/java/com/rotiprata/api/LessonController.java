package com.rotiprata.api;

import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.application.LessonFeedService;
import com.rotiprata.security.SecurityUtils;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {
    private final LessonFeedService lessonFeedService;

    public LessonController(LessonFeedService lessonFeedService) {
        this.lessonFeedService = lessonFeedService;
    }

    @GetMapping
    public LessonFeedResponse lessonFeed(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", required = false) Integer pageSize,
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "difficulty", required = false) Integer difficulty,
        @RequestParam(value = "maxMinutes", required = false) Integer maxMinutes,
        @RequestParam(value = "sort", defaultValue = "newest") String sort
    ) {
        if (jwt == null) {
            int safePage = Math.max(1, page);
            int safePageSize = pageSize == null ? 12 : Math.max(1, pageSize);
            return new LessonFeedResponse(List.of(), false, safePage, safePageSize);
        }
        return lessonFeedService.getLessonFeed(
            SecurityUtils.getAccessToken(),
            page,
            pageSize,
            query,
            difficulty,
            maxMinutes,
            sort
        );
    }
}
