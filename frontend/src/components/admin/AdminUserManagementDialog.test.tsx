import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { AdminUserManagementDialog } from '@/components/admin/AdminUserManagementDialog';
import { useAdminUserManagement } from '@/hooks/useAdminUserManagement';
import type { AdminUserDetail, AdminUserSummary } from '@/types';

const toastMock = vi.fn();
const fetchAdminUserDetailMock = vi.fn();
const updateAdminUserRoleMock = vi.fn();
const updateAdminUserStatusMock = vi.fn();
const resetAdminUserLessonProgressMock = vi.fn();
const deleteContentCommentMock = vi.fn();
const rejectContentMock = vi.fn();

vi.mock('@/components/ui/sonner', () => ({
  toast: (...args: unknown[]) => toastMock(...args),
}));

vi.mock('@/lib/api', () => ({
  fetchAdminUserDetail: (...args: unknown[]) => fetchAdminUserDetailMock(...args),
  updateAdminUserRole: (...args: unknown[]) => updateAdminUserRoleMock(...args),
  updateAdminUserStatus: (...args: unknown[]) => updateAdminUserStatusMock(...args),
  resetAdminUserLessonProgress: (...args: unknown[]) =>
    resetAdminUserLessonProgressMock(...args),
  deleteContentComment: (...args: unknown[]) => deleteContentCommentMock(...args),
  rejectContent: (...args: unknown[]) => rejectContentMock(...args),
}));

const cloneDetail = (detail: AdminUserDetail): AdminUserDetail =>
  JSON.parse(JSON.stringify(detail)) as AdminUserDetail;

const createUserDetail = (
  overrides: Partial<AdminUserDetail> = {},
  summaryOverrides: Partial<AdminUserSummary> = {}
): AdminUserDetail => ({
  summary: {
    userId: 'user-1',
    displayName: 'Flagged User',
    email: 'user@example.com',
    avatarUrl: null,
    reputationPoints: 120,
    currentStreak: 4,
    longestStreak: 8,
    lastActivityDate: '2026-04-03T08:00:00.000Z',
    totalHoursLearned: 15,
    roles: ['user'],
    status: 'active',
    createdAt: '2026-01-01T08:00:00.000Z',
    lastSignInAt: '2026-04-04T08:00:00.000Z',
    ...summaryOverrides,
  },
  suspendedUntil: null,
  activity: {
    postedContentCount: 1,
    likedContentCount: 2,
    savedContentCount: 3,
    commentCount: 1,
    enrolledLessonCount: 2,
    completedLessonCount: 1,
    badgeCount: 1,
    browsingCount: 3,
    searchCount: 2,
    chatMessageCount: 1,
  },
  postedContent: [
    {
      id: 'content-1',
      title: 'Unsafe Roti Facts',
      description: 'Needs moderation review',
      content_type: 'article',
      status: 'approved',
    } as never,
  ],
  likedContent: [],
  savedContent: [],
  comments: [
    {
      id: 'comment-1',
      contentId: 'content-1',
      contentTitle: 'Unsafe Roti Facts',
      body: 'Interesting',
      author: 'Flagged User',
      createdAt: '2026-04-04T08:00:00.000Z',
      updatedAt: '2026-04-04T08:00:00.000Z',
    },
  ],
  lessonProgress: [
    {
      id: 'progress-1',
      lessonId: 'lesson-1',
      lessonTitle: 'Intro Lesson',
      status: 'in_progress',
      progressPercentage: 50,
      currentSection: '1',
      startedAt: '2026-04-01T08:00:00.000Z',
      completedAt: null,
      lastAccessedAt: '2026-04-04T08:00:00.000Z',
    },
  ],
  badges: [],
  browsingHistory: [],
  searchHistory: [],
  chatHistory: [],
  ...overrides,
});

type HarnessProps = {
  currentAdminUserId?: string | null;
  initialAdminUsers?: AdminUserSummary[];
};

const Harness = ({
  currentAdminUserId = 'admin-2',
  initialAdminUsers = [createUserDetail().summary, createUserDetail({}, {
    userId: 'admin-2',
    displayName: 'Other Admin',
    email: 'admin@example.com',
    roles: ['admin'],
    status: 'active',
  }).summary],
}: HarnessProps) => {
  const [adminUsers, setAdminUsers] = useState(initialAdminUsers);
  const adminCount = adminUsers.filter((user) => user.roles.includes('admin')).length;

  const upsertUserSummary = (summary: AdminUserSummary) => {
    setAdminUsers((users) => {
      const existingIndex = users.findIndex((user) => user.userId === summary.userId);
      if (existingIndex === -1) {
        return [...users, summary];
      }
      return users.map((user) => (user.userId === summary.userId ? summary : user));
    });
  };

  const userManagement = useAdminUserManagement({
    currentAdminUserId,
    adminCount,
    findUserSummary: (userId) => adminUsers.find((user) => user.userId === userId) ?? null,
    onUserSummaryUpdated: upsertUserSummary,
  });

  return (
    <div>
      <button type="button" onClick={() => void userManagement.openUser('user-1')}>
        Open analytics user
      </button>
      <button type="button" onClick={userManagement.closeUser}>
        Force close
      </button>
      <AdminUserManagementDialog
        isOpen={userManagement.isOpen}
        user={userManagement.selectedUser}
        isLoading={userManagement.isLoading}
        userActionKey={userManagement.userActionKey}
        currentAdminUserId={currentAdminUserId}
        adminCount={adminCount}
        userContentRejectTarget={userManagement.userContentRejectTarget}
        userContentRejectReason={userManagement.userContentRejectReason}
        userContentRejectAttempted={userManagement.userContentRejectAttempted}
        onClose={userManagement.closeUser}
        onToggleRole={userManagement.updateRole}
        onToggleStatus={userManagement.toggleStatus}
        onResetLessonProgress={userManagement.resetLessonProgress}
        onDeleteComment={userManagement.deleteComment}
        onStartTakeDownContent={userManagement.startTakeDownContent}
        onTakeDownReasonChange={userManagement.setTakeDownReason}
        onCancelTakeDownContent={userManagement.cancelTakeDownContent}
        onConfirmTakeDownContent={userManagement.confirmTakeDownContent}
      />
    </div>
  );
};

