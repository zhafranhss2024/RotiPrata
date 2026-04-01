import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import type { ReactNode } from "react";
import type { Content } from "@/types";
import { ContentDetailSheet } from "./ContentDetailSheet";

const fetchSimilarContent = vi.fn();
const navigateMock = vi.fn();

vi.mock("@/lib/api", () => ({
  SIMILAR_CONTENT_LIMIT: 6,
  fetchSimilarContent: (...args: unknown[]) => fetchSimilarContent(...args),
}));

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual<typeof import("react-router-dom")>("react-router-dom");
  return {
    ...actual,
    useNavigate: () => navigateMock,
    useLocation: () => ({ pathname: "/", search: "", state: null }),
    Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
  };
});

const buildContent = (id: string): Content => ({
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

describe("ContentDetailSheet", () => {
  beforeEach(() => {
    fetchSimilarContent.mockReset();
    navigateMock.mockReset();
  });

  it("navigates with the current similar queue and tapped index", async () => {
    const content = buildContent("current");
    const similar = [buildContent("one"), buildContent("two"), buildContent("three")];
    fetchSimilarContent.mockResolvedValue(similar);

    render(<ContentDetailSheet content={content} open onOpenChange={() => undefined} />);

    await waitFor(() => expect(fetchSimilarContent).toHaveBeenCalledWith("current", 6));
    fireEvent.click(screen.getByRole("button", { name: /video two/i }));

    expect(navigateMock).toHaveBeenCalledWith("/content/two", {
      state: {
        queueContents: similar,
        initialIndex: 1,
        returnTo: "/",
        backLabel: "Back to Feed",
      },
    });
  });
});
