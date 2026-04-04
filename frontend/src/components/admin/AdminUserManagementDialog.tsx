import { Loader2 } from 'lucide-react';
import { AdminUserDetailPanel } from '@/components/admin/AdminUserDetailPanel';
import {
  ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH,
  normalizeAdminUserText,
} from '@/components/admin/adminUserManagementUtils';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { AdminUserDetail, AdminUserSummary, AppRole, Content } from '@/types';

type Props = {
  isOpen: boolean;
  user: AdminUserDetail | null;
  isLoading: boolean;
  userActionKey: string | null;
  currentAdminUserId: string | null;
  adminCount: number;
  userContentRejectTarget: Content | null;
  userContentRejectReason: string;
  userContentRejectAttempted: boolean;
  onClose: () => void;
  onToggleRole: (userId: string, role: AppRole) => void | Promise<void>;
  onToggleStatus: (user: AdminUserSummary) => void | Promise<void>;
  onResetLessonProgress: (lessonId: string | null) => void | Promise<void>;
  onDeleteComment: (commentId: string, contentId: string | null) => void | Promise<void>;
  onStartTakeDownContent: (content: Content) => void;
  onTakeDownReasonChange: (value: string) => void;
  onCancelTakeDownContent: () => void;
  onConfirmTakeDownContent: () => void | Promise<void>;
};

export const AdminUserManagementDialog = ({
  isOpen,
  user,
  isLoading,
  userActionKey,
  currentAdminUserId,
  adminCount,
  userContentRejectTarget,
  userContentRejectReason,
  userContentRejectAttempted,
  onClose,
  onToggleRole,
  onToggleStatus,
  onResetLessonProgress,
  onDeleteComment,
  onStartTakeDownContent,
  onTakeDownReasonChange,
  onCancelTakeDownContent,
  onConfirmTakeDownContent,
}: Props) => (
  <>
    <Dialog
      open={isOpen}
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent className="max-w-6xl w-[95vw] p-0 overflow-hidden">
        <div className="max-h-[85vh] overflow-y-auto">
          <DialogHeader className="p-6">
            <DialogTitle>User Management</DialogTitle>
            <DialogDescription>
              Review account status, moderation footprint, and learning activity for a user.
            </DialogDescription>
          </DialogHeader>

          {isLoading ? (
            <div className="flex items-center justify-center px-6 pb-8 text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Loading user details...
            </div>
          ) : user ? (
            <AdminUserDetailPanel
              user={user}
              userActionKey={userActionKey}
              currentAdminUserId={currentAdminUserId}
              adminCount={adminCount}
              onToggleRole={onToggleRole}
              onToggleStatus={onToggleStatus}
              onResetLessonProgress={onResetLessonProgress}
              onDeleteComment={onDeleteComment}
              onTakeDownContent={onStartTakeDownContent}
            />
          ) : (
            <div className="px-6 pb-8 text-sm text-muted-foreground">Select a user to review.</div>
          )}
        </div>
      </DialogContent>
    </Dialog>

    <Dialog
      open={userContentRejectTarget !== null}
      onOpenChange={(open) => {
        if (!open) {
          onCancelTakeDownContent();
        }
      }}
    >
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Take Down User Content</DialogTitle>
          <DialogDescription>
            Provide a clear moderation reason before removing this content from the user profile.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="user-content-reject-reason">Reason</Label>
          <Textarea
            id="user-content-reject-reason"
            value={userContentRejectReason}
            onChange={(event) => onTakeDownReasonChange(event.target.value)}
            maxLength={ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH}
            rows={4}
          />
          {userContentRejectAttempted &&
          !normalizeAdminUserText(
            userContentRejectReason,
            ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH
          ) ? (
            <p className="text-xs text-destructive">A takedown reason is required.</p>
          ) : null}
          <p className="text-xs text-muted-foreground">
            {userContentRejectReason.length}/{ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH}
          </p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancelTakeDownContent}>
            Cancel
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={
              !!userContentRejectTarget && userActionKey === `content:${userContentRejectTarget.id}`
            }
            onClick={() => {
              void onConfirmTakeDownContent();
            }}
          >
            Take down
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>
);
