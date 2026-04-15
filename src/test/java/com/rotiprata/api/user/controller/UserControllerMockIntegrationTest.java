package com.rotiprata.api.user.controller;

import com.rotiprata.api.browsing.dto.GetHistoryDTO;
import com.rotiprata.api.browsing.dto.SaveHistoryRequestDTO;
import com.rotiprata.api.browsing.service.BrowsingService;
import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.chat.service.ChatService;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.lesson.dto.LessonHeartsStatusResponse;
import com.rotiprata.api.lesson.service.LessonQuizService;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.api.user.domain.Profile;
import com.rotiprata.api.user.dto.UserBadgeResponse;
import com.rotiprata.api.user.service.UserService;
import com.rotiprata.api.user.response.LeaderboardResponse;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.api.user.preference.ThemePreference;
import io.restassured.http.ContentType;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import io.restassured.module.mockmvc.specification.MockMvcRequestSpecification;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Covers user controller scenarios and regression behavior for the current branch changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserControllerMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private LessonService lessonService;

    @MockBean
    private LessonQuizService lessonQuizService;

    @MockBean
    private BrowsingService browsingService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ContentService contentService;

    private static final String TOKEN = "mock-jwt-token";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvcRequestSpecification auth;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);
        auth = given().auth().with(jwt().jwt(j -> j.subject(USER_ID.toString()).tokenValue(TOKEN)));
    }

    /**
     * Verifies that me should return profile when jwt provided.
     */
    /** Verifies the current user's profile is returned. */
    @Test
    void me_ShouldReturnProfile_WhenJwtProvided() {
        // arrange
        Profile profile = new Profile();
        profile.setDisplayName("roti-user");
        when(userService.getOrCreateProfileFromJwt(any(), eq(TOKEN))).thenReturn(profile);

        // act
        var response = auth.when().get("/api/users/me");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).contentType(ContentType.JSON).body("display_name", equalTo("roti-user"));

        // verify
        verify(userService).getOrCreateProfileFromJwt(any(), eq(TOKEN));
    }

    /**
     * Verifies that roles should return lowercase roles when roles exist.
     */
    /** Verifies roles are normalized to lowercase strings. */
    @Test
    void roles_ShouldReturnLowercaseRoles_WhenRolesExist() {
        // arrange
        when(userService.getRoles(eq(USER_ID), eq(TOKEN))).thenReturn(List.of(AppRole.USER, AppRole.ADMIN));

        // act
        var response = auth.when().get("/api/users/me/roles");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("[0]", equalTo("user")).body("[1]", equalTo("admin"));

        // verify
        verify(userService).getRoles(eq(USER_ID), eq(TOKEN));
    }

    /**
     * Verifies that theme preference should return theme when profile has theme preference.
     */
    /** Verifies a stored theme preference is returned in lowercase. */
    @Test
    void themePreference_ShouldReturnTheme_WhenProfileHasThemePreference() {
        // arrange
        Profile profile = new Profile();
        profile.setThemePreference(ThemePreference.DARK);
        when(userService.getOrCreateProfileFromJwt(any(), eq(TOKEN))).thenReturn(profile);

        // act
        var response = auth.when().get("/api/users/me/preferences");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body(equalTo("dark"));

        // verify
        verify(userService).getOrCreateProfileFromJwt(any(), eq(TOKEN));
    }

    /**
     * Verifies that theme preference should return system when profile theme preference is null.
     */
    /** Verifies system theme is returned when profile preference is null. */
    @Test
    void themePreference_ShouldReturnSystem_WhenProfileThemePreferenceIsNull() {
        // arrange
        Profile profile = new Profile();
        profile.setThemePreference(null);
        when(userService.getOrCreateProfileFromJwt(any(), eq(TOKEN))).thenReturn(profile);

        // act
        var response = auth.when().get("/api/users/me/preferences");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body(equalTo("system"));

        // verify
        verify(userService).getOrCreateProfileFromJwt(any(), eq(TOKEN));
    }

    /**
     * Verifies that update theme preference should return updated profile when request is valid.
     */
    /** Verifies theme updates are delegated and updated profile is returned. */
    @Test
    void updateThemePreference_ShouldReturnUpdatedProfile_WhenRequestIsValid() {
        // arrange
        Profile updated = new Profile();
        updated.setThemePreference(ThemePreference.LIGHT);
        when(userService.updateThemePreference(eq(USER_ID), eq(ThemePreference.LIGHT), eq(TOKEN))).thenReturn(updated);

        // act
        var response = auth.contentType(ContentType.JSON).body(Map.of("themePreference", "light")).when().put("/api/users/me/preferences");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("theme_preference", equalTo("light"));

        // verify
        verify(userService).updateThemePreference(eq(USER_ID), eq(ThemePreference.LIGHT), eq(TOKEN));
    }

    /**
     * Verifies that update profile should return updated profile when request is valid.
     */
    /** Verifies profile updates are delegated and returned. */
    @Test
    void updateProfile_ShouldReturnUpdatedProfile_WhenRequestIsValid() {
        // arrange
        Profile updated = new Profile();
        updated.setDisplayName("new_name");
        updated.setGenAlpha(true);
        when(userService.updateProfile(eq(USER_ID), eq("new_name"), eq(true), eq(TOKEN))).thenReturn(updated);

        // act
        var response = auth.contentType(ContentType.JSON).body(Map.of("displayName", "new_name", "isGenAlpha", true)).when().put("/api/users/me");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("display_name", equalTo("new_name")).body("is_gen_alpha", equalTo(true));

        // verify
        verify(userService).updateProfile(eq(USER_ID), eq("new_name"), eq(true), eq(TOKEN));
    }

    /**
     * Verifies that save browsing history should return ok when request is valid.
     */
    /** Verifies browsing history save delegates query and timestamp correctly. */
    @Test
    void saveBrowsingHistory_ShouldReturnOk_WhenRequestIsValid() {
        // arrange
        SaveHistoryRequestDTO request = new SaveHistoryRequestDTO("grammar", Instant.parse("2026-04-08T00:00:00Z"));

        // act
        var response = auth.contentType(ContentType.JSON).body(request).when().post("/api/users/me/history");

        // assert
        response.then().statusCode(HttpStatus.OK.value());

        // verify
        verify(browsingService).saveHistory(eq(USER_ID.toString()), eq("grammar"), eq(Instant.parse("2026-04-08T00:00:00Z")), eq(TOKEN));
    }

    /**
     * Verifies that browsing history should return history when history exists.
     */
    /** Verifies browsing history list is returned for the user. */
    @Test
    void browsingHistory_ShouldReturnHistory_WhenHistoryExists() {
        // arrange
        when(browsingService.fetchHistory(eq(USER_ID.toString()), eq(TOKEN)))
            .thenReturn(List.of(new GetHistoryDTO("h1", "search", LocalDateTime.of(2026, 4, 8, 1, 0))));

        // act
        var response = auth.when().get("/api/users/me/history");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("[0].id", equalTo("h1")).body("[0].query", equalTo("search"));

        // verify
        verify(browsingService).fetchHistory(eq(USER_ID.toString()), eq(TOKEN));
    }

    /**
     * Verifies that clear browsing history should return ok when id provided.
     */
    /** Verifies deleting one browsing history entry delegates to service. */
    @Test
    void clearBrowsingHistory_ShouldReturnOk_WhenIdProvided() {
        // arrange
        String historyId = "history-1";

        // act
        var response = auth.when().delete("/api/users/me/history/{id}", historyId);

        // assert
        response.then().statusCode(HttpStatus.OK.value());

        // verify
        verify(browsingService).deleteHistoryById(eq(historyId), eq(USER_ID.toString()), eq(TOKEN));
    }

    /**
     * Verifies that user stats should return stats when requested.
     */
    /** Verifies user stats map is returned for the authenticated user. */
    @Test
    void userStats_ShouldReturnStats_WhenRequested() {
        // arrange
        when(lessonService.getUserStats(eq(USER_ID), eq(TOKEN))).thenReturn(Map.of("completedLessons", 5));

        // act
        var response = auth.when().get("/api/users/me/stats");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("completedLessons", equalTo(5));

        // verify
        verify(lessonService).getUserStats(eq(USER_ID), eq(TOKEN));
    }

    /**
     * Verifies that badges should return badges when requested.
     */
    /** Verifies earned badges are returned for the authenticated user. */
    @Test
    void badges_ShouldReturnBadges_WhenRequested() {
        // arrange
        when(userService.getUserBadges(eq(USER_ID), eq(TOKEN))).thenReturn(
            List.of(new UserBadgeResponse(UUID.randomUUID(), "Lesson A", "Rookie", "icon.png", true, OffsetDateTime.parse("2026-04-08T00:00:00Z")))
        );

        // act
        var response = auth.when().get("/api/users/me/badges");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("[0].lessonTitle", equalTo("Lesson A"));

        // verify
        verify(userService).getUserBadges(eq(USER_ID), eq(TOKEN));
    }

    /**
     * Verifies that leaderboard should return leaderboard when request is valid.
     */
    /** Verifies leaderboard results are returned using request pagination and query. */
    @Test
    void leaderboard_ShouldReturnLeaderboard_WhenRequestIsValid() {
        // arrange
        LeaderboardResponse leaderboard = new LeaderboardResponse(List.of(), 2, 10, true, 40, "ali", null);
        when(userService.getLeaderboard(eq(USER_ID), eq(2), eq(10), eq("ali"), eq(TOKEN))).thenReturn(leaderboard);

        // act
        var response = auth.queryParam("page", 2).queryParam("pageSize", 10).queryParam("query", "ali").when().get("/api/users/leaderboard");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("page", equalTo(2)).body("pageSize", equalTo(10)).body("query", equalTo("ali"));

        // verify
        verify(userService).getLeaderboard(eq(USER_ID), eq(2), eq(10), eq("ali"), eq(TOKEN));
    }

    /**
     * Verifies that profile content should return content list when collection provided.
     */
    /** Verifies profile content is fetched by requested collection type. */
    @Test
    void profileContent_ShouldReturnContentList_WhenCollectionProvided() {
        // arrange
        when(contentService.getProfileContentCollection(eq(USER_ID), eq(TOKEN), eq("liked")))
            .thenReturn(List.of(Map.of("id", "content-1")));

        // act
        var response = auth.queryParam("collection", "liked").when().get("/api/users/me/content");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("id", hasSize(1)).body("id[0]", equalTo("content-1"));

        // verify
        verify(contentService).getProfileContentCollection(eq(USER_ID), eq(TOKEN), eq("liked"));
    }

    /**
     * Verifies that lesson progress should return progress when requested.
     */
    /** Verifies lesson progress summary is returned. */
    @Test
    void lessonProgress_ShouldReturnProgress_WhenRequested() {
        // arrange
        when(lessonService.getUserLessonProgress(eq(USER_ID), eq(TOKEN))).thenReturn(Map.of("completed", 3));

        // act
        var response = auth.when().get("/api/users/me/lessons/progress");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("completed", equalTo(3));

        // verify
        verify(lessonService).getUserLessonProgress(eq(USER_ID), eq(TOKEN));
    }

    /**
     * Verifies that hearts should return hearts payload when requested.
     */
    /** Verifies hearts endpoint maps service response into expected payload keys. */
    @Test
    void hearts_ShouldReturnHeartsPayload_WhenRequested() {
        // arrange
        OffsetDateTime refillAt = OffsetDateTime.parse("2026-04-08T10:00:00Z");
        when(lessonQuizService.getHeartsStatus(eq(USER_ID), eq(TOKEN))).thenReturn(new LessonHeartsStatusResponse(4, refillAt));

        // act
        var response = auth.when().get("/api/users/me/hearts");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("heartsRemaining", equalTo(4));

        // verify
        verify(lessonQuizService).getHeartsStatus(eq(USER_ID), eq(TOKEN));
    }

    /**
     * Verifies that save messages should return ok when request is valid.
     */
    /** Verifies chat messages are persisted from request body. */
    @Test
    void saveMessages_ShouldReturnOk_WhenRequestIsValid() {
        // arrange
        ChatbotMessageDTO dto = new ChatbotMessageDTO("user", "hello", Instant.parse("2026-04-08T00:00:00Z"));

        // act
        var response = auth.contentType(ContentType.JSON).body(dto).when().post("/api/users/me/chat");

        // assert
        response.then().statusCode(HttpStatus.OK.value());

        // verify
        verify(chatService).saveMessages(eq(TOKEN), eq("hello"), eq("user"));
    }

    /**
     * Verifies that get message history should return messages when history exists.
     */
    /** Verifies chat history is returned for the authenticated user. */
    @Test
    void getMessageHistory_ShouldReturnMessages_WhenHistoryExists() {
        // arrange
        when(chatService.getMessageHistory(eq(TOKEN), eq(USER_ID.toString())))
            .thenReturn(List.of(new ChatbotMessageDTO("assistant", "hi", Instant.parse("2026-04-08T00:00:00Z"))));

        // act
        var response = auth.when().get("/api/users/me/chat");

        // assert
        response.then().statusCode(HttpStatus.OK.value()).body("[0].role", equalTo("assistant")).body("[0].message", equalTo("hi"));

        // verify
        verify(chatService).getMessageHistory(eq(TOKEN), eq(USER_ID.toString()));
    }

    /**
     * Verifies that delete message history should return ok when requested.
     */
    /** Verifies chat history deletion delegates with the authenticated user id. */
    @Test
    void deleteMessageHistory_ShouldReturnOk_WhenRequested() {
        // arrange

        // act
        var response = auth.when().delete("/api/users/me/chat");

        // assert
        response.then().statusCode(HttpStatus.OK.value());

        // verify
        verify(chatService).deleteMessageHistory(eq(TOKEN), eq(USER_ID.toString()));
    }
}
