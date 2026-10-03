import { describe, it, expect } from 'vitest';
import { parseIntParam } from './app';

const LIMIT = { min: 0, max: 100 };

/**
 * Pins the integer query-param rule the four demos now share.
 *
 * The rule was measured, not chosen. Against a live backend,
 * `?limit= 5` and `?limit=+5` both return 5 rows, `?limit=0x10` returns 16
 * observations (Spring binds the int through `Integer.decode` semantics, so
 * the hex prefix is honoured), and `?limit=` returns the backend's default page.
 *
 * The Go, Python and JS demos each rejected some of those and accepted others,
 * every one of them the lone outlier in exactly one case. This demo rejected
 * `+5`, because the pattern was `^-?\d+$` with no plus branch.
 */
describe('parseIntParam', () => {
  it('treats absent and empty as the default', () => {
    expect(parseIntParam(undefined, 'limit', LIMIT)).toEqual({ ok: true, value: 0 });
    expect(parseIntParam(null, 'limit', LIMIT)).toEqual({ ok: true, value: 0 });
    expect(parseIntParam('', 'limit', LIMIT)).toEqual({ ok: true, value: 0 });
  });

  it('parses plain integers', () => {
    expect(parseIntParam('5', 'limit', LIMIT)).toEqual({ ok: true, value: 5 });
    expect(parseIntParam('100', 'limit', LIMIT)).toEqual({ ok: true, value: 100 });
  });

  it('accepts a leading plus, as the backend and the other demos do', () => {
    expect(parseIntParam('+5', 'limit', LIMIT)).toEqual({ ok: true, value: 5 });
  });

  it('tolerates surrounding whitespace', () => {
    expect(parseIntParam(' 5', 'limit', LIMIT)).toEqual({ ok: true, value: 5 });
    expect(parseIntParam('5 ', 'limit', LIMIT)).toEqual({ ok: true, value: 5 });
  });

  it('rejects hex rather than passing the backend decode through', () => {
    // The backend answers this one with 16 rows. parseInt('0x10', 10) is NaN and
    // Number('0x10') is 16, so neither is a safe fallback here.
    const r = parseIntParam('0x10', 'limit', LIMIT);
    expect(r.ok).toBe(false);
    expect(r).toHaveProperty('message', 'limit must be an integer');
  });

  it('rejects trailing garbage, fractions and exponents', () => {
    for (const bad of ['10abc', '1.5', '1e3']) {
      expect(parseIntParam(bad, 'limit', LIMIT)).toEqual({
        ok: false,
        message: 'limit must be an integer',
      });
    }
  });

  it('rejects a value outside the range with a range message', () => {
    expect(parseIntParam('101', 'limit', LIMIT)).toEqual({
      ok: false,
      message: 'limit must be between 0 and 100',
    });
    expect(parseIntParam('-1', 'limit', LIMIT)).toEqual({
      ok: false,
      message: 'limit must be between 0 and 100',
    });
  });

  it('never returns a value for input that parseInt would have silently accepted', () => {
    // This is the round-198 bug in a different guise: parseInt('10abc', 10) is 10.
    for (const bad of ['10abc', '0x10', '1e3', '  7abc']) {
      expect(parseIntParam(bad, 'limit', LIMIT).ok).toBe(false);
    }
  });
});
