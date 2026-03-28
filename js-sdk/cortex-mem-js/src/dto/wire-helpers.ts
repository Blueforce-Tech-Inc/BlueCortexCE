// ============================================================
// Wire format safe type conversion helpers
// ============================================================

/**
 * Safely convert unknown value to string.
 * Returns undefined for null/undefined/non-primitive.
 * Only converts primitives (string, number, boolean, bigint) to avoid
 * silent "[object Object]" corruption from objects/arrays.
 */
export function safeString(v: unknown): string | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v === 'string') return v;
  if (typeof v === 'number' || typeof v === 'boolean' || typeof v === 'bigint') return String(v);
  return undefined;
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
 * Returns undefined for null/undefined/non-object/non-plain-object.
 * Rejects arrays, Date instances, and other class instances.
 */
export function safeRecord(v: unknown): Record<string, unknown> | undefined {
  if (v === null || v === undefined) return undefined;
  if (typeof v !== 'object') return undefined;
  if (Array.isArray(v)) return undefined;
  // Reject Date, RegExp, and other non-plain objects
  if (Object.prototype.toString.call(v) !== '[object Object]') return undefined;
  return v as Record<string, unknown>;
}

/**
 * Check multiple key variants on a wire-format object, return first non-null value.
 * Handles backend SNAKE_CASE output with camelCase fallback.
 */
export function firstNonNullOr(
  raw: Record<string, unknown>,
  keys: string[],
): unknown {
  for (const key of keys) {
    const val = raw[key];
    if (val !== null && val !== undefined) return val;
  }
  return undefined;
}
