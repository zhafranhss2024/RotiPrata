import { useState, useEffect, useCallback } from 'react';
import type { ThemePreference } from '@/types';
import { fetchThemePreference, updateThemePreference } from '@/lib/api';

// Backend integration: /api/users/me/preferences.
// If mocks are enabled, this falls back to localStorage and dummy data.

export function useTheme() {
  const [theme, setThemeState] = useState<ThemePreference>('system');
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');

  // Get stored theme preference
  useEffect(() => {
    const loadTheme = async () => {
      try {
        const preference = await fetchThemePreference();
        if (preference) {
          setThemeState(preference);
          return;
        }
      } catch (error) {
        console.warn('Theme preference fetch failed, using local fallback', error);
      }

      // Dummy fallback: localStorage
      const stored = localStorage.getItem('theme-preference') as ThemePreference | null;
      if (stored) {
        setThemeState(stored);
      }
    };

    loadTheme();
  }, []);

  // Resolve system theme
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    
    const updateResolvedTheme = () => {
      if (theme === 'system') {
        setResolvedTheme(mediaQuery.matches ? 'dark' : 'light');
      } else {
        setResolvedTheme(theme as 'light' | 'dark');
      }
    };

    updateResolvedTheme();
    mediaQuery.addEventListener('change', updateResolvedTheme);
    
    return () => mediaQuery.removeEventListener('change', updateResolvedTheme);
  }, [theme]);

  // Apply theme to document
  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove('light', 'dark');
    root.classList.add(resolvedTheme);
  }, [resolvedTheme]);

  const setTheme = useCallback((newTheme: ThemePreference) => {
    setThemeState(newTheme);
    localStorage.setItem('theme-preference', newTheme);

    // Fire-and-forget update to backend. In mock mode, this is a no-op.
    updateThemePreference(newTheme).catch((error) => {
      console.warn('Theme preference update failed', error);
    });
  }, []);

  const toggleTheme = useCallback(() => {
    const nextTheme = resolvedTheme === 'light' ? 'dark' : 'light';
    setTheme(nextTheme);
  }, [resolvedTheme, setTheme]);

  return {
    theme,
    resolvedTheme,
    setTheme,
    toggleTheme,
    isDark: resolvedTheme === 'dark',
  };
}
