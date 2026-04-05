import { useState } from 'react';
import { toast } from '@/components/ui/sonner';
import {
  deleteContentComment,
  fetchAdminUserDetail,
  rejectContent,
  resetAdminUserLessonProgress,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '@/lib/api';
import type { AdminUserDetail, AdminUserSummary, AppRole, Content } from '@/types';
import {
  ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH,
  getRoleChangeGuardReason,
  normalizeAdminUserText,
  sanitizeAdminUserInputValue,
} from '@/components/admin/adminUserManagementUtils';

type UseAdminUserManagementOptions = {
  currentAdminUserId: string | null;
  adminCount: number;
  findUserSummary?: (userId: string) => AdminUserSummary | null;
  onUserSummaryUpdated?: (summary: AdminUserSummary) => void;
};

const resettableRejectState = {
  userContentRejectTarget: null as Content | null,
  userContentRejectReason: '',
  userContentRejectAttempted: false,
};

export const useAdminUserManagement = ({
  currentAdminUserId,
  adminCount,
  findUserSummary,
  onUserSummaryUpdated,
}: UseAdminUserManagementOptions) => {
  const [selectedUser, setSelectedUser] = useState<AdminUserDetail | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [userActionKey, setUserActionKey] = useState<string | null>(null);
  const [userContentRejectTarget, setUserContentRejectTarget] = useState<Content | null>(
    resettableRejectState.userContentRejectTarget
  );
  const [userContentRejectReason, setUserContentRejectReasonState] = useState(
    resettableRejectState.userContentRejectReason
  );
  const [userContentRejectAttempted, setUserContentRejectAttempted] = useState(
    resettableRejectState.userContentRejectAttempted
  );

  const resetRejectState = () => {
    setUserContentRejectTarget(resettableRejectState.userContentRejectTarget);
    setUserContentRejectReasonState(resettableRejectState.userContentRejectReason);
    setUserContentRejectAttempted(resettableRejectState.userContentRejectAttempted);
  };

  const closeUser = () => {
    setIsOpen(false);
    setSelectedUser(null);
    resetRejectState();
  };

  const loadUserDetail = async (userId: string, keepDialogOpen = true) => {
    const hadSelectedUser = selectedUser !== null;

    try {
      setIsLoading(true);
      if (keepDialogOpen) {
        setIsOpen(true);
      }
      const detail = await fetchAdminUserDetail(userId);
      setSelectedUser(detail);
      onUserSummaryUpdated?.(detail.summary);
      return detail;
    } catch (error) {
      console.warn('Failed to load admin user detail', error);
      toast('Failed to load user details', { position: 'bottom-center' });
      if (!hadSelectedUser) {
        setIsOpen(false);
      }
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  const openUser = async (userId: string) => loadUserDetail(userId);

  const updateRole = async (userId: string, role: AppRole) => {
    const targetUser =
      findUserSummary?.(userId) ??
      (selectedUser?.summary.userId === userId ? selectedUser.summary : null);
    const guardReason = getRoleChangeGuardReason(targetUser, role, currentAdminUserId, adminCount);
    if (guardReason) {
      toast(guardReason, { position: 'bottom-center' });
      return;
    }

    try {
      setUserActionKey(`role:${userId}:${role}`);
      const updated = await updateAdminUserRole(userId, role);
      onUserSummaryUpdated?.(updated);
      if (selectedUser?.summary.userId === userId) {
        await loadUserDetail(userId, false);
      }
      toast(`User role updated to ${role}`, { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to update user role', error);
      toast(error instanceof Error ? error.message : 'Failed to update user role', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const toggleStatus = async (user: AdminUserSummary) => {
    const nextStatus = user.status === 'active' ? 'suspended' : 'active';
    try {
      setUserActionKey(`status:${user.userId}:${nextStatus}`);
      const updated = await updateAdminUserStatus(user.userId, nextStatus);
      onUserSummaryUpdated?.(updated);
      if (selectedUser?.summary.userId === user.userId) {
        await loadUserDetail(user.userId, false);
      }
      toast(nextStatus === 'suspended' ? 'User suspended' : 'User reactivated', {
        position: 'bottom-center',
      });
    } catch (error) {
      console.warn('Failed to update user status', error);
      toast(error instanceof Error ? error.message : 'Failed to update user status', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const resetLessonProgress = async (lessonId: string | null) => {
    if (!selectedUser || !lessonId) {
      return;
    }
    try {
      setUserActionKey(`progress:${lessonId}`);
      await resetAdminUserLessonProgress(selectedUser.summary.userId, lessonId);
      const refreshed = await loadUserDetail(selectedUser.summary.userId, false);
      if (refreshed) {
        onUserSummaryUpdated?.(refreshed.summary);
      }
      toast('Lesson progress reset', { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to reset lesson progress', error);
      toast(error instanceof Error ? error.message : 'Failed to reset lesson progress', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const deleteComment = async (commentId: string, contentId: string | null) => {
    if (!selectedUser || !contentId) {
      return;
    }
    try {
      setUserActionKey(`comment:${commentId}`);
      await deleteContentComment(contentId, commentId);
      setSelectedUser((current) =>
        current
          ? {
              ...current,
              comments: current.comments.filter((comment) => comment.id !== commentId),
              activity: {
                ...current.activity,
                commentCount: Math.max(0, current.activity.commentCount - 1),
              },
            }
          : current
      );
      toast('Comment removed', { position: 'bottom-center' });
    } catch (error) {
      console.warn('Failed to delete user comment', error);
      toast(error instanceof Error ? error.message : 'Failed to delete comment', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  const startTakeDownContent = (content: Content) => {
    setUserContentRejectTarget(content);
    setUserContentRejectReasonState('');
    setUserContentRejectAttempted(false);
  };

  const cancelTakeDownContent = () => resetRejectState();

  const setTakeDownReason = (value: string) => {
    setUserContentRejectReasonState(
      sanitizeAdminUserInputValue(value, ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH)
    );
  };

  const confirmTakeDownContent = async () => {
    if (!selectedUser || !userContentRejectTarget) {
      return;
    }
    const feedback = normalizeAdminUserText(
      userContentRejectReason,
      ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH
    );
    if (!feedback) {
      setUserContentRejectAttempted(true);
      return;
    }

    try {
      setUserActionKey(`content:${userContentRejectTarget.id}`);
      await rejectContent(userContentRejectTarget.id, feedback);
      toast('Content taken down', { position: 'bottom-center' });
      resetRejectState();
      await loadUserDetail(selectedUser.summary.userId, false);
    } catch (error) {
      console.warn('Failed to take down user content', error);
      toast(error instanceof Error ? error.message : 'Failed to take down content', {
        position: 'bottom-center',
      });
    } finally {
      setUserActionKey(null);
    }
  };

  return {
    selectedUser,
    isOpen,
    isLoading,
    userActionKey,
    userContentRejectTarget,
    userContentRejectReason,
    userContentRejectAttempted,
    openUser,
    closeUser,
    updateRole,
    toggleStatus,
    resetLessonProgress,
    deleteComment,
    startTakeDownContent,
    cancelTakeDownContent,
    setTakeDownReason,
    confirmTakeDownContent,
  };
};
