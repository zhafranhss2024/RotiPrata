import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import {
  Compass,
  Home,
  Plus,
  Search,
  Shield,
  User,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuthContext } from '@/contexts/AuthContext';

const baseNavItems = [
  { label: 'Learn', href: '/lessons', icon: Home },
  { label: 'Feed', href: '/', icon: Compass },
  { label: 'Explore', href: '/explore', icon: Search },
  { label: 'Profile', href: '/profile', icon: User },
];

export function BottomNav() {
  const location = useLocation();
  const { isAdmin } = useAuthContext();

  const navItems = isAdmin()
    ? [...baseNavItems.slice(0, 3), { label: 'Admin', href: '/admin', icon: Shield }]
    : baseNavItems;

  return (
    <nav className="lg:hidden fixed bottom-0 left-0 right-0 z-50 border-t border-mainAlt bg-mainDark/95 backdrop-blur">
      <div className="relative h-20 px-6 pb-safe">
        <div className="h-full grid grid-cols-4 items-center gap-2">
          {navItems.map((item) => {
            const isActive =
              location.pathname === item.href ||
              (item.href !== '/' && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  'inline-flex flex-col items-center justify-center gap-1 rounded-xl py-2 text-xs transition-colors',
                  isActive
                    ? 'text-mainAccent bg-main border border-mainAlt'
                    : 'text-mainAccent/75 hover:text-mainAccent dark:text-white/80 dark:hover:text-white'
                )}
              >
                <Icon className="h-5 w-5" />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </div>

        <Link
          to="/create"
          aria-label="Create"
          className="absolute left-1/2 -translate-x-1/2 -top-5 h-12 w-12 rounded-full duo-button-primary flex items-center justify-center"
        >
          <Plus className="h-6 w-6" />
        </Link>
      </div>
    </nav>
  );
}

