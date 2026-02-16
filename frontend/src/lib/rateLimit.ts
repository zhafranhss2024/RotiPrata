export const formatRetryAfterMinutes = (retryAfterSeconds?: number) => {
  if (!retryAfterSeconds || retryAfterSeconds <= 0) {
    return null;
  }
  const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
  return minutes;
};

export const formatRateLimitMessage = (retryAfterSeconds?: number) => {
  const minutes = formatRetryAfterMinutes(retryAfterSeconds);
  if (!minutes) {
    return "Too many attempts. Try again later.";
  }
  return `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? "" : "s"}.`;
};
