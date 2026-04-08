import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi, beforeEach } from "vitest";
import ContentFeedPage from "@/pages/ContentFeedPage";
import type { Content } from "@/types";

const fetchContentById = vi.fn();
const fetchSimilarContent = vi.fn();
const feedContainerSpy = vi.fn();

vi.mock("@/lib/api", () => ({
  SIMILAR_CONTENT_LIMIT: 6,
  fetchContentById: (...args: unknown[]) => fetchContentById(...args),
  fetchSimilarContent: (...args: unknown[]) => fetchSimilarContent(...args),
}));

vi.mock("@/components/layout/MainLayout", () => ({
  MainLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/feed/FeedContainer", () => ({
  FeedContainer: (props: unknown) => {
    feedContainerSpy(props);
    return <div data-testid="feed-container" />;
  },
}));

const buildVideo = (id: string): Content => ({
  id,
  creator_id: `creator-${id}`,
  title: `Video ${id}`,
  description: `Description ${id}`,
  content_type: "video",
  media_url: `https://cdn.example.com/${id}.mp4`,
  thumbnail_url: `https://cdn.example.com/${id}.jpg`,
  category_id: null,
  status: "approved",
  learning_objective: null,
  origin_explanation: null,
  definition_literal: null,
  definition_used: null,
  older_version_reference: null,
  educational_value_votes: 0,
  view_count: 0,
  is_featured: false,
  reviewed_by: null,
  reviewed_at: null,
  review_feedback: null,
  created_at: "2026-03-24T10:00:00.000Z",
  updated_at: "2026-03-24T10:00:00.000Z",
});

describe("ContentFeedPage", () => {
  beforeEach(() => {
    fetchContentById.mockReset();
    fetchSimilarContent.mockReset();
    feedContainerSpy.mockReset();
  });

  it("builds a finite selected-video-plus-similar queue and disables infinite feed loading", async () => {
    const selected = buildVideo("selected");
    const similar = [buildVideo("similar-1"), buildVideo("selected"), buildVideo("similar-2")];
    fetchContentById.mockResolvedValue(selected);
    fetchSimilarContent.mockResolvedValue(similar);

    render(
      <MemoryRouter initialEntries={["/content/selected"]}>
        <Routes>
          <Route path="/content/:id" element={<ContentFeedPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(fetchContentById).toHaveBeenCalledWith("selected"));
    await waitFor(() => expect(fetchSimilarContent).toHaveBeenCalledWith("selected", 6));
    await waitFor(() => expect(screen.getByTestId("feed-container")).toBeInTheDocument());

    const lastProps = feedContainerSpy.mock.calls[feedContainerSpy.mock.calls.length - 1]?.[0] as {
      contents: Content[];
      hasMore: boolean;
      initialIndex: number;
      containerClassName?: string;
    };

    expect(lastProps.contents.map((content) => content.id)).toEqual([
      "selected",
      "similar-1",
      "similar-2",
    ]);
    expect(lastProps.hasMore).toBe(false);
    expect(lastProps.initialIndex).toBe(0);
    expect(lastProps.containerClassName).toBe("h-full");
    expect(screen.getByLabelText("Back")).toBeInTheDocument();
  });

  it("uses route-state queue contents and initial index without refetching", async () => {
    const queueContents = [buildVideo("alpha"), buildVideo("beta"), buildVideo("gamma")];

    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: "/content/beta",
            state: {
              queueContents,
              initialIndex: 1,
              returnTo: "/",
              backLabel: "Back to Feed",
            },
          },
        ]}
      >
        <Routes>
          <Route path="/content/:id" element={<ContentFeedPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.getByTestId("feed-container")).toBeInTheDocument());
    expect(fetchContentById).not.toHaveBeenCalled();
    expect(fetchSimilarContent).not.toHaveBeenCalled();

    const lastProps = feedContainerSpy.mock.calls[feedContainerSpy.mock.calls.length - 1]?.[0] as {
      contents: Content[];
      hasMore: boolean;
      initialIndex: number;
      containerClassName?: string;
    };

    expect(lastProps.contents.map((content) => content.id)).toEqual(["alpha", "beta", "gamma"]);
    expect(lastProps.initialIndex).toBe(1);
    expect(lastProps.hasMore).toBe(false);
    expect(lastProps.containerClassName).toBe("h-full");
    expect(screen.getByLabelText("Back to Feed")).toBeInTheDocument();
  });
});
