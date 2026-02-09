import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MainLayout } from '@/components/layout/MainLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { setTokens } from '@/lib/tokenStorage';
import { useAuthContext } from '@/contexts/AuthContext';

const parseAuthParams = () => {
  const hash = window.location.hash.replace(/^#/, '');
  const query = window.location.search.replace(/^\?/, '');
  const params = new URLSearchParams(hash || query);

  return {
    accessToken: params.get('access_token'),
    refreshToken: params.get('refresh_token'),
    tokenType: params.get('token_type'),
    expiresIn: params.get('expires_in'),
    type: params.get('type'),
    error: params.get('error') || params.get('error_description'),
  };
};

const AuthCallbackPage = () => {
  const navigate = useNavigate();
  const { checkAuth } = useAuthContext();
  const [error, setError] = useState('');

  useEffect(() => {
    const { accessToken, refreshToken, tokenType, expiresIn, type, error: authError } = parseAuthParams();

    if (authError) {
      setError(authError);
      return;
    }

    if (!accessToken) {
      setError('Missing access token. Please try signing in again.');
      return;
    }

    if (type === 'recovery') {
      window.history.replaceState({}, document.title, '/auth/callback');
      navigate(`/reset-password#access_token=${encodeURIComponent(accessToken)}`, { replace: true });
      return;
    }

    setTokens(accessToken, refreshToken, tokenType);
    window.history.replaceState({}, document.title, '/auth/callback');

    checkAuth()
      .then(() => navigate('/'))
      .catch((err) => {
        console.error('Auth callback failed', err);
        setError('Unable to complete sign-in.');
      });
  }, [checkAuth, navigate]);

  return (
    <MainLayout hideNav>
      <div className="min-h-screen flex items-center justify-center px-4 py-8">
        <div className="w-full max-w-md">
          <Card>
            <CardHeader className="text-center">
              <CardTitle className="text-2xl">Completing sign-in</CardTitle>
              <CardDescription>
                {error ? 'Something went wrong.' : 'Setting up your session...'}
              </CardDescription>
            </CardHeader>
            <CardContent className="text-center space-y-4">
              {error ? (
                <>
                  <div className="p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
                    {error}
                  </div>
                  <Button onClick={() => navigate('/login')}>Back to Sign In</Button>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">Please wait...</p>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </MainLayout>
  );
};

export default AuthCallbackPage;
