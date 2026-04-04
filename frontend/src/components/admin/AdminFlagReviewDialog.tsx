import { Loader2 } from 'lucide-react';
import {
  ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH,
  formatAdminFlagContentStatus,
  getFlagActionUnavailableMessage,
  getFlagContentTitle,
  getFlagCreatorDisplayName,
  normalizeAdminFlagText,
} from '@/components/admin/adminFlagReviewUtils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { AdminContentFlagReport, AdminFlagReview } from '@/types';
import { getFlagReasonSummary } from '@/pages/admin/flagModeration';

type Props = {
  isOpen: boolean;
  review: AdminFlagReview | null;
  isReviewLoading: boolean;
  reports: AdminContentFlagReport[];
  isReportsLoading: boolean;
  reporterSearch: string;
  reportsPage: number;
  reportsHasNext: boolean;
  isResolveLoading: boolean;
  isTakeDownOpen: boolean;
  takeDownReason: string;
  takeDownAttempted: boolean;
  isTakeDownLoading: boolean;
  onClose: () => void;
  onReporterSearchChange: (value: string) => void;
  onPreviousReportsPage: () => void;
  onNextReportsPage: () => void;
  onResolve: () => void | Promise<void>;
  onOpenTakeDown: () => void;
  onCloseTakeDown: () => void;
  onTakeDownReasonChange: (value: string) => void;
  onConfirmTakeDown: () => void | Promise<void>;
};

const reviewToReasonSummary = (review: AdminFlagReview) =>
  getFlagReasonSummary({
    id: review.actionableFlagId ?? review.contentId,
    content_id: review.contentId,
    status: review.status ?? 'unknown',
    created_at: review.latestReportAt ?? '',
    report_count: review.reportCount,
    notes_count: review.notesCount,
    reasons: review.reasons,
  });

