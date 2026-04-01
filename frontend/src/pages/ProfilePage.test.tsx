import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ProfilePage from "@/pages/ProfilePage";
import type { Profile } from "@/types";

const fetchProfile = vi.fn();
const fetchUserBadges = vi.fn();
const fetchUserStats = vi.fn();
const fetchLeaderboard = vi.fn();
const fetchProfileContentCollection = vi.fn();

vi.mock("@/lib/api", () => ({
  fetchProfile: (...args: unknown[]) => fetchProfile(...args),
  fetchUserBadges: (...args: unknown[]) => fetchUserBadges(...args),
  fetchUserStats: (...args: unknown[]) => fetchUserStats(...args),
  fetchLeaderboard: (...args: unknown[]) => fetchLeaderboard(...args),
  fetchProfileContentCollection: (...args: unknown[]) => fetchProfileContentCollection(...args),
}));

vi.mock("@/contexts/AuthContext", () => ({
  useAuthContext: () => ({
    isAuthenticated: true,
    isAdmin: () => false,
  }),
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/feed/ContentDetailSheet", () => ({
  ContentDetailSheet: () => null,
}));

vi.mock("@/components/profile/ProfileContentTile", () => ({
  ProfileContentTile: () => <div>Profile content tile</div>,
}));

const profile: Profile = {
  id: "profile-1",
  user_id: "user-1",
  display_name: "Brain Rot Learner",
  avatar_url: null,
  bio: "bio",
  date_of_birth: null,
  is_gen_alpha: false,
  theme_preference: "system",
  is_verified: false,
  reputation_points: 250,
  current_streak: 5,
  longest_streak: 7,
  last_activity_date: null,
  total_hours_learned: 4,
  created_at: new Date().toISOString(),
  updated_at: new Date().toISOString(),
};

describe("ProfilePage", () => {
  beforeEach(() => {
    fetchProfile.mockReset();
    fetchUserBadges.mockReset();
    fetchUserStats.mockReset();
    fetchLeaderboard.mockReset();
    fetchProfileContentCollection.mockReset();

    fetchProfile.mockResolvedValue(profile);
    fetchUserBadges.mockResolvedValue([]);
    fetchUserStats.mockResolvedValue({
      lessonsEnrolled: 4,
      lessonsCompleted: 2,
      quizzesTaken: 3,
      hoursLearned: 1,
    });
    fetchLeaderboard.mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 1,
      hasNext: true,
      totalCount: 10,
      query: "",
      currentUser: {
        rank: 5,
        userId: "user-1",
        displayName: "Brain Rot Learner",
        avatarUrl: null,
        xp: 250,
        currentStreak: 5,
        isCurrentUser: true,
      },
    });
    fetchProfileContentCollection.mockResolvedValue([]);
  });

  it("shows the signed-in user's leaderboard rank on the profile tile", async () => {
    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>
    );

    await waitFor(() => expect(fetchLeaderboard).toHaveBeenCalledWith(1, 1, ""));
    expect(await screen.findByText("#5")).toBeInTheDocument();
    expect(screen.getByText("Global rank")).toBeInTheDocument();
  });
});
