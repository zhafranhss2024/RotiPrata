import React from 'react';
import { Link } from 'react-router-dom';
import { Shield } from 'lucide-react';
import { useAuthContext } from '@/contexts/AuthContext';
import { HeartsIndicator } from './HeartsIndicator';
import { useIsDesktop } from '@/hooks/use-desktop';

export function MobileTopBar() {
  const { isAuthenticated, isAdmin, user } = useAuthContext();
  const isDesktop = useIsDesktop();

  return (
    <header className="lg:hidden sticky top-0 z-40 border-b border-mainAlt bg-main dark:bg-mainDark">
      <div className="h-[var(--mobile-top-bar-height)] px-4 flex items-center justify-between gap-3">
        <Link to="/" className="inline-flex items-center gap-2">
          <img src="/icon-images/LEADERBOARD_ICON.png" alt="Rotiprata" className="h-7 w-7 rounded-md object-cover" />
          <span className="text-mainAccent dark:text-white font-bold text-lg">Rotiprata</span>
        </Link>

        <div className="inline-flex items-center gap-2">
          {isAuthenticated ? (
            <>
              <div className="inline-flex items-center gap-1 rounded-full bg-main border border-mainAlt px-2 py-1 text-xs text-mainAccent">
                <img src="/icon-images/STREAK_FLAME_ICON.png" alt="" className="h-4 w-4 object-contain" />
                <span>{user?.current_streak ?? 0}</span>
              </div>
              {!isDesktop && <HeartsIndicator />}
              {!isDesktop && isAdmin() ? (
                <Link
                  to="/admin"
                  className="inline-flex items-center gap-1 rounded-full bg-main border border-mainAlt px-2 py-1 text-xs text-mainAccent"
                >
                  <Shield className="h-4 w-4" />
                  <span>Admin</span>
                </Link>
              ) : null}
            </>
          ) : (
            <Link to="/login" className="text-sm text-mainAccent">
              Sign in
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
