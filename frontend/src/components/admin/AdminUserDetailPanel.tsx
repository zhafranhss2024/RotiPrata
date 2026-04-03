import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import type { AdminUserDetail, AdminUserSummary, AppRole, Content } from '@/types';

type Props = {
  user: AdminUserDetail;
  userActionKey: string | null;
  currentAdminUserId: string | null;
  adminCount: number;
  onToggleRole: (userId: string, nextRole: AppRole) => void | Promise<void>;
  onToggleStatus: (user: AdminUserSummary) => void | Promise<void>;
  onResetLessonProgress: (lessonId: string | null) => void | Promise<void>;
  onDeleteComment: (commentId: string, contentId: string | null) => void | Promise<void>;
  onTakeDownContent: (content: Content) => void;
  className?: string;
};

const formatDateTime = (value: string | null | undefined, fallback: string) => {
  if (!value) {
    return fallback;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? fallback : parsed.toLocaleString();
};

const formatDate = (value: string | null | undefined, fallback: string) => {
  if (!value) {
    return fallback;
  }
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? fallback : parsed.toLocaleDateString();
};

export const AdminUserDetailPanel = ({
  user,
  userActionKey,
  currentAdminUserId,
  adminCount,
  onToggleRole,
  onToggleStatus,
  onResetLessonProgress,
  onDeleteComment,
  onTakeDownContent,
  className,
}: Props) => {
  const nextRole: AppRole = user.summary.roles.includes('admin') ? 'user' : 'admin';
  const nextStatus = user.summary.status === 'active' ? 'suspended' : 'active';
  const isDemotingAdmin = user.summary.roles.includes('admin') && nextRole !== 'admin';
  const roleGuardReason = !isDemotingAdmin
    ? null
    : user.summary.userId === currentAdminUserId
      ? 'You cannot remove your own admin role'
      : adminCount <= 1
        ? 'At least one admin must exist'
        : null;

  return (
    <div className={cn('grid gap-6 px-6 pb-6', className)}>
      <div className="grid gap-4 rounded-lg border border-border/70 p-4 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <p className="text-xl font-semibold">{user.summary.displayName}</p>
            <Badge variant={user.summary.status === 'active' ? 'secondary' : 'destructive'}>
              {user.summary.status}
            </Badge>
            {user.summary.roles.map((role) => (
              <Badge key={`${user.summary.userId}-${role}`} variant="outline">
                {role}
              </Badge>
            ))}
          </div>
          <p className="text-sm text-muted-foreground">
            {user.summary.email ?? user.summary.userId}
          </p>
          <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
            <p>Created: {formatDateTime(user.summary.createdAt, 'Unknown')}</p>
            <p>Last sign in: {formatDateTime(user.summary.lastSignInAt, 'Never')}</p>
            <p>Last active: {formatDate(user.summary.lastActivityDate, 'Unknown')}</p>
            <p>Suspended until: {formatDateTime(user.suspendedUntil, 'Not suspended')}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2 lg:justify-end">
          <Button
            type="button"
            variant="outline"
            title={roleGuardReason ?? undefined}
            disabled={Boolean(roleGuardReason) || userActionKey === `role:${user.summary.userId}:${nextRole}`}
            onClick={() => {
              void onToggleRole(user.summary.userId, nextRole);
            }}
          >
            {user.summary.roles.includes('admin') ? 'Make User' : 'Make Admin'}
          </Button>
          <Button
            type="button"
            variant={user.summary.status === 'active' ? 'destructive' : 'secondary'}
            disabled={userActionKey === `status:${user.summary.userId}:${nextStatus}`}
            onClick={() => {
              void onToggleStatus(user.summary);
            }}
          >
            {user.summary.status === 'active' ? 'Suspend Account' : 'Reactivate Account'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Posted</p>
            <p className="text-xl font-semibold">{user.activity.postedContentCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Comments</p>
            <p className="text-xl font-semibold">{user.activity.commentCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Lessons</p>
            <p className="text-xl font-semibold">{user.activity.enrolledLessonCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Completed</p>
            <p className="text-xl font-semibold">{user.activity.completedLessonCount}</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4">
            <p className="text-xs text-muted-foreground">Badges</p>
            <p className="text-xl font-semibold">{user.activity.badgeCount}</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Posted Content</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {user.postedContent.length === 0 ? (
              <p className="text-sm text-muted-foreground">No posted content.</p>
            ) : (
              user.postedContent.map((content) => (
                <div key={content.id} className="rounded-lg border border-border/70 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-medium">{content.title}</p>
                      <p className="text-xs text-muted-foreground">
                        {content.content_type} | {content.status}
                      </p>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      variant="destructive"
                      disabled={content.status === 'rejected'}
                      onClick={() => onTakeDownContent(content)}
                    >
                      {content.status === 'rejected' ? 'Taken down' : 'Take down'}
                    </Button>
                  </div>
                  {content.description ? (
                    <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
                      {content.description}
                    </p>
                  ) : null}
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Comments</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {user.comments.length === 0 ? (
              <p className="text-sm text-muted-foreground">No comments recorded.</p>
            ) : (
              user.comments.map((comment) => (
                <div key={comment.id} className="rounded-lg border border-border/70 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-medium">{comment.contentTitle ?? 'Unknown content'}</p>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={userActionKey === `comment:${comment.id}` || !comment.contentId}
                      onClick={() => {
                        void onDeleteComment(comment.id, comment.contentId);
                      }}
                    >
                      Delete
                    </Button>
                  </div>
                  <p className="mt-2 text-sm text-muted-foreground">{comment.body ?? 'No comment body.'}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatDateTime(comment.createdAt, 'Unknown time')}
                  </p>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Lesson Progress</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {user.lessonProgress.length === 0 ? (
              <p className="text-sm text-muted-foreground">No lesson progress recorded.</p>
            ) : (
              user.lessonProgress.map((progress) => (
                <div key={progress.id} className="rounded-lg border border-border/70 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <p className="font-medium">{progress.lessonTitle ?? progress.lessonId ?? 'Unknown lesson'}</p>
                      <p className="text-xs text-muted-foreground">
                        {progress.status ?? 'unknown'} | {progress.progressPercentage}%
                      </p>
                    </div>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={userActionKey === `progress:${progress.lessonId}` || !progress.lessonId}
                      onClick={() => {
                        void onResetLessonProgress(progress.lessonId);
                      }}
                    >
                      Reset
                    </Button>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Last accessed {formatDateTime(progress.lastAccessedAt, 'Unknown')}
                  </p>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Badges and Saved Activity</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <p className="text-sm font-medium">Badges</p>
              {user.badges.length === 0 ? (
                <p className="text-sm text-muted-foreground">No badges yet.</p>
              ) : (
                user.badges.map((badge) => (
                  <div
                    key={`${badge.lessonId ?? badge.badgeName}-${badge.earnedAt ?? 'locked'}`}
                    className="rounded-md border border-border/70 p-3"
                  >
                    <p className="font-medium">{badge.badgeName}</p>
                    <p className="text-xs text-muted-foreground">
                      {badge.lessonTitle ?? 'Lesson badge'} | {badge.earned ? 'Earned' : 'Locked'}
                    </p>
                  </div>
                ))
              )}
            </div>
            <div className="space-y-2">
              <p className="text-sm font-medium">Liked and Saved</p>
              <p className="text-sm text-muted-foreground">
                {user.activity.likedContentCount} liked | {user.activity.savedContentCount} saved
              </p>
              {user.likedContent.slice(0, 3).map((content) => (
                <div key={`liked-${content.id}`} className="rounded-md border border-border/70 p-3">
                  <p className="font-medium">{content.title}</p>
                  <p className="text-xs text-muted-foreground">Liked content</p>
                </div>
              ))}
              {user.savedContent.slice(0, 3).map((content) => (
                <div key={`saved-${content.id}`} className="rounded-md border border-border/70 p-3">
                  <p className="font-medium">{content.title}</p>
                  <p className="text-xs text-muted-foreground">Saved content</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Browsing History</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {user.browsingHistory.length === 0 ? (
              <p className="text-sm text-muted-foreground">No browsing history.</p>
            ) : (
              user.browsingHistory.map((entry) => (
                <div key={entry.id} className="rounded-md border border-border/70 p-3">
                  <p className="font-medium">{entry.title ?? entry.itemId ?? 'Viewed item'}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatDateTime(entry.viewedAt, 'Unknown time')}
                  </p>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Search History</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {user.searchHistory.length === 0 ? (
              <p className="text-sm text-muted-foreground">No search history.</p>
            ) : (
              user.searchHistory.map((entry) => (
                <div key={entry.id} className="rounded-md border border-border/70 p-3">
                  <p className="font-medium">{entry.query ?? 'Untitled query'}</p>
                  <p className="text-xs text-muted-foreground">
                    {formatDateTime(entry.searchedAt, 'Unknown time')}
                  </p>
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Chat History</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {user.chatHistory.length === 0 ? (
              <p className="text-sm text-muted-foreground">No chat history.</p>
            ) : (
              user.chatHistory.map((message, index) => (
                <div key={`${message.timestamp}-${index}`} className="rounded-md border border-border/70 p-3">
                  <p className="text-xs uppercase text-muted-foreground">{message.role}</p>
                  <p className="mt-1 text-sm">{message.message}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatDateTime(message.timestamp, 'Unknown time')}
                  </p>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
