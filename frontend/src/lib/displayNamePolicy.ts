export const DISPLAY_NAME_POLICY = {
  minLength: 3,
  maxLength: 30,
  pattern: /^[a-zA-Z0-9._-]+$/,
};

export const DISPLAY_NAME_POLICY_MESSAGE =
  "Display name must be 3-30 characters and use letters, numbers, dot, underscore, or hyphen.";

export const normalizeDisplayName = (value: string) => value.trim().toLowerCase();

export const isDisplayNameFormatValid = (value: string) => {
  const trimmed = value.trim();
  if (trimmed.length < DISPLAY_NAME_POLICY.minLength || trimmed.length > DISPLAY_NAME_POLICY.maxLength) {
    return false;
  }
  return DISPLAY_NAME_POLICY.pattern.test(trimmed);
};
