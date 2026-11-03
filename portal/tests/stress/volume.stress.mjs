// Volume stress for the pure domain code: large inputs checked against an
// independent oracle, plus scaling checks that catch accidental quadratic
// algorithms without depending on how fast the CI machine is.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { SEED, courseIds, rng } from "./random.mjs";

const require = createRequire(import.meta.url);
const { warningsForSnapshot, CODES } = require("../../api/_prerequisites.js");
const g = require("../../api/_gradecard.js");
const { toCsv } = require("../../api/_lib.js");

/** Random snapshot: students, ranked courses, rules and completions (incl. U/W/I and retakes). */
function snapshotOf(r, students, courses, ruleCount) {
  const ids = courseIds(courses);
  const rules = new Map();
  while (rules.size < ruleCount) {
    const course = r.pick(ids);
    const prereq = r.pick(ids);
    if (course !== prereq) rules.set(`${course}>${prereq}`, { course_id: course, prereq_course_id: prereq });
  }
  const snap = { students: [], prerequisites: [...rules.values()], completions: [] };
  for (let s = 0; s < students; s += 1) {
    const sid = `S${String(s).padStart(5, "0")}`;
    r.sample(ids, r.int(1, 10)).forEach((course_id, k) => snap.students.push({ student_id: sid, course_id, rank: k + 1 }));
    if (r.chance(0.8)) {
      for (const course_id of r.sample(ids, r.int(0, 25))) {
        snap.completions.push({ student_id: sid, course_id, grade: r.pick(["S", "A", "B", "C", "D", "E", "P", "U", "W", "I"]) });
        if (r.chance(0.1)) snap.completions.push({ student_id: sid, course_id, grade: r.pick(["A", "U"]) });
      }
    }
  }
  return snap;
}

/** Independent, deliberately naive implementation of the documented rule. */
function oracle(snap) {
  const out = [];
  const students = [...new Set(snap.students.map(row => row.student_id))];
  for (const sid of students) {
    const ranked = snap.students.filter(row => row.student_id === sid).sort((a, b) => a.rank - b.rank).map(row => row.course_id);
    const records = snap.completions.filter(row => row.student_id === sid);
    const done = new Set(records.filter(row => !["U", "W", "I"].includes(row.grade)).map(row => row.course_id));
    const constrained = ranked.filter(course => snap.prerequisites.some(rule => rule.course_id === course));
    if (!constrained.length) continue;
    if (!records.length) {
      out.push(`${sid}|${CODES.NO_RECORD}|${constrained.join("|")}|`);
      continue;
    }
    for (const course of constrained) {
      const needs = snap.prerequisites.filter(rule => rule.course_id === course).map(rule => rule.prereq_course_id).sort();
      for (const prereq of needs) if (!done.has(prereq)) out.push(`${sid}|${CODES.MISSING}|${course}|${prereq}`);
    }
  }
  return out;
}

const key = w => `${w.student_id}|${w.code}|${w.course_id}|${w.prereq_course_id}`;

test("prerequisite warnings for 3,000 random students equal an independent oracle", () => {
  const snap = snapshotOf(rng(SEED), 3000, 120, 400);
  const actual = warningsForSnapshot(snap).map(key);
  const expected = oracle(snap);
  assert.ok(expected.length > 1000, `fixture should produce many warnings, got ${expected.length}`);
  assert.deepEqual(actual, expected);
});

/** Fastest of five runs after two warm-ups, in ms: strips JIT and GC noise. */
function timeOf(fn) {
  fn(); fn();
  let best = Infinity;
  for (let i = 0; i < 5; i += 1) {
    const start = performance.now();
    fn();
    best = Math.min(best, performance.now() - start);
  }
  return best;
}

/**
 * Cost of one run on 10x input, relative to the same total work done as ten
 * runs on the small input. Linear code gives about 1 (a little more from
 * cache effects, or log n from a sort); quadratic gives about 10. Comparing
 * equal work keeps both timings long, so a busy CI runner cannot decide it.
 */
function growth(fn, small, large) {
  return timeOf(() => fn(large)) / timeOf(() => { for (let i = 0; i < 10; i += 1) fn(small); });
}

const MAX_GROWTH = 5;

test("warning computation scales linearly: one 10x run costs about ten small runs", () => {
  const small = snapshotOf(rng(SEED + 1), 2000, 150, 400);
  const large = snapshotOf(rng(SEED + 1), 20000, 150, 400);
  const ratio = growth(warningsForSnapshot, small, large);
  console.log(`# warnings: 10x students -> ${ratio.toFixed(2)}x the equal small work`);
  assert.ok(ratio < MAX_GROWTH, `growth ${ratio.toFixed(2)}x suggests a quadratic step`);
});

/** PDF text items for a grade card with `rows` course rows, shuffled like a real content stream. */
function textItems(r, rows) {
  const items = [];
  for (let k = 0; k < rows; k += 1) {
    const y = 10_000 - k * 11;
    // Course codes stay in the 4-digit form the parser accepts (EE1000-EE9999).
    const cells = [String(1 + (k % 8)).padStart(2, "0"), `EE${1000 + (k % 9000)}`, "Course title", "10", r.pick(["S", "A", "U"]), "VG", "2025"];
    [31, 85, 141, 441, 471, 509, 552].forEach((x, c) => items.push({ str: cells[c], transform: [1, 0, 0, 1, x, y + r.next() - 0.5] }));
  }
  return r.shuffle(items);
}

test("grade-card row building scales linearly: one 10x run costs about ten small runs", () => {
  const r = rng(SEED + 2);
  const small = textItems(r, 2000);
  const large = textItems(r, 20000);
  const ratio = growth(items => g.itemsToRows(items), small, large);
  console.log(`# itemsToRows: 10x items -> ${ratio.toFixed(2)}x the equal small work`);
  assert.ok(ratio < MAX_GROWTH, `growth ${ratio.toFixed(2)}x suggests a quadratic step`);
});

test("a 20,000-row grade card is rebuilt and parsed exactly", () => {
  const r = rng(SEED + 3);
  const rows = g.itemsToRows(textItems(r, 20000));
  assert.equal(rows.length, 20000);
  rows.forEach(cells => assert.equal(cells.length, 7));
  const card = g.parseGradeCardRows(rows);
  assert.equal(card.attempts.length, 20000);
  assert.equal(card.unreadable_rows.length, 0);
  assert.ok(card.completed.every(c => c.grade !== "U"));
});

test("CSV export of 100,000 rows has one line per row and stays linear", () => {
  const headers = ["student_id", "course_id", "rank"];
  const make = n => Array.from({ length: n }, (_, i) => ({ student_id: `S${i}`, course_id: i % 7 ? "EE1101" : 'EE "x", y', rank: (i % 10) + 1 }));
  const csv = toCsv(headers, make(100_000));
  assert.equal(csv.split("\n").length - 1, 100_001);
  const small = make(10_000);
  const large = make(100_000);
  const ratio = growth(rows => toCsv(headers, rows), small, large);
  console.log(`# toCsv: 10x rows -> ${ratio.toFixed(2)}x the equal small work`);
  assert.ok(ratio < MAX_GROWTH, `growth ${ratio.toFixed(2)}x suggests a quadratic step`);
});
