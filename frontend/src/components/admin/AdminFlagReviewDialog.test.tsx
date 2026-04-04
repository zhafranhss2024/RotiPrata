import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { AdminFlagReviewDialog } from '@/components/admin/AdminFlagReviewDialog';
import { useAdminFlagReview } from '@/hooks/useAdminFlagReview';
import type { AdminContentFlagReport, AdminFlagReview, Content } from '@/types';

const toastMock = vi.fn();
const fetchAdminFlagReviewMock = vi.fn();
const fetchAdminFlagReviewReportsMock = vi.fn();
const resolveFlagMock = vi.fn();
const takeDownFlagMock = vi.fn();

vi.mock('@/components/ui/sonner', () => ({
  toast: (...args: unknown[]) => toastMock(...args),
}));

vi.mock('@/lib/api', () => ({
  fetchAdminFlagReview: (...args: unknown[]) => fetchAdminFlagReviewMock(...args),
  fetchAdminFlagReviewReports: (...args: unknown[]) => fetchAdminFlagReviewReportsMock(...args),
  resolveFlag: (...args: unknown[]) => resolveFlagMock(...args),
  takeDownFlag: (...args: unknown[]) => takeDownFlagMock(...args),
}));

const createContent = (overrides: Partial<Content> = {}): Content =>
  ({
    id: 'content-1',
    title: 'Flagged Roti Post',
    description: 'Needs review',
    content_type: 'article',
    status: 'approved',
    creator: {
      display_name: 'creator-user',
    },
    ...overrides,
  } as Content);

const createReview = (overrides: Partial<AdminFlagReview> = {}): AdminFlagReview => ({
  contentId: 'content-1',
  content: createContent(),
  status: 'pending',
  reportCount: 2,
  notesCount: 1,
  reasons: ['Inappropriate content'],
  latestReportAt: '2026-04-01T12:00:00.000Z',
  actionableFlagId: 'flag-1',
  canResolve: true,
  canTakeDown: true,
  ...overrides,
});

const createReport = (overrides: Partial<AdminContentFlagReport> = {}): AdminContentFlagReport => ({
  id: 'report-1',
  reported_by: 'reporter-1',
  reporter: {
    user_id: 'reporter-1',
    display_name: 'rotiadmin',
    avatar_url: null,
  },
  reason: 'Inappropriate content',
  description: 'Please review this',
  created_at: '2026-04-01T12:00:00.000Z',
  ...overrides,
});

type HarnessProps = {
  onReviewMutated?: (contentId: string, action: 'resolve' | 'take-down') => void | Promise<void>;
};

const Harness = ({ onReviewMutated = vi.fn() }: HarnessProps) => {
  const [mutationCount, setMutationCount] = useState(0);
  const review = useAdminFlagReview({
    onReviewMutated: async (contentId, action) => {
      await onReviewMutated(contentId, action);
      setMutationCount((current) => current + 1);
    },
  });

  return (
    <div>
      <button type="button" onClick={() => review.openReview('content-1')}>
        Open dashboard review
      </button>
      <button
        type="button"
        onClick={() => review.openReview('content-1', { month: '04', year: '2026' })}
      >
        Open analytics review
      </button>
      <button type="button" onClick={() => review.openTakeDownForContent('content-1')}>
        Quick take down
      </button>
      <button type="button" onClick={review.closeReview}>
        Force close
      </button>
      <span>Mutations: {mutationCount}</span>
      <AdminFlagReviewDialog
        isOpen={review.isOpen}
        review={review.review}
        isReviewLoading={review.isReviewLoading}
        reports={review.reports}
        isReportsLoading={review.isReportsLoading}
        reporterSearch={review.reporterSearch}
        reportsPage={review.reportsPage}
        reportsHasNext={review.reportsHasNext}
        isResolveLoading={review.isResolveLoading}
        isTakeDownOpen={review.isTakeDownOpen}
        takeDownReason={review.takeDownReason}
        takeDownAttempted={review.takeDownAttempted}
        isTakeDownLoading={review.isTakeDownLoading}
        onClose={review.closeReview}
        onReporterSearchChange={review.setReporterSearch}
        onPreviousReportsPage={review.previousReportsPage}
        onNextReportsPage={review.nextReportsPage}
        onResolve={review.handleResolve}
        onOpenTakeDown={review.openTakeDown}
        onCloseTakeDown={review.closeTakeDown}
        onTakeDownReasonChange={review.setTakeDownReason}
        onConfirmTakeDown={review.confirmTakeDown}
      />
    </div>
  );
};

