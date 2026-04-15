import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { HeartsIndicator } from "@/components/layout/HeartsIndicator";

const fetchUserHearts = vi.fn();
const subscribeToHeartsUpdated = vi.fn(() => () => undefined);

vi.mock("@/lib/api", () => ({
  fetchUserHearts: (...args: unknown[]) => fetchUserHearts(...args),
}));

vi.mock("@/lib/heartsEvents", () => ({
  subscribeToHeartsUpdated: (...args: unknown[]) => subscribeToHeartsUpdated(...args),
}));

describe("HeartsIndicator", () => {
  beforeEach(() => {
    fetchUserHearts.mockReset();
    subscribeToHeartsUpdated.mockClear();
  });

  it("does not show a refill tooltip when hearts are already full", async () => {
    fetchUserHearts.mockResolvedValue({
      heartsRemaining: 5,
      heartsRefillAt: "2026-04-16T06:00:00.000Z",
    });

    render(
      <MemoryRouter>
        <HeartsIndicator />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("5")).toBeInTheDocument();
    });

    expect(screen.getByText("5").closest("div")).toHaveAttribute("title", "Hearts ready");
  });

  it("shows a refill tooltip when hearts are below max", async () => {
    fetchUserHearts.mockResolvedValue({
      heartsRemaining: 3,
      heartsRefillAt: "2026-04-16T06:00:00.000Z",
    });

    render(
      <MemoryRouter>
        <HeartsIndicator />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("3")).toBeInTheDocument();
    });

    expect(screen.getByText("3").closest("div")?.getAttribute("title")).toMatch(/^Refill at /);
  });
});
