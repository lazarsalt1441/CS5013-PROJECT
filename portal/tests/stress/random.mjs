// Seeded randomness for stress tests. Every suite prints its seed; replay a
// failure with STRESS_SEED=<seed> npm run test:stress.

export const SEED = Number(process.env.STRESS_SEED || 20261104);

/** mulberry32: small, fast, deterministic PRNG. */
export function rng(seed = SEED) {
  let state = seed >>> 0;
  const next = () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const int = (lo, hi) => lo + Math.floor(next() * (hi - lo + 1));
  const pick = list => list[int(0, list.length - 1)];
  const chance = p => next() < p;
  const shuffle = list => {
    const copy = [...list];
    for (let i = copy.length - 1; i > 0; i -= 1) {
      const j = int(0, i);
      [copy[i], copy[j]] = [copy[j], copy[i]];
    }
    return copy;
  };
  const sample = (list, n) => shuffle(list).slice(0, n);
  return { next, int, pick, chance, shuffle, sample };
}

/** Course IDs in the portal's accepted shape. */
export function courseIds(count, prefix = "EE") {
  return Array.from({ length: count }, (_, i) => `${prefix}${String(1000 + i)}`);
}

/** Awkward strings: delimiters, quotes, control characters, unicode, blanks. */
export function nastyString(r, maxLength = 40) {
  const pieces = [",", '"', '""', "\n", "\r\n", "\t", " ", "\u0000", "\u007f", "é", "数", "🙂", "a", "Z", "9", "-", "_", "'", "\\", ";", "<script>", "%"];
  let out = "";
  const length = r.int(0, maxLength);
  while (out.length < length) out += r.pick(pieces);
  return out;
}

/** Fails the test if fn takes longer than limitMs, printing the timing. */
export async function within(limitMs, label, fn) {
  const start = performance.now();
  const result = await fn();
  const elapsed = performance.now() - start;
  if (elapsed > limitMs) throw new Error(`${label} took ${elapsed.toFixed(0)} ms; limit ${limitMs} ms`);
  return { result, elapsed };
}