describe('AdminFlagReviewDialog', () => {
  let currentReview: AdminFlagReview;

  beforeEach(() => {
    toastMock.mockReset();
    fetchAdminFlagReviewMock.mockReset();
    fetchAdminFlagReviewReportsMock.mockReset();
    resolveFlagMock.mockReset();
    takeDownFlagMock.mockReset();

    currentReview = createReview();
    fetchAdminFlagReviewMock.mockImplementation(async () => currentReview);
    fetchAdminFlagReviewReportsMock.mockImplementation(
      async (_contentId: string, page: number, query: string) => ({
        items:
          page === 1
            ? [
                createReport(),
                createReport({
                  id: 'report-2',
                  reported_by: 'reporter-2',
                  reporter: {
                    user_id: 'reporter-2',
                    display_name: 'xinyi',
                    avatar_url: null,
                  },
                  description: '',
                }),
              ].filter((report) => {
                if (!query.trim()) {
                  return true;
                }
                return (
                  report.reporter?.display_name?.toLowerCase().includes(query.trim().toLowerCase()) ??
                  false
                );
              })
            : [createReport({ id: `report-page-${page}` })],
        page,
        page_size: 5,
        has_next: page < 2,
        query,
      })
    );
    resolveFlagMock.mockResolvedValue(undefined);
    takeDownFlagMock.mockResolvedValue(undefined);
  });

  it('opens the shared review UI from both dashboard and analytics', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open dashboard review' }));
    expect(await screen.findByText('Flag Review')).toBeInTheDocument();
    expect(await screen.findByText('Submitted reports')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Force close'));

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics review' }));
    expect(await screen.findByText('Flagged Roti Post')).toBeInTheDocument();

    await waitFor(() => {
      expect(fetchAdminFlagReviewMock).toHaveBeenLastCalledWith('content-1', {
        month: '04',
        year: '2026',
      });
    });
  });

  it('shows loading state while review data is being fetched', async () => {
    let resolveFetch: ((value: AdminFlagReview) => void) | null = null;
    fetchAdminFlagReviewMock.mockImplementationOnce(
      () =>
        new Promise<AdminFlagReview>((resolve) => {
          resolveFetch = resolve;
        })
    );

    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open dashboard review' }));
    expect(await screen.findByText('Loading flag review...')).toBeInTheDocument();

    resolveFetch?.(currentReview);

    await waitFor(() => {
      expect(screen.getByText('Submitted reports')).toBeInTheDocument();
    });
  });

  it('forwards analytics filters to shared report loading and pagination', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics review' }));
    await screen.findByText('Submitted reports');

    await waitFor(() => {
      expect(fetchAdminFlagReviewReportsMock).toHaveBeenCalledWith(
        'content-1',
        1,
        '',
        { month: '04', year: '2026' }
      );
    });

    fireEvent.change(screen.getByLabelText('Search reporter'), {
      target: { value: 'xinyi' },
    });

    await waitFor(() => {
      expect(fetchAdminFlagReviewReportsMock).toHaveBeenCalledWith(
        'content-1',
        1,
        'xinyi',
        { month: '04', year: '2026' }
      );
    });

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    await waitFor(() => {
      expect(fetchAdminFlagReviewReportsMock).toHaveBeenCalledWith(
        'content-1',
        2,
        'xinyi',
        { month: '04', year: '2026' }
      );
    });
  });

  it('resolves through the shared controller and notifies the parent', async () => {
    const onReviewMutated = vi.fn();
    render(<Harness onReviewMutated={onReviewMutated} />);

    fireEvent.click(screen.getByRole('button', { name: 'Open dashboard review' }));
    await screen.findByText('Flagged Roti Post');

    fireEvent.click(screen.getByRole('button', { name: 'Resolve' }));

    await waitFor(() => {
      expect(resolveFlagMock).toHaveBeenCalledWith('flag-1');
      expect(onReviewMutated).toHaveBeenCalledWith('content-1', 'resolve');
      expect(screen.getByText('Mutations: 1')).toBeInTheDocument();
    });
  });

  it('disables actions for historical non-actionable items', async () => {
    currentReview = createReview({
      status: 'resolved',
      actionableFlagId: null,
      canResolve: false,
      canTakeDown: false,
    });

    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics review' }));

    expect(
      await screen.findByText('No open moderation action is available for this historical flag item.')
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Resolve' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Take down' })).toBeDisabled();
  });

  it('resets take-down state when the shared review closes', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Quick take down' }));
    expect(await screen.findByText('Take Down Flagged Content')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^Take down$/ }));
    expect(await screen.findByText('A takedown reason is required.')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Force close'));

    await waitFor(() => {
      expect(screen.queryByText('Take Down Flagged Content')).not.toBeInTheDocument();
      expect(screen.queryByText('A takedown reason is required.')).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Quick take down' }));
    expect(await screen.findByText('Take Down Flagged Content')).toBeInTheDocument();
    expect(screen.queryByText('A takedown reason is required.')).not.toBeInTheDocument();
  });
});
