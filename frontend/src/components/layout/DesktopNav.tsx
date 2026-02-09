import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { 
  Home, 
  Search, 
  BookOpen, 
  User,
  PlusCircle,
  Shield,
  Moon,
  Sun,
  LogIn,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { useAuthContext } from '@/contexts/AuthContext';
import { useThemeContext } from '@/contexts/ThemeContext';

const navItems = [
  { label: 'Feed', href: '/', icon: Home },
  { label: 'Explore', href: '/explore', icon: Search },
  { label: 'Lessons', href: '/lessons', icon: BookOpen },
];

export function DesktopNav() {
  const location = useLocation();
  const { isAuthenticated, isAdmin } = useAuthContext();
  const { toggleTheme, isDark } = useThemeContext();

  return (
    <header className="hidden md:flex fixed top-0 left-0 right-0 z-50 glass border-b border-border">
      <div className="container flex items-center justify-between h-16">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <div className="gradient-primary p-2 rounded-xl">
            <span className="text-xl font-bold text-white">🥞</span>
          </div>
          <span className="text-xl font-display font-bold text-gradient-primary">
            Rotiprata
          </span>
        </Link>

        {/* Navigation */}
        <nav className="flex items-center gap-1">
          {navItems.map((item) => {
            const isActive = location.pathname === item.href || 
              (item.href !== '/' && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  "flex items-center gap-2 px-4 py-2 rounded-lg transition-colors",
                  isActive 
                    ? "bg-primary/10 text-primary" 
                    : "text-muted-foreground hover:text-foreground hover:bg-muted"
                )}
              >
                <Icon className="h-5 w-5" />
                <span className="font-medium">{item.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* Right section */}
        <div className="flex items-center gap-2">
          {/* Theme toggle */}
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleTheme}
            className="rounded-full"
          >
            {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
          </Button>

          {isAuthenticated ? (
            <>
              {/* Create button */}
              <Button asChild className="gradient-primary border-0">
                <Link to="/create">
                  <PlusCircle className="h-5 w-5 mr-2" />
                  Create
                </Link>
              </Button>

              {/* Admin link */}
              {isAdmin() && (
                <Button variant="outline" asChild>
                  <Link to="/admin">
                    <Shield className="h-5 w-5 mr-2" />
                    Admin
                  </Link>
                </Button>
              )}

              {/* Profile */}
              <Button variant="ghost" size="icon" asChild className="rounded-full">
                <Link to="/profile">
                  <User className="h-5 w-5" />
                </Link>
              </Button>
            </>
          ) : (
            <Button asChild>
              <Link to="/login">
                <LogIn className="h-5 w-5 mr-2" />
                Sign In
              </Link>
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}