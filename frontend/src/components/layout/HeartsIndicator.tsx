import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { fetchUserHearts } from '@/lib/api';
import { subscribeToHeartsUpdated } from '@/lib/heartsEvents';
import type { LessonHeartsStatus } from '@/types';

interface HeartsIndicatorProps {
  className?: string;
}

export function HeartsIndicator({ className }: HeartsIndicatorProps) {
  const location = useLocation();
  const isQuizRoute = /^\/lessons\/[^/]+\/quiz\/?$/.test(location.pathname);
  const [hearts, setHearts] = useState<LessonHeartsStatus | null>(null);
  const [hasError, setHasError] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;
    const load = () => {
      fetchUserHearts()
        .then((data) => {
          if (active) {
            setHearts(data);
            setHasError(false);
            setIsLoading(false);
          }
        })
        .catch(() => {
          if (active) {
            setHasError(true);
            setIsLoading(false);
          }
        });
    };

    load();
    const handleFocus = () => load();
    if (!isQuizRoute) {
      window.addEventListener('focus', handleFocus);
    }

    return () => {
      active = false;
      if (!isQuizRoute) {
        window.removeEventListener('focus', handleFocus);
      }
    };
  }, [isQuizRoute]);

  useEffect(() => {
    return subscribeToHeartsUpdated((nextHearts) => {
      setHearts(nextHearts);
      setHasError(false);
      setIsLoading(false);
    });
  }, []);

  return (
    <div
      className={`inline-flex items-center gap-1.5 rounded-full border border-mainAlt bg-main px-3 py-1.5 text-sm font-bold text-white ${hasError ? 'opacity-80' : ''} ${className ?? ''}`}
      title={
        hasError
          ? 'Hearts unavailable right now'
          : hearts?.heartsRefillAt
            ? `Refill at ${new Date(hearts.heartsRefillAt).toLocaleString()}`
            : 'Global hearts'
      }
    >
      <img src="/icon-images/heart.svg" alt="" className="h-4 w-4" />
      <span>{isLoading ? '...' : hearts?.heartsRemaining ?? '--'}</span>
    </div>
  );
}
