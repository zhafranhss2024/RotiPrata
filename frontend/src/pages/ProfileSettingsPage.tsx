import { Link } from "react-router-dom";
import { ArrowLeft, ChevronRight, Laptop, LogOut, Moon, Settings, Shield, Sun } from "lucide-react";

import { MainLayout } from "@/components/layout/MainLayout";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useAuthContext } from "@/contexts/AuthContext";
import { useThemeContext } from "@/contexts/ThemeContext";
import type { ThemePreference } from "@/types";

const THEME_OPTIONS: Array<{ value: ThemePreference; label: string }> = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
  { value: "system", label: "System" },
];

const ProfileSettingsPage = () => {
  const { logout, isAdmin } = useAuthContext();
  const { theme, resolvedTheme, setTheme } = useThemeContext();

  const handleLogout = async () => {
    await logout();
  };

  return (
    <MainLayout>
      <div className="container max-w-3xl mx-auto px-4 py-6 md:py-8 pb-safe space-y-6">
        <div className="flex items-center gap-3">
          <Button asChild variant="ghost" size="icon">
            <Link to="/profile" aria-label="Back to profile">
              <ArrowLeft className="h-5 w-5" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold">Settings</h1>
            <p className="text-sm text-muted-foreground">Manage your appearance, account controls, and admin tools.</p>
          </div>
        </div>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              {theme === "system" ? (
                <Laptop className="h-5 w-5" />
              ) : resolvedTheme === "dark" ? (
                <Moon className="h-5 w-5" />
              ) : (
                <Sun className="h-5 w-5" />
              )}
              Theme
            </CardTitle>
            <CardDescription>Choose how Rotiprata looks on this device.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between gap-3 rounded-2xl border border-mainAlt/80 bg-main p-4">
              <div className="flex items-center gap-3">
                <Settings className="h-5 w-5" />
                <span>Current theme</span>
              </div>
              <Badge variant="secondary">
                {theme === "system"
                  ? `System (${resolvedTheme === "dark" ? "Dark" : "Light"})`
                  : theme === "dark"
                    ? "Dark"
                    : "Light"}
              </Badge>
            </div>
            <div className="grid grid-cols-3 gap-2">
              {THEME_OPTIONS.map((option) => (
                <Button
                  key={option.value}
                  type="button"
                  size="sm"
                  variant={theme === option.value ? "default" : "outline"}
                  onClick={() => setTheme(option.value)}
                  className="w-full"
                >
                  {option.label}
                </Button>
              ))}
            </div>
          </CardContent>
        </Card>

        <Card className="border-mainAlt/80 bg-main shadow-sm">
          <CardContent className="p-0">
            <Link to="/profile/edit" className="flex items-center justify-between p-4 transition-colors hover:bg-muted/50">
              <div className="flex items-center gap-3">
                <Settings className="h-5 w-5" />
                <span>Edit profile</span>
              </div>
              <ChevronRight className="h-5 w-5 text-muted-foreground" />
            </Link>

            {isAdmin() ? (
              <>
                <Separator />
                <Link to="/admin" className="flex items-center justify-between p-4 transition-colors hover:bg-muted/50">
                  <div className="flex items-center gap-3">
                    <Shield className="h-5 w-5 text-destructive" />
                    <span>Admin Panel</span>
                  </div>
                  <ChevronRight className="h-5 w-5 text-muted-foreground" />
                </Link>
              </>
            ) : null}

            <Separator />

            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-3 p-4 text-destructive transition-colors hover:bg-muted/50"
            >
              <LogOut className="h-5 w-5" />
              <span>Log Out</span>
            </button>
          </CardContent>
        </Card>
      </div>
    </MainLayout>
  );
};

export default ProfileSettingsPage;
