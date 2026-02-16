import zxcvbn from "zxcvbn";

export const PASSWORD_POLICY = {
  minLength: 12,
  maxLength: 128,
};

export const PASSWORD_POLICY_MESSAGE =
  "Password must be at least 12 characters and include uppercase, lowercase, number, and symbol.";

const UPPER_REGEX = /[A-Z]/;
const LOWER_REGEX = /[a-z]/;
const DIGIT_REGEX = /\d/;
const SYMBOL_REGEX = /[^A-Za-z0-9\s]/;

export type PasswordChecklistItem = {
  key: "length" | "uppercase" | "lowercase" | "digit" | "symbol";
  label: string;
  met: boolean;
};

export const getPasswordChecklist = (password: string): PasswordChecklistItem[] => [
  {
    key: "length",
    label: `At least ${PASSWORD_POLICY.minLength} characters`,
    met: password.length >= PASSWORD_POLICY.minLength && password.length <= PASSWORD_POLICY.maxLength,
  },
  { key: "uppercase", label: "One uppercase letter", met: UPPER_REGEX.test(password) },
  { key: "lowercase", label: "One lowercase letter", met: LOWER_REGEX.test(password) },
  { key: "digit", label: "One number", met: DIGIT_REGEX.test(password) },
  { key: "symbol", label: "One symbol", met: SYMBOL_REGEX.test(password) },
];

export const isPasswordCompliant = (password: string) =>
  getPasswordChecklist(password).every(item => item.met);

export const getPasswordStrength = (password: string) => {
  if (!password) {
    return { score: 0, label: "Too short", percent: 0 };
  }
  const result = zxcvbn(password);
  const score = result.score;
  const labels = ["Very weak", "Weak", "Okay", "Strong", "Very strong"];
  const percent = Math.min(100, Math.round((score / 4) * 100));
  return {
    score,
    label: labels[score] ?? "Weak",
    percent,
  };
};
