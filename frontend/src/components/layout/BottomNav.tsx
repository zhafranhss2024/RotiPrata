import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { 
  Home, 
  BookOpen, 
  User,
  Search,
  Plus,
} from 'lucide-react';
import { cn } from '@/lib/utils';

const navItems = [
  { label: 'Feed', href: '/', icon: Home },
  { label: 'Explore', href: '/explore', icon: Search },
  { label: 'Lesson Hub', href: '/lessons', icon: BookOpen },
  { label: 'Profile', href: '/profile', icon: User },
];

export function BottomNav() {
  const location = useLocation();

  const createHref = '/create';
  const CreateIcon = Plus;

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-50 glass border-t border-border md:hidden">
      <div className="relative flex items-center h-nav-height px-4 pl-safe-left pr-safe-right pb-safe-bottom">
        <div className="flex w-full items-center justify-between">
          <div className="flex items-center gap-6">
            {navItems.slice(0, 2).map((item) => {
            const isActive = location.pathname === item.href || 
              (item.href !== '/' && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                to={item.href}
                aria-label={item.label}
                className={cn(
                  "flex items-center justify-center touch-target transition-colors",
                  isActive 
                    ? "text-primary" 
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                <Icon className={cn("h-6 w-6", isActive && "animate-bounce-gentle")} />
                <span className="sr-only">{item.label}</span>
              </Link>
            );
            })}
          </div>
          <div className="flex items-center gap-6">
            {navItems.slice(2).map((item) => {
            const isActive = location.pathname === item.href || 
              (item.href !== '/' && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                to={item.href}
                aria-label={item.label}
                className={cn(
                  "flex items-center justify-center touch-target transition-colors",
                  isActive 
                    ? "text-primary" 
                    : "text-muted-foreground hover:text-foreground"
                )}
              >
                <Icon className={cn("h-6 w-6", isActive && "animate-bounce-gentle")} />
                <span className="sr-only">{item.label}</span>
              </Link>
            );
            })}
          </div>
        </div>

        <Link
          to={createHref}
          aria-label="Create"
          className="absolute left-1/2 -translate-x-1/2 -top-4"
        >
          <div className="h-12 w-12 rounded-full gradient-primary shadow-glow flex items-center justify-center">
            <CreateIcon className="h-6 w-6 text-white" />
          </div>
        </Link>
      </div>
    </nav>
  );
}
