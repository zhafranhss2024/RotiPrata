import { useState, useEffect, useCallback } from 'react';
import type { ThemePreference } from '@/types';
import { fetchThemePreference, updateThemePreference } from '@/lib/api';
import { AUTH_TOKEN_CHANGED_EVENT, getAccessToken } from '@/lib/tokenStorage';

const THEME_STORAGE_KEY = 'theme-preference';

const isThemePreference = (value: string | null): value is ThemePreference => {
  return value === 'light' || value === 'dark' || value === 'system';
};

const readStoredTheme = (): ThemePreference | null => {
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  return isThemePreference(stored) ? stored : null;
};

export function useTheme() {
  const [theme, setThemeState] = useState<ThemePreference>(() => readStoredTheme() ?? 'system');
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');

  const loadThemePreference = useCallback(async () => {
    const token = getAccessToken();
    if (token) {
      try {
        const preference = await fetchThemePreference();
        if (isThemePreference(preference)) {
          setThemeState(preference);
          localStorage.setItem(THEME_STORAGE_KEY, preference);
          return;
        }
      } catch (error) {
        console.warn('Theme preference fetch failed, using local fallback', error);
      }
    }

    const stored = readStoredTheme();
    if (stored) {
      setThemeState(stored);
    }
  }, []);

  // Load theme at startup and re-sync when auth token changes.
  useEffect(() => {
    const bootstrapTimer = window.setTimeout(() => {
      void loadThemePreference();
    }, 0);

    const handleAuthTokenChanged = () => {
      void loadThemePreference();
    };

    const handleStorage = (event: StorageEvent) => {
      if (event.key === THEME_STORAGE_KEY && isThemePreference(event.newValue)) {
        setThemeState(event.newValue);
        return;
      }

      if (
        event.key === 'rotiprata.accessToken' ||
        event.key === 'rotiprata.refreshToken' ||
        event.key === 'rotiprata.tokenType'
      ) {
        void loadThemePreference();
      }
    };

    window.addEventListener(AUTH_TOKEN_CHANGED_EVENT, handleAuthTokenChanged);
    window.addEventListener('storage', handleStorage);

    return () => {
      window.clearTimeout(bootstrapTimer);
      window.removeEventListener(AUTH_TOKEN_CHANGED_EVENT, handleAuthTokenChanged);
      window.removeEventListener('storage', handleStorage);
    };
  }, [loadThemePreference]);

  // Resolve system theme
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    const updateResolvedTheme = () => {
      if (theme === 'system') {
        setResolvedTheme(mediaQuery.matches ? 'dark' : 'light');
      } else {
        setResolvedTheme(theme);
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
    localStorage.setItem(THEME_STORAGE_KEY, newTheme);

    if (!getAccessToken()) {
      return;
    }

    updateThemePreference(newTheme).catch((error) => {
      console.warn('Theme preference update failed', error);
    });
  }, []);

  const toggleTheme = useCallback(() => {
    const nextTheme: ThemePreference = resolvedTheme === 'light' ? 'dark' : 'light';
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
