import React from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Loader2 } from "lucide-react";

export interface UserSummary {
  userId: string;
  displayName: string;
  email?: string;
  status: "active" | "suspended";
  roles: string[];
  createdAt?: string;
  lastSignInAt?: string;
  lastActivityDate?: string;
}

export interface UserDetailModalProps {
  isOpen: boolean;
  user: UserSummary | null;
  isLoading: boolean;
  onClose: () => void;
  onUpdateRole: (userId: string, newRole: string) => void;
  onToggleStatus: (user: UserSummary) => void;
  userActionKey?: string;
}

export const UserDetailModal: React.FC<UserDetailModalProps> = ({
  isOpen,
  user,
  isLoading,
  onClose,
  onUpdateRole,
  onToggleStatus,
  userActionKey,
}) => {
  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
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
            <div className="grid gap-6 px-6 pb-6">
              <div className="grid gap-4 rounded-lg border border-border/70 p-4 lg:grid-cols-[1.6fr_1fr]">
                {/* User Info */}
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-xl font-semibold">{user.displayName}</p>
                    <Badge variant={user.status === "active" ? "secondary" : "destructive"}>
                      {user.status}
                    </Badge>
                    {user.roles.map((role) => (
                      <Badge key={`${user.userId}-${role}`} variant="outline">
                        {role}
                      </Badge>
                    ))}
                  </div>
                  <p className="text-sm text-muted-foreground">{user.email ?? user.userId}</p>
                  <div className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
                    <p>Created: {user.createdAt ? new Date(user.createdAt).toLocaleString() : "Unknown"}</p>
                    <p>Last sign in: {user.lastSignInAt ? new Date(user.lastSignInAt).toLocaleString() : "Never"}</p>
                    <p>Last active: {user.lastActivityDate ? new Date(user.lastActivityDate).toLocaleDateString() : "Unknown"}</p>
                    <p>Suspended until: {user.status === "suspended" ? "Suspended" : "Not suspended"}</p>
                  </div>
                </div>

                {/* Action Buttons */}
                <div className="flex flex-wrap gap-2 lg:justify-end">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={
                      userActionKey ===
                      `role:${user.userId}:${user.roles.includes("admin") ? "user" : "admin"}`
                    }
                    onClick={() =>
                      onUpdateRole(user.userId, user.roles.includes("admin") ? "user" : "admin")
                    }
                  >
                    {user.roles.includes("admin") ? "Make User" : "Make Admin"}
                  </Button>

                  <Button
                    type="button"
                    variant={user.status === "active" ? "destructive" : "secondary"}
                    disabled={
                      userActionKey ===
                      `status:${user.userId}:${user.status === "active" ? "suspended" : "active"}`
                    }
                    onClick={() => onToggleStatus(user)}
                  >
                    {user.status === "active" ? "Suspend Account" : "Reactivate Account"}
                  </Button>

                  <Button type="button" variant="secondary" onClick={onClose}>
                    Close
                  </Button>
                </div>
              </div>
            </div>
          ) : (
            <div className="px-6 pb-6 text-sm text-muted-foreground">No user selected.</div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
};