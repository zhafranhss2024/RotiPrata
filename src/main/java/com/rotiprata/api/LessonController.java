package com.rotiprata.api;

import com.rotiprata.application.LessonService;
import com.rotiprata.security.SecurityUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LessonController {
    private final LessonService lessonService;

    public LessonController(LessonService lessonService) {
        this.lessonService = lessonService;
    }

    @GetMapping("/lessons")
    public List<Map<String, Object>> lessons() {
        return lessonService.getLessons(SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/search")
    public List<Map<String, Object>> searchLessons(@RequestParam("q") String q) {
        return lessonService.searchLessons(q, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/{lessonId}")
    public Map<String, Object> lessonById(@PathVariable UUID lessonId) {
        return lessonService.getLessonById(lessonId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/lessons/{lessonId}/sections")
    public List<Map<String, Object>> lessonSections(@PathVariable UUID lessonId) {
        return lessonService.getLessonSections(lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/enroll")
    public void enrollLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.enrollLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/lessons/{lessonId}/save")
    public void saveLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.saveLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PutMapping("/lessons/{lessonId}/progress")
    public void updateProgress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Integer> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        int progress = payload.getOrDefault("progress", 0);
        lessonService.updateLessonProgress(userId, lessonId, progress, SecurityUtils.getAccessToken());
    }

    @GetMapping("/admin/lessons")
    public List<Map<String, Object>> adminLessons(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getAdminLessons(userId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lessons")
    public Map<String, Object> createLesson(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> payload) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLesson(userId, payload, SecurityUtils.getAccessToken());
    }

    @PutMapping("/admin/lessons/{lessonId}")
    public Map<String, Object> updateLesson(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.updateLesson(userId, lessonId, payload, SecurityUtils.getAccessToken());
    }

    @DeleteMapping("/admin/lessons/{lessonId}")
    public void deleteLesson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID userId = SecurityUtils.getUserId(jwt);
        lessonService.deleteLesson(userId, lessonId, SecurityUtils.getAccessToken());
    }

    @PostMapping("/admin/lessons/{lessonId}/quiz")
    public Map<String, Object> createLessonQuiz(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID lessonId,
        @RequestBody Map<String, Object> payload
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.createLessonQuiz(userId, lessonId, payload, SecurityUtils.getAccessToken());
    }
}
