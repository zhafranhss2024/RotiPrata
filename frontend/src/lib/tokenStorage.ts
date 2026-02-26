const ACCESS_TOKEN_KEY = "rotiprata.accessToken";
const REFRESH_TOKEN_KEY = "rotiprata.refreshToken";
const TOKEN_TYPE_KEY = "rotiprata.tokenType";
export const AUTH_TOKEN_CHANGED_EVENT = "rotiprata:auth-token-changed";

const notifyAuthTokenChanged = () => {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(AUTH_TOKEN_CHANGED_EVENT));
};

export const getAccessToken = () => localStorage.getItem(ACCESS_TOKEN_KEY);
export const getRefreshToken = () => localStorage.getItem(REFRESH_TOKEN_KEY);

export const setTokens = (accessToken: string, refreshToken?: string | null, tokenType?: string | null) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
  if (tokenType) {
    localStorage.setItem(TOKEN_TYPE_KEY, tokenType);
  }
  notifyAuthTokenChanged();
};

export const clearTokens = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(TOKEN_TYPE_KEY);
  notifyAuthTokenChanged();
};
