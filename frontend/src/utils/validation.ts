/**
 * Client-side validation helpers that mirror backend rules from API.md.
 */

export const ALIAS_REGEX = /^[a-zA-Z0-9_-]{3,32}$/;
export const TAG_REGEX = /^[a-zA-Z0-9_-]+$/;
export const MAX_TAGS = 10;
export const MAX_URL_LENGTH = 2048;
export const MAX_CLICKS_LIMIT = 1_000_000;

export function isHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

export function validateOriginalUrl(value: string): string | null {
  if (!value || !value.trim()) return 'Original URL is required';
  if (value.length > MAX_URL_LENGTH) return `URL must be ≤ ${MAX_URL_LENGTH} characters`;
  if (!isHttpUrl(value)) return 'Must be a valid http(s) URL';
  return null;
}

export function validateAlias(value: string): string | null {
  if (!value) return 'Alias is required';
  if (!ALIAS_REGEX.test(value)) {
    return 'Alias must be 3–32 chars (letters, digits, _ or -)';
  }
  return null;
}

export function validateTag(value: string): string | null {
  if (!value) return null;
  if (value.length > 32) return 'Tag must be ≤ 32 characters';
  if (!TAG_REGEX.test(value)) return 'Tag may only contain letters, digits, _ or -';
  return null;
}

export function validateTags(values: string[]): string | null {
  if (values.length > MAX_TAGS) return `At most ${MAX_TAGS} tags allowed`;
  for (const v of values) {
    const err = validateTag(v);
    if (err) return `"${v}": ${err}`;
  }
  return null;
}

export function validateMaxClicks(value: number | null | undefined): string | null {
  if (value === null || value === undefined) return null;
  if (!Number.isFinite(value) || !Number.isInteger(value)) return 'Must be an integer';
  if (value < 1) return 'Must be at least 1';
  if (value > MAX_CLICKS_LIMIT) return `Must be at most ${MAX_CLICKS_LIMIT.toLocaleString()}`;
  return null;
}

export function validateFutureDate(value: string | null | undefined): string | null {
  if (!value) return null;
  const ts = Date.parse(value);
  if (Number.isNaN(ts)) return 'Invalid date';
  if (ts <= Date.now()) return 'Expiration must be in the future';
  return null;
}
