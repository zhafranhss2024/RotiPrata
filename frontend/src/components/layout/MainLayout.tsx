import React, { ReactNode } from 'react';
import { BottomNav } from './BottomNav';
import { DesktopNav } from './DesktopNav';
import { cn } from '@/lib/utils';

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
  return (
    <div className="min-h-screen bg-background">
      {!hideNav && <DesktopNav />}
      
      <main 
        className={cn(
          !fullScreen && "md:pt-16",
          className
        )}
      >
        {children}
      </main>
      
      {!hideNav && <BottomNav />}
    </div>
  );
}
