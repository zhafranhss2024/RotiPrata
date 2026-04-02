import type { ReactNode } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LeaderboardPage from "@/pages/LeaderboardPage";
import type { LeaderboardResponse } from "@/types";

const fetchLeaderboard = vi.fn();

vi.mock("@/lib/api", () => ({
  fetchLeaderboard: (...args: unknown[]) => fetchLeaderboard(...args),
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

const buildResponse = (overrides: Partial<LeaderboardResponse> = {}): LeaderboardResponse => ({
  items: [
    {
      rank: 1,
      userId: "user-2",
      displayName: "xpqueen",
      avatarUrl: null,
      xp: 900,
      currentStreak: 9,
      isCurrentUser: false,
    },
    {
      rank: 2,
      userId: "user-1",
      displayName: "brainrotlearner",
      avatarUrl: null,
      xp: 250,
      currentStreak: 5,
      isCurrentUser: true,
    },
  ],
  page: 1,
  pageSize: 20,
  hasNext: false,
  totalCount: 2,
  query: "",
  currentUser: {
    rank: 2,
    userId: "user-1",
    displayName: "brainrotlearner",
    avatarUrl: null,
    xp: 250,
    currentStreak: 5,
    isCurrentUser: true,
  },
  ...overrides,
});

describe("LeaderboardPage", () => {
  beforeEach(() => {
    fetchLeaderboard.mockReset();
  });

  it("renders the current user summary and leaderboard rows", async () => {
    fetchLeaderboard.mockResolvedValue(buildResponse());

    render(
      <MemoryRouter>
        <LeaderboardPage />
      </MemoryRouter>
    );

    await waitFor(() => expect(fetchLeaderboard).toHaveBeenCalledWith(1, 20, ""));
    expect(screen.getByText("XP Leaderboard")).toBeInTheDocument();
    expect(screen.getAllByText("#2").length).toBeGreaterThan(0);
    expect(screen.getAllByText("brainrotlearner").length).toBeGreaterThan(0);
    expect(screen.getAllByText("xpqueen").length).toBeGreaterThan(0);
  });

  it("resets pagination to page one when the search query changes", async () => {
    fetchLeaderboard
      .mockResolvedValueOnce(buildResponse({ hasNext: true }))
      .mockResolvedValueOnce(buildResponse({ page: 2, hasNext: false }))
      .mockResolvedValueOnce(buildResponse({ page: 1, hasNext: false }))
      .mockResolvedValueOnce(buildResponse({ query: "brain", items: [buildResponse().items[1]], totalCount: 1 }));

    render(
      <MemoryRouter>
        <LeaderboardPage />
      </MemoryRouter>
    );

    await waitFor(() => expect(fetchLeaderboard).toHaveBeenCalledWith(1, 20, ""));
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(fetchLeaderboard).toHaveBeenCalledWith(2, 20, ""));

    fireEvent.change(screen.getByPlaceholderText("Search by username"), {
      target: { value: "brain" },
    });

    await waitFor(() => expect(fetchLeaderboard).toHaveBeenCalledWith(1, 20, "brain"));
  });
});
