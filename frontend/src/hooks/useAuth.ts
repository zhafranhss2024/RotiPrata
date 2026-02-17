import { useState, useEffect, useCallback } from 'react';
import type { Profile, AppRole } from '@/types';
import {
  fetchCurrentUser,
  fetchUserRoles,
  loginUser,
  logoutUser,
  registerUser,
} from '@/lib/api';
import { ApiError } from '@/lib/apiClient';
import { clearTokens, getAccessToken, setTokens } from '@/lib/tokenStorage';
import { formatRateLimitMessage } from '@/lib/rateLimit';

// NOTE: Real backend API calls live in src/lib/api.ts.
// When VITE_USE_MOCKS=true or set to auto without a backend, calls fall back to dummy data.

interface AuthState {
  user: Profile | null;
  roles: AppRole[];
  isLoading: boolean;
  isAuthenticated: boolean;
}

export function useAuth() {
  const [state, setState] = useState<AuthState>({
    user: null,
    roles: [],
    isLoading: true,
    isAuthenticated: false,
  });

  // Check auth status on mount
  useEffect(() => {
    checkAuth();
  }, []);

  const checkAuth = useCallback(async () => {
    setState(prev => ({ ...prev, isLoading: true }));

    try {
      const accessToken = getAccessToken();
      if (!accessToken) {
        setState({ user: null, roles: [], isLoading: false, isAuthenticated: false });
        return;
      }
      const [user, roles] = await Promise.all([fetchCurrentUser(), fetchUserRoles()]);
      if (user) {
        setState({
          user,
          roles,
          isLoading: false,
          isAuthenticated: true,
        });
      } else {
        setState({ user: null, roles: [], isLoading: false, isAuthenticated: false });
      }
    } catch (error) {
      // Dummy fallback: if the backend is unavailable and mocks are disabled, show logged-out state.
      console.error('Auth check failed', error);
      clearTokens();
      setState({ user: null, roles: [], isLoading: false, isAuthenticated: false });
    }
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setState(prev => ({ ...prev, isLoading: true }));

    try {
      const result = await loginUser(email, password);
      if (result.accessToken) {
        setTokens(result.accessToken, result.refreshToken, result.tokenType);
        await checkAuth();
        return { success: true };
      }
      return { success: false, error: result.message || 'Invalid credentials' };
    } catch (error) {
      console.error('Login failed', error);
      setState(prev => ({ ...prev, isLoading: false }));
      if (error instanceof ApiError) {
        if (error.code === 'rate_limited') {
          return { success: false, error: 'Confirmation email not sent. Please wait 1 hour before trying again.' };
        }
        if (error.code === 'invalid_credentials') {
          return { success: false, error: 'Invalid email or password.' };
        }
        if (error.message) {
          return { success: false, error: error.message };
        }
        return { success: false, error: 'Login failed' };
      }
      return { success: false, error: 'Login failed' };
    }
  }, [checkAuth]);

  const register = useCallback(async (email: string, password: string, displayName: string, isGenAlpha?: boolean) => {
    setState(prev => ({ ...prev, isLoading: true }));

    try {
      const result = await registerUser(email, password, displayName, isGenAlpha);
      if (result.accessToken) {
        setTokens(result.accessToken, result.refreshToken, result.tokenType);
        await checkAuth();
        return { success: true };
      }
      if (result.requiresEmailConfirmation) {
        setState(prev => ({ ...prev, isLoading: false }));
        return {
          success: true,
          requiresVerification: true,
          message: result.message || 'Check your email to confirm your account.',
        };
      }
      return { success: false, error: result.message || 'Registration failed' };
    } catch (error) {
      console.error('Registration failed', error);
      setState(prev => ({ ...prev, isLoading: false }));
      if (error instanceof ApiError) {
        if (error.code === 'email_in_use') {
          return { success: false, error: 'Email already in use.' };
        }
        if (error.code === 'username_in_use') {
          return { success: false, error: 'Display name already in use.' };
        }
        if (error.code === 'rate_limited' || error.status === 429) {
          return {
            success: false,
            error: 'Confirmation email not sent. Please wait 1 hour before trying again.',
          };
        }
        if (error.fieldErrors) {
          const first = Object.values(error.fieldErrors)[0];
          return { success: false, error: first || error.message || 'Registration failed' };
        }
        return { success: false, error: error.message || 'Registration failed' };
      }
      return { success: false, error: 'Registration failed' };
    }
  }, [checkAuth]);

  const logout = useCallback(async () => {
    try {
      await logoutUser();
    } catch (error) {
      console.error('Logout failed', error);
    } finally {
      clearTokens();
      setState({
        user: null,
        roles: [],
        isLoading: false,
        isAuthenticated: false,
      });
    }
  }, []);

  const hasRole = useCallback((role: AppRole) => {
    return state.roles.includes(role);
  }, [state.roles]);

  const isAdmin = useCallback(() => hasRole('admin'), [hasRole]);
  const isContributor = useCallback(() => hasRole('user'), [hasRole]);
  const isLearner = useCallback(() => hasRole('user') || state.isAuthenticated, [hasRole, state.isAuthenticated]);

  return {
    ...state,
    login,
    register,
    logout,
    checkAuth,
    hasRole,
    isAdmin,
    isContributor,
    isLearner,
  };
}
