package com.rotiprata.api;

import com.rotiprata.domain.Profile;
import com.rotiprata.domain.ThemePreference;
import com.rotiprata.security.SecurityUtils;
import com.rotiprata.application.BrowsingService;
import com.rotiprata.application.UserService;
import com.rotiprata.api.dto.SaveHistoryDTO;
import com.rotiprata.api.dto.SaveHistoryRequestDTO;
import com.rotiprata.api.dto.ThemePreferenceRequest;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rotiprata.api.dto.GetHistoryDTO;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final BrowsingService browsingService;

    public UserController(UserService userService, BrowsingService browsingService) {
        this.userService = userService;
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
        UUID userId = SecurityUtils.getUserId(jwt);
        Profile profile = userService.getOrCreateProfileFromJwt(jwt, SecurityUtils.getAccessToken());
        return profile.getThemePreference().name().toLowerCase();
    }

    @PutMapping("/me/preferences")
    public Profile updateThemePreference(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ThemePreferenceRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        ThemePreference preference = ThemePreference.valueOf(request.themePreference().toUpperCase());
        return userService.updateThemePreference(userId, preference, SecurityUtils.getAccessToken());
    }

    @PostMapping("/me/history")
    public void saveHistory(
        @RequestBody SaveHistoryRequestDTO request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        String accessToken = jwt.getTokenValue();
        browsingService.saveHistory(request.getContentId(), request.getLessonId(), accessToken);
    }

    @GetMapping("/me/history")
    public List<GetHistoryDTO> getHistory(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        String accessToken = jwt.getTokenValue();
        return browsingService.getHistory(userId, accessToken);
    }

    @DeleteMapping("/me/history")
    public void clearHistory(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        String accessToken = jwt.getTokenValue();
        browsingService.clearHistory(userId, accessToken);
    }

}
