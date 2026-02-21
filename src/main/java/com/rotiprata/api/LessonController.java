package com.rotiprata.api;

import com.rotiprata.api.dto.LessonFeedResponse;
import com.rotiprata.api.dto.LessonStatsResponse;
import com.rotiprata.api.dto.UpdateLessonProgressRequest;
import com.rotiprata.application.LessonService;
import com.rotiprata.application.UserService;
import com.rotiprata.domain.Lesson;
import com.rotiprata.domain.Profile;
import com.rotiprata.security.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LessonController {
    private final LessonService lessonService;
    private final UserService userService;

    public LessonController(LessonService lessonService, UserService userService) {
        this.lessonService = lessonService;
        this.userService = userService;
    }

    @GetMapping("/lessons")
    public List<Lesson> lessons(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "difficulty", required = false) Integer difficulty,
        @RequestParam(value = "maxMinutes", required = false) Integer maxMinutes,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "24") int pageSize
    ) {
        return lessonService.fetchLessonFeed(SecurityUtils.getAccessToken(), query, difficulty, maxMinutes, page, pageSize);
    }

    @GetMapping("/lessons/feed")
    public LessonFeedResponse lessonFeed(
        @RequestParam(value = "q", required = false) String query,
        @RequestParam(value = "difficulty", required = false) Integer difficulty,
        @RequestParam(value = "maxMinutes", required = false) Integer maxMinutes,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "pageSize", defaultValue = "24") int pageSize
    ) {
        List<Lesson> items = lessonService.fetchLessonFeed(SecurityUtils.getAccessToken(), query, difficulty, maxMinutes, page, pageSize);
        long total = lessonService.countLessons(SecurityUtils.getAccessToken(), query, difficulty, maxMinutes);
        return new LessonFeedResponse(items, total, page, pageSize);
    }

    @GetMapping("/lessons/search")
    public List<Lesson> searchLessons(@RequestParam("q") String query) {
        return lessonService.searchLessons(SecurityUtils.getAccessToken(), query);
    }

    @GetMapping("/lessons/{id}")
    public Lesson lessonById(@PathVariable("id") UUID id) {
        return lessonService.getLessonById(SecurityUtils.getAccessToken(), id);
    }

    @GetMapping("/users/me/lessons/progress")
    public Map<String, Integer> lessonProgress(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getLessonProgressByLessonId(SecurityUtils.getAccessToken(), userId);
    }

    @GetMapping("/users/me/stats")
    public LessonStatsResponse userLessonStats(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        Profile profile = userService.getOrCreateProfileFromJwt(jwt, SecurityUtils.getAccessToken());
        return lessonService.getLessonStats(
            SecurityUtils.getAccessToken(),
            userId,
            profile.getCurrentStreak() == null ? 0 : profile.getCurrentStreak(),
            profile.getTotalHoursLearned() == null ? 0 : profile.getTotalHoursLearned().doubleValue()
        );
    }

    @PutMapping("/lessons/{id}/progress")
    public void updateLessonProgress(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id, @Valid @RequestBody UpdateLessonProgressRequest request) {
        UUID userId = SecurityUtils.getUserId(jwt);
        int progress = request.progress() == null ? 0 : request.progress();
        lessonService.upsertLessonProgress(SecurityUtils.getAccessToken(), userId, id, progress);
    }
}
