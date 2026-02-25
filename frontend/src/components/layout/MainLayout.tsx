import React, { ReactNode } from "react";
import { BottomNav } from "./BottomNav";
import { DesktopNav } from "./DesktopNav";
import { MobileTopBar } from "./MobileTopBar";
import { cn } from "@/lib/utils";

interface MainLayoutProps {
  children: ReactNode;
  className?: string;
  hideNav?: boolean;
  fullScreen?: boolean;
}

export function MainLayout({
  children,
  className,
  hideNav = false,
  fullScreen = false,
}: MainLayoutProps) {
  if (hideNav) {
    return (
      <div className={cn("min-h-screen bg-mainDark text-white", className)}>
        {children}
      </div>
    );
  }

  return (
    <div className="min-h-screen h-dvh bg-mainDark text-white">
      <DesktopNav />
      <MobileTopBar />
      <main
        className={cn(
          "h-dvh overflow-y-auto duo-scrollbar pt-16 lg:pt-16",
          !fullScreen && "pb-safe lg:pb-0",
          className
        )}
      >
        {children}
      </main>
      <BottomNav />
    </div>
  );
}
