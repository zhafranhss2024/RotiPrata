import type { AdminUserSummary, AppRole } from '@/types';

export const ADMIN_USER_TAKEDOWN_REASON_MAX_LENGTH = 500;

const stripControlCharacters = (value: string) =>
  Array.from(value)
    .filter((character) => {
      const code = character.charCodeAt(0);
      return code > 31 && code !== 127;
    })
    .join('');

export const sanitizeAdminUserInputValue = (value: string, maxLength: number) => {
  const cleaned = stripControlCharacters(value);
  if (maxLength > 0 && cleaned.length > maxLength) {
    return cleaned.slice(0, maxLength);
  }
  return cleaned;
};

export const normalizeAdminUserText = (value: string, maxLength: number) => {
  const cleaned = sanitizeAdminUserInputValue(value, maxLength);
  return cleaned.replace(/\s+/g, ' ').trim();
};

export const getRoleChangeGuardReason = (
  user: AdminUserSummary | null,
  role: AppRole,
  currentAdminUserId: string | null,
  adminCount: number
) => {
  if (!user || role === 'admin' || !user.roles.includes('admin')) {
    return null;
  }
  if (user.userId === currentAdminUserId) {
    return 'You cannot remove your own admin role';
  }
  if (adminCount <= 1) {
    return 'At least one admin must exist';
  }
  return null;
};