export const AdminFlagReviewDialog = ({
  isOpen,
  review,
  isReviewLoading,
  reports,
  isReportsLoading,
  reporterSearch,
  reportsPage,
  reportsHasNext,
  isResolveLoading,
  isTakeDownOpen,
  takeDownReason,
  takeDownAttempted,
  isTakeDownLoading,
  onClose,
  onReporterSearchChange,
  onPreviousReportsPage,
  onNextReportsPage,
  onResolve,
  onOpenTakeDown,
  onCloseTakeDown,
  onTakeDownReasonChange,
  onConfirmTakeDown,
}: Props) => {
  const actionUnavailableMessage = getFlagActionUnavailableMessage(review);
  const canModerate = Boolean(review?.canResolve || review?.canTakeDown);

  return (
    <>
      <Dialog
        open={isOpen}
        onOpenChange={(open) => {
          if (!open) {
            onClose();
          }
        }}
      >
        <DialogContent className="max-h-[calc(100dvh-1.5rem)] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Flag Review</DialogTitle>
            <DialogDescription>
              Review the reported post, reporter context, and moderation action.
            </DialogDescription>
          </DialogHeader>
          {isReviewLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading flag review...
            </div>
          ) : review ? (
            <div className="grid gap-6">
              <div className="grid gap-3 rounded-lg border border-border/70 p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="destructive">{reviewToReasonSummary(review)}</Badge>
                  <Badge variant="secondary">{review.reportCount} reports</Badge>
                  {review.content?.content_type ? (
                    <Badge variant="outline" className="capitalize">
                      {review.content.content_type}
                    </Badge>
                  ) : null}
                  {review.content?.status ? (
                    <Badge variant="secondary">
                      {formatAdminFlagContentStatus(review.content.status)}
                    </Badge>
                  ) : null}
                  {review.status && review.status.toLowerCase() !== 'pending' ? (
                    <Badge variant="outline" className="capitalize">
                      {review.status}
                    </Badge>
                  ) : null}
                </div>
                <div>
                  <p className="text-base font-semibold">
                    {getFlagContentTitle(review.content, review.contentId)}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Creator: @{getFlagCreatorDisplayName(review.content)}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Latest report on{' '}
                    {review.latestReportAt ? new Date(review.latestReportAt).toLocaleString() : 'Unknown'}
                  </p>
                </div>
                <div className="grid gap-3 rounded-md bg-muted/40 p-3">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                      <p className="text-sm font-medium">Submitted reports</p>
                      <p className="text-xs text-muted-foreground">
                        Showing {reports.length} of {review.reportCount} reports
                      </p>
                    </div>
                    <div className="grid gap-1">
                      <Label htmlFor="shared-flag-report-search">Search reporter</Label>
                      <Input
                        id="shared-flag-report-search"
                        value={reporterSearch}
                        onChange={(event) => onReporterSearchChange(event.target.value)}
                        placeholder="Search by username"
                      />
                    </div>
                  </div>
                  <div className="grid gap-3">
                    {isReportsLoading ? (
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        Loading reports...
                      </div>
                    ) : reports.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        {reporterSearch.trim()
                          ? 'No reports match that username.'
                          : 'No submitted reports found.'}
                      </p>
                    ) : (
                      reports.map((report, index) => (
                        <div
                          key={report.id}
                          className="rounded-md border border-border/70 bg-background px-3 py-3"
                        >
                          <div className="flex flex-wrap items-center gap-2">
                            <Badge
                              variant="destructive"
                              className="max-w-full whitespace-normal break-words leading-4"
                            >
                              {report.reason}
                            </Badge>
                            <Badge variant="outline">
                              Report {(reportsPage - 1) * 5 + index + 1}
                            </Badge>
                            <span className="text-xs text-muted-foreground">
                              {new Date(report.created_at).toLocaleString()}
                            </span>
                          </div>
                          <p className="mt-2 break-words text-xs text-muted-foreground">
                            Reporter: @{report.reporter?.display_name ?? report.reported_by}
                          </p>
                          <p className="mt-2 break-words text-sm text-muted-foreground">
                            {normalizeAdminFlagText(
                              report.description ?? '',
                              ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH
                            )
                              ? `Reporter note: ${report.description}`
                              : 'No additional reporter note.'}
                          </p>
                        </div>
                      ))
                    )}
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs text-muted-foreground">Page {reportsPage}</p>
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isReportsLoading || reportsPage <= 1}
                        onClick={onPreviousReportsPage}
                      >
                        Previous
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isReportsLoading || !reportsHasNext}
                        onClick={onNextReportsPage}
                      >
                        Next
                      </Button>
                    </div>
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-muted/30 p-3">
                  {review.content?.content_type === 'video' && review.content.media_url ? (
                    <video
                      controls
                      src={review.content.media_url}
                      className="w-full max-h-[360px] rounded-md bg-black"
                    />
                  ) : review.content?.media_url ? (
                    <img
                      src={review.content.media_url}
                      alt={review.content.title}
                      className="w-full max-h-[360px] rounded-md object-contain bg-muted"
                    />
                  ) : review.content?.thumbnail_url ? (
                    <img
                      src={review.content.thumbnail_url}
                      alt={review.content.title ?? 'Flagged content preview'}
                      className="w-full max-h-[360px] rounded-md object-contain bg-muted"
                    />
                  ) : (
                    <p className="text-sm text-muted-foreground">No media preview available.</p>
                  )}
                </div>
                {actionUnavailableMessage ? (
                  <p className="text-sm text-muted-foreground">{actionUnavailableMessage}</p>
                ) : null}
              </div>

              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  disabled={!review.canResolve || isResolveLoading || isTakeDownLoading}
                  onClick={() => {
                    void onResolve();
                  }}
                >
                  {isResolveLoading ? 'Resolving...' : 'Resolve'}
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  disabled={!review.canTakeDown || isResolveLoading || isTakeDownLoading || !canModerate}
                  onClick={onOpenTakeDown}
                >
                  Take down
                </Button>
              </DialogFooter>
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={isTakeDownOpen}
        onOpenChange={(open) => {
          if (!open) {
            onCloseTakeDown();
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Take Down Flagged Content</DialogTitle>
            <DialogDescription>
              This will mark the post as taken down and show your feedback to the creator in their Posted videos.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-2">
            <Label htmlFor="shared-flag-takedown-reason">Reason for creator</Label>
            <Textarea
              id="shared-flag-takedown-reason"
              value={takeDownReason}
              onChange={(event) => onTakeDownReasonChange(event.target.value)}
              maxLength={ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH}
              rows={4}
            />
            {takeDownAttempted &&
            !normalizeAdminFlagText(
              takeDownReason,
              ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH
            ) ? (
              <p className="text-xs text-destructive">A takedown reason is required.</p>
            ) : null}
            <p className="text-xs text-muted-foreground">
              {takeDownReason.length}/{ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH}
            </p>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={isTakeDownLoading}
              onClick={onCloseTakeDown}
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              disabled={isTakeDownLoading}
              onClick={() => {
                void onConfirmTakeDown();
              }}
            >
              {isTakeDownLoading ? 'Taking down...' : 'Take down'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};
