package com.rotiprata.api;

import com.rotiprata.api.dto.SaveHistoryRequestDTO;
import com.rotiprata.api.dto.ThemePreferenceRequest;
import com.rotiprata.api.dto.GetHistoryDTO;
import com.rotiprata.application.BrowsingService;
import com.rotiprata.application.LessonQuizService;
import com.rotiprata.application.LessonService;
import com.rotiprata.application.UserService;
import com.rotiprata.domain.Profile;
import com.rotiprata.domain.ThemePreference;
import com.rotiprata.security.SecurityUtils;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final LessonService lessonService;
    private final LessonQuizService lessonQuizService;
    private final BrowsingService browsingService;

    public UserController(
            UserService userService,
            LessonService lessonService,
            LessonQuizService lessonQuizService,
            BrowsingService browsingService
    ) {
        this.userService = userService;
        this.lessonService = lessonService;
        this.lessonQuizService = lessonQuizService;
        this.browsingService = browsingService;
    }

    @GetMapping("/me")
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getOrCreateProfileFromJwt(jwt, SecurityUtils.getAccessToken());
    }

    @GetMapping("/me/roles")
    public List<String> roles(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return userService.getRoles(userId, SecurityUtils.getAccessToken())
                .stream()
                .map(role -> role.name().toLowerCase())
                .toList();
    }

    @GetMapping("/me/preferences")
    public String themePreference(@AuthenticationPrincipal Jwt jwt) {
        Profile profile =
                userService.getOrCreateProfileFromJwt(jwt, SecurityUtils.getAccessToken());

        ThemePreference pref = profile.getThemePreference();
        return pref == null ? "system" : pref.name().toLowerCase();
    }

    @PutMapping("/me/preferences")
    public Profile updateThemePreference(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ThemePreferenceRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);

        ThemePreference preference =
                ThemePreference.valueOf(request.themePreference().toUpperCase());

        return userService.updateThemePreference(
                userId,
                preference,
                SecurityUtils.getAccessToken()
        );
    }

    // 🔥 SINGLE clean history endpoint

    @PostMapping("/me/history")
    public void saveBrowsingHistory(
            @RequestBody SaveHistoryRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);

        browsingService.saveHistory(
                userId.toString(),
                request.getContentId(),
                request.getLessonId(),
                request.getTitle(),
                SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/me/history")
    public List<GetHistoryDTO> browsingHistory(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return browsingService.fetchHistory(
                userId.toString(),
                SecurityUtils.getAccessToken()
        );
    }

    @DeleteMapping("/me/history")
    public void clearBrowsingHistory(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        browsingService.purgeHistory(
                userId.toString(),
                SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/me/stats")
    public Map<String, Integer> userStats(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getUserStats(
                userId,
                SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/me/lessons/progress")
    public Map<String, Integer> lessonProgress(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getUserLessonProgress(
                userId,
                SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/me/hearts")
    public Map<String, Object> hearts(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        var hearts = lessonQuizService.getHeartsStatus(userId, SecurityUtils.getAccessToken());
        return Map.of(
            "heartsRemaining", hearts.heartsRemaining(),
            "heartsRefillAt", hearts.heartsRefillAt()
        );
    }
}
