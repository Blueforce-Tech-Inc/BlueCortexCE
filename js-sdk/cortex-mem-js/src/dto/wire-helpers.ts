// ============================================================
// Wire format safe type conversion helpers
// ============================================================

/**
 * Safely convert unknown value to string.
 * Returns undefined for null/undefined/non-string.
 */
export function safeString(v: unknown): string | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'string') return v;
  return String(v);
}

/**
 * Safely convert unknown value to string with default.
 */
export function safeStringOr(v: unknown, fallback: string): string {
  return safeString(v) ?? fallback;
}

/**
 * Safely convert unknown value to number.
 * Returns undefined for null/undefined/non-number/NaN.
 */
export function safeNumber(v: unknown): number | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'number' && !Number.isNaN(v)) return v;
  if (typeof v === 'string') {
    const n = Number(v);
    if (!Number.isNaN(n)) return n;
  }
  return undefined;
}

/**
 * Safely convert unknown value to number with default.
 */
export function safeNumberOr(v: unknown, fallback: number): number {
  return safeNumber(v) ?? fallback;
}

/**
 * Safely convert unknown value to string array.
 * Returns undefined for null/undefined/non-array.
 * Converts non-string items via String().
 */
export function safeStringArray(v: unknown): string[] | undefined {
  if (v === null || v === undefined) return undefined;
  if (!Array.isArray(v)) return undefined;
  const result: string[] = [];
  for (const item of v) {
    if (typeof item === 'string') result.push(item);
    else if (item !== null && item !== undefined) result.push(String(item));
  }
  return result;
}

/**
 * Safely extract a Record<string, unknown> from wire data.
 * Returns undefined for null/undefined/non-object.
 */
export function safeRecord(v: unknown): Record<string, unknown> | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'object' && !Array.isArray(v)) return v as Record<string, unknown>;
  return undefined;
}
