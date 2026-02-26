import React from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Compass,
  Home,
  LogIn,
  LogOut,
  PlusCircle,
  Search,
  Shield,
  User,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useAuthContext } from "@/contexts/AuthContext";
import { HeartsIndicator } from "./HeartsIndicator";
import { useIsDesktop } from "@/hooks/use-desktop";

const navItems = [
  { label: "Learn", href: "/lessons", icon: Home },
  { label: "Feed", href: "/", icon: Compass },
  { label: "Explore", href: "/explore", icon: Search },
  { label: "Profile", href: "/profile", icon: User },
];

export function DesktopNav() {
  const location = useLocation();
  const { isAuthenticated, isAdmin, logout } = useAuthContext();
  const isDesktop = useIsDesktop();

  return (
    <header className="hidden lg:flex fixed top-0 left-0 right-0 z-50 border-b border-mainAlt bg-mainDark/95 backdrop-blur">
      <div className="relative w-full px-6 h-16 flex items-center justify-between gap-6">
        <Link to="/" className="flex items-center gap-3 min-w-fit">
          <img
            src="/icon-images/LEADERBOARD_ICON.png"
            alt=""
            className="h-9 w-9 rounded-md object-cover"
          />
          <span className="text-mainAccent dark:text-white text-xl">Rotiprata</span>
        </Link>

        <nav className="absolute left-1/2 -translate-x-1/2 flex items-center gap-2">
          {navItems.map((item) => {
            const isActive =
              location.pathname === item.href ||
              (item.href !== "/" && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                to={item.href}
                className={cn(
                  "flex items-center gap-2 rounded-xl border px-3 py-2 transition-colors",
                  isActive
                    ? "border-mainAlt bg-main text-mainAccent dark:text-white"
                    : "border-transparent text-mainAccent/80 hover:text-mainAccent hover:border-mainAlt hover:bg-main/60 dark:text-white dark:hover:text-white"
                )}
              >
                <Icon className="h-4 w-4" />
                <span className="text-sm">{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-2 min-w-fit">
          {isAuthenticated && isDesktop && <HeartsIndicator />}

          <Button asChild className="duo-button-primary h-10">
            <Link to="/create">
              <PlusCircle className="h-4 w-4 mr-2" />
              Create
            </Link>
          </Button>

          {isAdmin() && (
            <Button
              asChild
              variant="outline"
              className="h-10 border-mainAlt bg-main text-mainAccent hover:bg-mainAlt dark:text-white"
            >
              <Link to="/admin">
                <Shield className="h-4 w-4 mr-2" />
                Admin
              </Link>
            </Button>
          )}

          {isAuthenticated ? (
            <Button
              type="button"
              variant="ghost"
              className="h-10 text-mainAccent hover:bg-mainAlt hover:text-mainAccent dark:text-white dark:hover:text-white"
              onClick={() => {
                void logout();
              }}
            >
              <LogOut className="h-4 w-4 mr-2" />
              Log Out
            </Button>
          ) : (
            <Button asChild className="duo-button-primary h-10">
              <Link to="/login">
                <LogIn className="h-4 w-4 mr-2" />
                Sign In
              </Link>
            </Button>
          )}
        </div>
      </div>
    </header>
  );
}