describe('AdminUserManagementDialog', () => {
  let currentDetail: AdminUserDetail;

  beforeEach(() => {
    toastMock.mockReset();
    fetchAdminUserDetailMock.mockReset();
    updateAdminUserRoleMock.mockReset();
    updateAdminUserStatusMock.mockReset();
    resetAdminUserLessonProgressMock.mockReset();
    deleteContentCommentMock.mockReset();
    rejectContentMock.mockReset();

    currentDetail = createUserDetail();
    fetchAdminUserDetailMock.mockImplementation(async () => cloneDetail(currentDetail));
    updateAdminUserRoleMock.mockImplementation(
      async (_userId: string, role: AdminUserSummary['roles'][number]) => {
      currentDetail = createUserDetail(
        { ...currentDetail, summary: { ...currentDetail.summary, roles: [role] } },
        { roles: [role] }
      );
      return cloneDetail(currentDetail).summary;
      }
    );
    updateAdminUserStatusMock.mockImplementation(
      async (_userId: string, status: AdminUserSummary['status']) => {
      currentDetail = createUserDetail(
        {
          ...currentDetail,
          summary: { ...currentDetail.summary, status },
          suspendedUntil: status === 'suspended' ? '2026-05-01T00:00:00.000Z' : null,
        },
        { status }
      );
      return cloneDetail(currentDetail).summary;
      }
    );
    resetAdminUserLessonProgressMock.mockResolvedValue(undefined);
    deleteContentCommentMock.mockResolvedValue(undefined);
    rejectContentMock.mockResolvedValue(undefined);
  });

  it('opens from an analytics click and shows the full user management panel', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));

    expect(await screen.findByText('User Management')).toBeInTheDocument();
    expect(await screen.findByText('Posted Content')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Comments' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Lesson Progress' })).toBeInTheDocument();
  });

  it('shows a loading state while user details are being fetched', async () => {
    let resolveFetch: ((value: AdminUserDetail) => void) | null = null;
    fetchAdminUserDetailMock.mockImplementationOnce(
      () =>
        new Promise<AdminUserDetail>((resolve) => {
          resolveFetch = resolve;
        })
    );

    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));

    expect(await screen.findByText('Loading user details...')).toBeInTheDocument();

    resolveFetch?.(cloneDetail(currentDetail));

    await waitFor(() => {
      expect(screen.getByText('Posted Content')).toBeInTheDocument();
    });
  });

  it('updates role and status through the shared controller and refreshes the dialog', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));
    await screen.findByText('Posted Content');

    fireEvent.click(screen.getByRole('button', { name: 'Make Admin' }));

    await waitFor(() => {
      expect(updateAdminUserRoleMock).toHaveBeenCalledWith('user-1', 'admin');
      expect(fetchAdminUserDetailMock).toHaveBeenCalledTimes(2);
      expect(screen.getByRole('button', { name: 'Make User' })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Suspend Account' }));

    await waitFor(() => {
      expect(updateAdminUserStatusMock).toHaveBeenCalledWith('user-1', 'suspended');
      expect(fetchAdminUserDetailMock).toHaveBeenCalledTimes(3);
      expect(screen.getByRole('button', { name: 'Reactivate Account' })).toBeInTheDocument();
    });
  });

  it('disables self-demotion and last-admin demotion', async () => {
    const selfAdminUser = createUserDetail({}, { roles: ['admin'] }).summary;
    const soloAdminUser = createUserDetail({}, { roles: ['admin'] }).summary;

    const selfView = render(
      <Harness
        currentAdminUserId="user-1"
        initialAdminUsers={[selfAdminUser, createUserDetail({}, {
          userId: 'admin-2',
          displayName: 'Other Admin',
          email: 'admin@example.com',
          roles: ['admin'],
          status: 'active',
        }).summary]}
      />
    );

    currentDetail = createUserDetail({}, { roles: ['admin'] });

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));
    expect(await screen.findByRole('button', { name: 'Make User' })).toBeDisabled();

    selfView.unmount();
    render(
      <Harness currentAdminUserId="admin-2" initialAdminUsers={[soloAdminUser]} />
    );

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));
    expect(await screen.findByRole('button', { name: 'Make User' })).toBeDisabled();
  });

  it('resets selected user and takedown state when the dialog closes', async () => {
    render(<Harness />);

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));
    await screen.findByText('Posted Content');

    fireEvent.click(screen.getByRole('button', { name: 'Take down' }));
    expect(await screen.findByText('Take Down User Content')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^Take down$/ }));
    expect(await screen.findByText('A takedown reason is required.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Force close', hidden: true }));

    await waitFor(() => {
      expect(screen.queryByText('Take Down User Content')).not.toBeInTheDocument();
      expect(screen.queryByText('A takedown reason is required.')).not.toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'Open analytics user' }));
    await screen.findByText('Posted Content');
    fireEvent.click(screen.getByRole('button', { name: 'Take down' }));

    expect(await screen.findByText('Take Down User Content')).toBeInTheDocument();
    expect(screen.queryByText('A takedown reason is required.')).not.toBeInTheDocument();
  });
});
