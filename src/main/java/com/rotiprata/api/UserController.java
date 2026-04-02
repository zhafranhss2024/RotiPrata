package com.rotiprata.api;

import com.rotiprata.api.dto.SaveHistoryRequestDTO;
import com.rotiprata.api.dto.ThemePreferenceRequest;
import com.rotiprata.api.dto.UserBadgeResponse;
import com.rotiprata.api.dto.UpdateProfileRequest;
import com.rotiprata.api.dto.GetHistoryDTO;
import com.rotiprata.api.dto.LeaderboardResponse;
import com.rotiprata.application.BrowsingService;
import com.rotiprata.application.ChatService;
import com.rotiprata.application.ContentService;
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

import com.rotiprata.api.dto.ChatbotMessageDTO;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final LessonService lessonService;
    private final LessonQuizService lessonQuizService;
    private final BrowsingService browsingService;
    private final ChatService chatService;
    private final ContentService contentService;

    public UserController(
            UserService userService,
            LessonService lessonService,
            LessonQuizService lessonQuizService,
            BrowsingService browsingService,
            ChatService chatService,
            ContentService contentService
    ) {
        this.userService = userService;
        this.lessonService = lessonService;
        this.lessonQuizService = lessonQuizService;
        this.browsingService = browsingService;
        this.chatService = chatService;
        this.contentService = contentService;
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

    @PutMapping("/me")
    public Profile updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return userService.updateProfile(
                userId,
                request.displayName(),
                request.isGenAlpha(),
                SecurityUtils.getAccessToken()
        );
    }

    @PostMapping("/me/history")
    public void saveBrowsingHistory(
            @RequestBody SaveHistoryRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        browsingService.saveHistory(
                userId,
                request.getQuery(),
                request.getSearchedAt(),
                SecurityUtils.getAccessToken()
        );
    }

    @GetMapping("/me/history")
    public List<GetHistoryDTO> browsingHistory(
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();

        return browsingService.fetchHistory(
                userId,
                SecurityUtils.getAccessToken()
        );
    }

    @DeleteMapping("/me/history/{id}")
    public void clearBrowsingHistory(
            @PathVariable("id") String id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        browsingService.deleteHistoryById(
                id,
                userId,
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

    @GetMapping("/me/badges")
    public List<UserBadgeResponse> badges(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return userService.getUserBadges(userId, SecurityUtils.getAccessToken());
    }

    @GetMapping("/leaderboard")
    public LeaderboardResponse leaderboard(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String query
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return userService.getLeaderboard(userId, page, pageSize, query, SecurityUtils.getAccessToken());
    }

    @GetMapping("/me/content")
    public List<Map<String, Object>> profileContent(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam("collection") String collection
    ) {
        UUID userId = SecurityUtils.getUserId(jwt);
        return contentService.getProfileContentCollection(userId, SecurityUtils.getAccessToken(), collection);
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

    @PostMapping("/me/chat")
    public void saveMessages(
        @AuthenticationPrincipal Jwt jwt, 
        @RequestBody ChatbotMessageDTO dto
    ) {
        chatService.saveMessages(SecurityUtils.getAccessToken(), dto.getMessage(), dto.getRole());
    }

    @GetMapping("/me/chat")
    public List<ChatbotMessageDTO> getMessageHistory (@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        return chatService.getMessageHistory(SecurityUtils.getAccessToken(), userId);
    }

    @DeleteMapping("/me/chat")
    public void deleteMessageHistory(
        @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = jwt.getSubject();
        chatService.deleteMessageHistory(SecurityUtils.getAccessToken(), userId);
    }
}
