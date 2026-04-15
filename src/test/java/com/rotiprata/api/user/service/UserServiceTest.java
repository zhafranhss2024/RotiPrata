package com.rotiprata.api.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rotiprata.api.user.domain.Profile;
import com.rotiprata.api.user.domain.UserRole;
import com.rotiprata.api.user.dto.UserBadgeResponse;
import com.rotiprata.api.user.response.LeaderboardResponse;
import com.rotiprata.security.authorization.AppRole;
import com.rotiprata.api.user.preference.ThemePreference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService tests")
class UserServiceTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(supabaseRestClient, supabaseAdminRestClient);
    }

    private static Profile profile(UUID userId, String displayName, Integer xp, Integer streak) {
        Profile profile = new Profile();
        profile.setUserId(userId);
        profile.setDisplayName(displayName);
        profile.setReputationPoints(xp);
        profile.setCurrentStreak(streak);
        return profile;
    }

    // Verifies getProfile returns first profile when a match is found.
    @Test
    void getProfile_ShouldReturnFirstProfile_WhenProfileExists() {
        UUID userId = UUID.randomUUID();
        Profile expected = profile(userId, "alpha", 10, 1);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(expected));

        //act
        Profile result = service.getProfile(userId, "token");

        //assert
        assertEquals(expected, result);

        //verify
        verify(supabaseRestClient, times(1)).getList(eq("profiles"), anyString(), eq("token"), any());
    }

    // Verifies getProfile throws not found when Supabase returns no rows.
    @Test
    void getProfile_ShouldThrowNotFound_WhenProfileDoesNotExist() {
        UUID userId = UUID.randomUUID();

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of());

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getProfile(userId, "token"));

        //assert
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, times(1)).getList(eq("profiles"), anyString(), eq("token"), any());
    }

    // Verifies display-name check returns false for blank input.
    @Test
    void isDisplayNameTaken_ShouldReturnFalse_WhenDisplayNameIsBlank() {
        //arrange

        //act
        boolean taken = service.isDisplayNameTaken("   ");

        //assert
        assertFalse(taken);

        //verify
        verify(supabaseRestClient, never()).getList(anyString(), anyString(), any(), any());
    }

    // Verifies display-name check returns true when a registry row exists.
    @Test
    void isDisplayNameTaken_ShouldReturnTrue_WhenRegistryContainsName() {
        Profile row = new Profile();

        //arrange
        when(supabaseRestClient.getList(eq("display_name_registry"), anyString(), eq(null), any())).thenReturn(List.of(row));

        //act
        boolean taken = service.isDisplayNameTaken("PlayerOne");

        //assert
        assertTrue(taken);

        //verify
        verify(supabaseRestClient).getList(eq("display_name_registry"), anyString(), eq(null), any());
    }

    // Verifies RLS authorization errors are gracefully treated as available.
    @Test
    void isDisplayNameTaken_ShouldReturnFalse_WhenRlsBlocksQuery() {
        //arrange
        when(supabaseRestClient.getList(eq("display_name_registry"), anyString(), eq(null), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "rls"));

        //act
        boolean taken = service.isDisplayNameTaken("blocked-name");

        //assert
        assertFalse(taken);

        //verify
        verify(supabaseRestClient).getList(eq("display_name_registry"), anyString(), eq(null), any());
    }

    // Verifies JWT claims are used to create a profile when none exists.
    @Test
    void getOrCreateProfileFromJwt_ShouldCreateProfile_WhenProfileMissing() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("jwt")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("email", "kid@example.com")
            .claim("user_metadata", Map.of("display_name", "KidUser", "is_gen_alpha", true))
            .build();

        Profile created = profile(userId, "kiduser", 0, 0);
        UserRole role = new UserRole();
        role.setId(UUID.randomUUID());

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any()))
            .thenReturn(List.of())
            .thenReturn(List.of(created));
        when(supabaseRestClient.postList(eq("profiles"), anyMap(), eq("token"), any())).thenReturn(List.of(created));
        when(supabaseRestClient.getList(eq("user_roles"), anyString(), eq("token"), any())).thenReturn(List.of()).thenReturn(List.of(role));
        when(supabaseRestClient.postList(eq("user_roles"), anyMap(), eq("token"), any())).thenReturn(List.of(role));

        //act
        Profile result = service.getOrCreateProfileFromJwt(jwt, "token");

        //assert
        assertEquals(created, result);

        //verify
        verify(supabaseRestClient).postList(eq("profiles"), anyMap(), eq("token"), any());
        verify(supabaseRestClient).postList(eq("user_roles"), anyMap(), eq("token"), any());
    }

    // Verifies ensureProfile updates mutable fields when existing profile differs.
    @Test
    void ensureProfile_ShouldPatchProfile_WhenExistingProfileNeedsChanges() {
        UUID userId = UUID.randomUUID();
        Profile existing = profile(userId, "old-name", 5, 1);
        existing.setGenAlpha(false);
        Profile updated = profile(userId, "new-name", 5, 1);
        updated.setGenAlpha(true);

        UserRole role = new UserRole();
        role.setId(UUID.randomUUID());

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(existing));
        when(supabaseRestClient.patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any())).thenReturn(List.of(updated));
        when(supabaseRestClient.getList(eq("user_roles"), anyString(), eq("token"), any())).thenReturn(List.of(role));

        //act
        Profile result = service.ensureProfile(userId, "new-name", true, "token", true);

        //assert
        assertEquals("new-name", result.getDisplayName());
        assertTrue(Boolean.TRUE.equals(result.getGenAlpha()));

        //verify
        verify(supabaseRestClient).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
        verify(supabaseRestClient, never()).postList(eq("user_roles"), anyMap(), eq("token"), any());
    }

    // Verifies ensureProfile inserts default user role when no role row exists.
    @Test
    void ensureProfile_ShouldInsertUserRole_WhenRoleIsMissing() {
        UUID userId = UUID.randomUUID();
        Profile existing = profile(userId, "same", 0, 0);
        existing.setGenAlpha(false);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(existing));
        when(supabaseRestClient.getList(eq("user_roles"), anyString(), eq("token"), any())).thenReturn(List.of());
        when(supabaseRestClient.postList(eq("user_roles"), anyMap(), eq("token"), any())).thenReturn(List.of(new UserRole()));

        //act
        Profile result = service.ensureProfile(userId, "same", false, "token", true);

        //assert
        assertEquals(existing, result);

        //verify
        verify(supabaseRestClient).postList(eq("user_roles"), anyMap(), eq("token"), any());
    }

    // Verifies service-role profile creation retries with suffix on conflict.
    @Test
    void ensureProfileWithServiceRole_ShouldRetryWithSuffix_WhenCreateConflictsAndAllowSuffix() {
        UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Profile created = profile(userId, "name-123e45", 0, 0);

        //arrange
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(List.of());
        when(supabaseAdminRestClient.postList(eq("profiles"), anyMap(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "dup"))
            .thenReturn(List.of(created));
        when(supabaseAdminRestClient.getList(eq("user_roles"), anyString(), any())).thenReturn(List.of(new UserRole()));

        //act
        Profile result = service.ensureProfileWithServiceRole(userId, "name", true, true);

        //assert
        assertEquals(created, result);

        //verify
        verify(supabaseAdminRestClient, times(2)).postList(eq("profiles"), anyMap(), any());
    }

    // Verifies getRoles maps role entities to enum values.
    @Test
    void getRoles_ShouldReturnRoleEnums_WhenRowsExist() {
        UUID userId = UUID.randomUUID();
        UserRole admin = new UserRole();
        admin.setRole(AppRole.ADMIN);
        UserRole user = new UserRole();
        user.setRole(AppRole.USER);

        //arrange
        when(supabaseRestClient.getList(eq("user_roles"), anyString(), eq("token"), any())).thenReturn(List.of(admin, user));

        //act
        List<AppRole> roles = service.getRoles(userId, "token");

        //assert
        assertEquals(List.of(AppRole.ADMIN, AppRole.USER), roles);

        //verify
        verify(supabaseRestClient).getList(eq("user_roles"), anyString(), eq("token"), any());
    }

    // Verifies theme preference update fails when no profile is updated.
    @Test
    void updateThemePreference_ShouldThrowNotFound_WhenProfileMissing() {
        UUID userId = UUID.randomUUID();

        //arrange
        when(supabaseRestClient.patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any())).thenReturn(List.of());

        //act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.updateThemePreference(userId, ThemePreference.DARK, "token")
        );

        //assert
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());

        //verify
        verify(supabaseRestClient).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
    }

    // Verifies profile update returns current profile when patch body is empty.
    @Test
    void updateProfile_ShouldReturnCurrentProfile_WhenNoChangesProvided() {
        UUID userId = UUID.randomUUID();
        Profile current = profile(userId, "name", 0, 0);
        current.setGenAlpha(false);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(current));

        //act
        Profile result = service.updateProfile(userId, null, null, "token");

        //assert
        assertEquals(current, result);

        //verify
        verify(supabaseRestClient, never()).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
    }

    // Verifies profile update rejects invalid display-name format.
    @Test
    void updateProfile_ShouldThrowBadRequest_WhenDisplayNameFormatIsInvalid() {
        UUID userId = UUID.randomUUID();
        Profile current = profile(userId, "name", 0, 0);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(current));

        //act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.updateProfile(userId, "**", true, "token")
        );

        //assert
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, never()).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
    }

    // Verifies profile update blocks duplicate normalized display names.
    @Test
    void updateProfile_ShouldThrowConflict_WhenDisplayNameAlreadyTaken() {
        UUID userId = UUID.randomUUID();
        Profile current = profile(userId, "oldname", 0, 0);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(current));
        when(supabaseRestClient.getList(eq("display_name_registry"), anyString(), eq(null), any())).thenReturn(List.of(new Profile()));

        //act
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.updateProfile(userId, "new-name", null, "token")
        );

        //assert
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());

        //verify
        verify(supabaseRestClient).getList(eq("display_name_registry"), anyString(), eq(null), any());
        verify(supabaseRestClient, never()).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
    }

    // Verifies profile update patches and returns new profile when valid changes exist.
    @Test
    void updateProfile_ShouldPatchAndReturnUpdatedProfile_WhenValidChangesProvided() {
        UUID userId = UUID.randomUUID();
        Profile current = profile(userId, "oldname", 0, 0);
        current.setGenAlpha(false);
        Profile updated = profile(userId, "new-name", 0, 0);
        updated.setGenAlpha(true);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of(current));
        when(supabaseRestClient.getList(eq("display_name_registry"), anyString(), eq(null), any())).thenReturn(List.of());
        when(supabaseRestClient.patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any())).thenReturn(List.of(updated));

        //act
        Profile result = service.updateProfile(userId, "new-name", true, "token");

        //assert
        assertEquals(updated, result);

        //verify
        verify(supabaseRestClient).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any());
    }

    // Verifies getUserBadges combines earned and locked badges with stable sorting.
    @Test
    void getUserBadges_ShouldReturnEarnedThenLockedBadges_WhenDataExists() {
        UUID userId = UUID.randomUUID();
        String earnedLesson = UUID.randomUUID().toString();
        String lockedLesson = UUID.randomUUID().toString();
        OffsetDateTime awardedAt = OffsetDateTime.parse("2026-04-01T10:15:30Z");

        //arrange
        when(supabaseAdminRestClient.getList(eq("user_lesson_rewards"), anyString(), any())).thenReturn(List.of(
            new HashMap<>(Map.of("lesson_id", earnedLesson, "badge_name", "Starter", "awarded_at", awardedAt.toString())),
            new HashMap<>(Map.of("lesson_id", earnedLesson, "badge_name", "Starter", "awarded_at", "invalid"))
        ));
        when(supabaseAdminRestClient.getList(eq("lessons"), anyString(), any()))
            .thenReturn(List.of(Map.of("id", earnedLesson, "title", "Intro", "badge_name", "Starter", "badge_icon_url", "icon-1")))
            .thenReturn(List.of(Map.of("id", lockedLesson, "title", "Advanced", "badge_name", "Master", "badge_icon_url", "icon-2")));

        //act
        List<UserBadgeResponse> result = service.getUserBadges(userId, "token");

        //assert
        assertEquals(2, result.size());
        assertTrue(result.get(0).earned());
        assertEquals("Starter", result.get(0).badgeName());
        assertEquals(awardedAt, result.get(0).earnedAt());
        assertFalse(result.get(1).earned());
        assertEquals("Master", result.get(1).badgeName());

        //verify
        verify(supabaseAdminRestClient, times(2)).getList(eq("lessons"), anyString(), any());
    }

    // Verifies leaderboard excludes admins and computes rank ties correctly.
    @Test
    void getLeaderboard_ShouldExcludeAdminsAndComputeRanks_WhenProfilesExist() {
        UUID currentUser = UUID.randomUUID();
        UUID adminUser = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();

        UserRole adminRole = new UserRole();
        adminRole.setUserId(adminUser);

        Profile p1 = profile(currentUser, "  Zed  ", 100, 3);
        Profile p2 = profile(otherUser, "alpha", 100, -1);
        Profile p3 = profile(adminUser, "admin", 999, 9);

        //arrange
        when(supabaseAdminRestClient.getList(eq("user_roles"), anyString(), any())).thenReturn(List.of(adminRole));
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(List.of(p1, p2, p3));

        //act
        LeaderboardResponse result = service.getLeaderboard(currentUser, 1, 1, " @aL ", "token");

        //assert
        assertEquals(1, result.page());
        assertEquals(1, result.pageSize());
        assertFalse(result.hasNext()); 
        assertEquals(1, result.totalCount());
        assertEquals("al", result.query());
        assertNotNull(result.currentUser());
        assertEquals(1, result.items().size());
        assertEquals(otherUser, result.items().get(0).userId());
        assertEquals(1, result.items().get(0).rank());

        //verify
        verify(supabaseAdminRestClient).getList(eq("user_roles"), anyString(), any());
        verify(supabaseAdminRestClient).getList(eq("profiles"), anyString(), any());
    }

    // Verifies leaderboard normalizes page size and handles empty query filter.
    @Test
    void getLeaderboard_ShouldNormalizePaging_WhenPageSizeIsInvalid() {
        UUID currentUser = UUID.randomUUID();

        //arrange
        when(supabaseAdminRestClient.getList(eq("user_roles"), anyString(), any())).thenReturn(List.of());
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(List.of());

        //act
        LeaderboardResponse result = service.getLeaderboard(currentUser, -9, 0, "   ", "token");

        //assert
        assertEquals(1, result.page());
        assertEquals(20, result.pageSize());
        assertEquals("", result.query());
        assertNull(result.currentUser());

        //verify
        verify(supabaseAdminRestClient).getList(eq("profiles"), anyString(), any());
    }

    // Verifies display-name format validation enforces length and characters.
    @Test
    void isDisplayNameFormatValid_ShouldReturnExpectedResult_WhenInputVaries() {
        //arrange

        //act
        boolean nullResult = service.isDisplayNameFormatValid(null);
        boolean shortResult = service.isDisplayNameFormatValid("ab");
        boolean invalidResult = service.isDisplayNameFormatValid("abc*");
        boolean validResult = service.isDisplayNameFormatValid("abc_123");

        //assert
        assertFalse(nullResult);
        assertFalse(shortResult);
        assertFalse(invalidResult);
        assertTrue(validResult);

        //verify
    }

    // Verifies display-name normalization trims and lowercases values.
    @Test
    void normalizeDisplayName_ShouldTrimAndLowercase_WhenInputHasWhitespaceAndCase() {
        //arrange

        //act
        String normalized = service.normalizeDisplayName("  HeLLo-User  ");

        //assert
        assertEquals("hello-user", normalized);

        //verify
    }

    // Verifies access-token validation fails early for secure methods.
    @Test
    void getRoles_ShouldThrowUnauthorized_WhenAccessTokenMissing() {
        UUID userId = UUID.randomUUID();

        //arrange

        //act
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getRoles(userId, "  "));

        //assert
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());

        //verify
        verify(supabaseRestClient, never()).getList(eq("user_roles"), anyString(), anyString(), any());
    }

    // Verifies ensureProfileWithServiceRole updates existing profile when values differ.
    @Test
    void ensureProfileWithServiceRole_ShouldPatchExistingProfile_WhenValuesDiffer() {
        UUID userId = UUID.randomUUID();
        Profile existing = profile(userId, "before", 0, 0);
        existing.setGenAlpha(false);
        Profile updated = profile(userId, "after", 0, 0);
        updated.setGenAlpha(true);

        //arrange
        when(supabaseAdminRestClient.getList(eq("profiles"), anyString(), any())).thenReturn(List.of(existing));
        when(supabaseAdminRestClient.patchList(eq("profiles"), anyString(), anyMap(), any())).thenReturn(List.of(updated));
        when(supabaseAdminRestClient.getList(eq("user_roles"), anyString(), any())).thenReturn(List.of(new UserRole()));

        //act
        Profile result = service.ensureProfileWithServiceRole(userId, "after", true, true);

        //assert
        assertEquals("after", result.getDisplayName());
        assertTrue(result.getGenAlpha());

        //verify
        verify(supabaseAdminRestClient).patchList(eq("profiles"), anyString(), anyMap(), any());
    }

    // Verifies ensureProfile creates fallback display name when hint is blank.
    @Test
    void getOrCreateProfile_ShouldDefaultDisplayName_WhenHintAndEmailAreMissing() {
        UUID userId = UUID.randomUUID();
        Profile created = profile(userId, "user", 0, 0);

        //arrange
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any())).thenReturn(List.of()).thenReturn(List.of(created));
        when(supabaseRestClient.postList(eq("profiles"), anyMap(), eq("token"), any())).thenReturn(List.of(created));
        when(supabaseRestClient.getList(eq("user_roles"), anyString(), eq("token"), any())).thenReturn(List.of(new UserRole()));

        //act
        Profile result = service.getOrCreateProfile(userId, null, "   ", null, "token");

        //assert
        assertEquals(created, result);

        //verify
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(supabaseRestClient).postList(eq("profiles"), captor.capture(), eq("token"), any());
        assertEquals("user", captor.getValue().get("display_name"));
    }
}
