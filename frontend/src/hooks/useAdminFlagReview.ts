import { useEffect, useState } from 'react';
import { toast } from '@/components/ui/sonner';
import {
  fetchAdminFlagReview,
  fetchAdminFlagReviewReports,
  resolveFlag,
  takeDownFlag,
  type AdminFlagReviewQueryOptions,
} from '@/lib/api';
import type { AdminContentFlagReport, AdminFlagReview } from '@/types';
import {
  ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH,
  normalizeAdminFlagText,
  sanitizeAdminFlagInputValue,
} from '@/components/admin/adminFlagReviewUtils';

type ReviewAction = 'resolve' | 'take-down';

type UseAdminFlagReviewOptions = {
  onReviewMutated?: (contentId: string, action: ReviewAction) => void | Promise<void>;
};

export const useAdminFlagReview = ({ onReviewMutated }: UseAdminFlagReviewOptions = {}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [contentId, setContentId] = useState<string | null>(null);
  const [queryOptions, setQueryOptions] = useState<AdminFlagReviewQueryOptions | undefined>();
  const [review, setReview] = useState<AdminFlagReview | null>(null);
  const [isReviewLoading, setIsReviewLoading] = useState(false);
  const [reports, setReports] = useState<AdminContentFlagReport[]>([]);
  const [isReportsLoading, setIsReportsLoading] = useState(false);
  const [reporterSearch, setReporterSearch] = useState('');
  const [reportsPage, setReportsPage] = useState(1);
  const [reportsHasNext, setReportsHasNext] = useState(false);
  const [isResolveLoading, setIsResolveLoading] = useState(false);
  const [isTakeDownOpen, setIsTakeDownOpen] = useState(false);
  const [takeDownReason, setTakeDownReasonState] = useState('');
  const [takeDownAttempted, setTakeDownAttempted] = useState(false);
  const [isTakeDownLoading, setIsTakeDownLoading] = useState(false);
  const [pendingTakeDownOnLoad, setPendingTakeDownOnLoad] = useState(false);

  const closeReview = () => {
    setIsOpen(false);
    setContentId(null);
    setQueryOptions(undefined);
    setReview(null);
    setReports([]);
    setReporterSearch('');
    setReportsPage(1);
    setReportsHasNext(false);
    setIsTakeDownOpen(false);
    setTakeDownReasonState('');
    setTakeDownAttempted(false);
    setPendingTakeDownOnLoad(false);
  };

  const openReview = (nextContentId: string, options?: AdminFlagReviewQueryOptions) => {
    setContentId(nextContentId);
    setQueryOptions(options);
    setReporterSearch('');
    setReportsPage(1);
    setReportsHasNext(false);
    setReports([]);
    setIsTakeDownOpen(false);
    setTakeDownReasonState('');
    setTakeDownAttempted(false);
    setPendingTakeDownOnLoad(false);
    setIsOpen(true);
  };

  const openTakeDownForContent = (
    nextContentId: string,
    options?: AdminFlagReviewQueryOptions
  ) => {
    setPendingTakeDownOnLoad(true);
    setContentId(nextContentId);
    setQueryOptions(options);
    setReporterSearch('');
    setReportsPage(1);
    setReportsHasNext(false);
    setReports([]);
    setTakeDownReasonState('');
    setTakeDownAttempted(false);
    setIsOpen(true);
  };

  const setTakeDownReason = (value: string) => {
    setTakeDownReasonState(
      sanitizeAdminFlagInputValue(value, ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH)
    );
  };

  useEffect(() => {
    if (!isOpen || !contentId) {
      return;
    }

    let active = true;
    setIsReviewLoading(true);
    fetchAdminFlagReview(contentId, queryOptions)
      .then((nextReview) => {
        if (!active) {
          return;
        }
        setReview(nextReview);
        if (pendingTakeDownOnLoad) {
          if (nextReview.canTakeDown) {
            setIsTakeDownOpen(true);
          } else {
            toast('No open moderation action is available for this item', {
              position: 'bottom-center',
            });
          }
          setPendingTakeDownOnLoad(false);
        }
      })
      .catch((error) => {
        console.warn('Failed to load flag review', error);
        if (active) {
          toast('Failed to load flag review', { position: 'bottom-center' });
          closeReview();
        }
      })
      .finally(() => {
        if (active) {
          setIsReviewLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [isOpen, contentId, pendingTakeDownOnLoad, queryOptions?.month, queryOptions?.year]);

  useEffect(() => {
    if (!isOpen || !contentId) {
      return;
    }

    let active = true;
    setIsReportsLoading(true);
    fetchAdminFlagReviewReports(contentId, reportsPage, reporterSearch, queryOptions)
      .then((response) => {
        if (!active) {
          return;
        }
        setReports(response.items ?? []);
        setReportsHasNext(Boolean(response.has_next));
      })
      .catch((error) => {
        console.warn('Failed to load flag reports', error);
        if (active) {
          setReports([]);
          setReportsHasNext(false);
        }
      })
      .finally(() => {
        if (active) {
          setIsReportsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [isOpen, contentId, reportsPage, reporterSearch, queryOptions?.month, queryOptions?.year]);

  const handleResolve = async () => {
    if (!review?.actionableFlagId || !contentId) {
      return;
    }
    try {
      setIsResolveLoading(true);
      await resolveFlag(review.actionableFlagId);
      await onReviewMutated?.(contentId, 'resolve');
      closeReview();
    } catch (error) {
      console.warn('Resolve flag failed', error);
      toast('Failed to resolve flag', { position: 'bottom-center' });
    } finally {
      setIsResolveLoading(false);
    }
  };

  const resolveContent = async (
    nextContentId: string,
    options?: AdminFlagReviewQueryOptions
  ) => {
    try {
      setIsResolveLoading(true);
      const nextReview = await fetchAdminFlagReview(nextContentId, options);
      if (!nextReview.actionableFlagId) {
        toast('No open moderation action is available for this item', {
          position: 'bottom-center',
        });
        return;
      }
      await resolveFlag(nextReview.actionableFlagId);
      await onReviewMutated?.(nextContentId, 'resolve');
      if (contentId === nextContentId) {
        closeReview();
      }
    } catch (error) {
      console.warn('Resolve flag failed', error);
      toast('Failed to resolve flag', { position: 'bottom-center' });
    } finally {
      setIsResolveLoading(false);
    }
  };

  const openTakeDown = () => {
    setIsTakeDownOpen(true);
    setTakeDownReasonState('');
    setTakeDownAttempted(false);
  };

  const closeTakeDown = () => {
    if (isTakeDownLoading) {
      return;
    }
    setIsTakeDownOpen(false);
    setTakeDownReasonState('');
    setTakeDownAttempted(false);
  };

  const confirmTakeDown = async () => {
    if (!review?.actionableFlagId || !contentId) {
      return;
    }
    const feedback = normalizeAdminFlagText(
      takeDownReason,
      ADMIN_FLAG_TAKEDOWN_REASON_MAX_LENGTH
    );
    if (!feedback) {
      setTakeDownAttempted(true);
      return;
    }
    try {
      setIsTakeDownLoading(true);
      await takeDownFlag(review.actionableFlagId, feedback);
      await onReviewMutated?.(contentId, 'take-down');
      closeReview();
    } catch (error) {
      console.warn('Flag take down failed', error);
      toast('Failed to take down flagged content', { position: 'bottom-center' });
    } finally {
      setIsTakeDownLoading(false);
    }
  };

  return {
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
    openReview,
    openTakeDownForContent,
    closeReview,
    setReporterSearch: (value: string) => {
      setReporterSearch(sanitizeAdminFlagInputValue(value, 80));
      setReportsPage(1);
    },
    previousReportsPage: () => setReportsPage((current) => Math.max(1, current - 1)),
    nextReportsPage: () => setReportsPage((current) => current + 1),
    handleResolve,
    resolveContent,
    openTakeDown,
    closeTakeDown,
    setTakeDownReason,
    confirmTakeDown,
  };
};
