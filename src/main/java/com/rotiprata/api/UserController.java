package com.rotiprata.api;

import com.rotiprata.api.dto.ThemePreferenceRequest;
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

    // ✅ Only ONE constructor
    public UserController(UserService userService, LessonService lessonService) {
        this.userService = userService;
        this.lessonService = lessonService;
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
        Profile profile = userService.getOrCreateProfileFromJwt(jwt, SecurityUtils.getAccessToken());
        return profile.getThemePreference().name().toLowerCase();
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

    @GetMapping("/me/lessons/progress")
    public Map<String, Integer> lessonProgress(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getUserLessonProgress(userId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/me/stats")
    public Map<String, Integer> userStats(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return lessonService.getUserStats(userId, SecurityUtils.getAccessToken());
    }
}
