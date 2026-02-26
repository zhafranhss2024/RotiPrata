import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuthContext } from '@/contexts/AuthContext';
import { checkDisplayNameAvailability, setupOAuthProfile } from '@/lib/api';
import {
  isDisplayNameFormatValid,
  DISPLAY_NAME_POLICY_MESSAGE,
} from '@/lib/displayNamePolicy';

const SetupProfilePage = () => {
  const navigate = useNavigate();
  const { checkAuth, isAuthenticated, isLoading, user } = useAuthContext();
  const [displayName, setDisplayName] = useState('');
  const [displayNameStatus, setDisplayNameStatus] = useState<
    'idle' | 'checking' | 'available' | 'taken' | 'invalid' | 'error'
  >('idle');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  // Pre-fill with existing display name from profile
  useEffect(() => {
    if (user?.displayName) {
      setDisplayName(user.displayName);
    }
  }, [user]);

  // Redirect if not authenticated (e.g., session expired)
  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      navigate('/login', { replace: true });
    }
  }, [isLoading, isAuthenticated, navigate]);

  // Real-time display name availability check
  useEffect(() => {
    const raw = displayName;
    if (!raw.trim()) {
      setDisplayNameStatus('idle');
      return;
    }
    if (!isDisplayNameFormatValid(raw)) {
      setDisplayNameStatus('invalid');
      return;
    }
    setDisplayNameStatus('checking');
    const timeout = window.setTimeout(async () => {
      try {
        const result = await checkDisplayNameAvailability(raw);
        setDisplayNameStatus(result.available ? 'available' : 'taken');
      } catch {
        setDisplayNameStatus('error');
      }
    }, 500);
    return () => window.clearTimeout(timeout);
  }, [displayName]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!isDisplayNameFormatValid(displayName)) {
      setError(DISPLAY_NAME_POLICY_MESSAGE);
      return;
    }
    if (displayNameStatus === 'checking') {
      setError('Checking display name availability. Please wait.');
      return;
    }
    if (displayNameStatus === 'taken') {
      setError('Display name already in use. Please choose another.');
      return;
    }
    if (displayNameStatus === 'invalid') {
      setError(DISPLAY_NAME_POLICY_MESSAGE);
      return;
    }

    setSubmitting(true);
    try {
      await setupOAuthProfile(displayName);
      await checkAuth();
      navigate('/', { replace: true });
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Unable to save profile. Please try again.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <MainLayout hideNav>
      <div className="min-h-screen flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md">
          <Card>
            <CardHeader className="text-center">
              <div className="w-16 h-16 mx-auto mb-4 gradient-primary rounded-2xl flex items-center justify-center">
                <span className="text-3xl">🥞</span>
              </div>
              <CardTitle className="text-2xl">One last step</CardTitle>
              <CardDescription>Choose a display name to complete your account setup</CardDescription>
            </CardHeader>

            <CardContent>
              <form onSubmit={handleSubmit} className="space-y-4">
                {error && (
                  <div className="p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
                    {error}
                  </div>
                )}

                <div className="space-y-2">
                  <Label htmlFor="displayName">Display name</Label>
                  <Input
                    id="displayName"
                    type="text"
                    placeholder="cooluser123"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                    required
                    autoFocus
                  />
                  {displayName && (
                    <p className="text-xs text-muted-foreground">
                      {displayNameStatus === 'checking' && 'Checking availability...'}
                      {displayNameStatus === 'available' && 'Display name is available.'}
                      {displayNameStatus === 'taken' && 'Display name is already taken.'}
                      {displayNameStatus === 'invalid' && DISPLAY_NAME_POLICY_MESSAGE}
                      {displayNameStatus === 'error' && 'Unable to check display name right now.'}
                    </p>
                  )}
                </div>

                <Button
                  type="submit"
                  className="w-full gradient-primary border-0"
                  size="lg"
                  disabled={submitting || displayNameStatus === 'checking' || displayNameStatus === 'taken'}
                >
                  {submitting ? 'Saving...' : 'Continue'}
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
};

export default SetupProfilePage;
