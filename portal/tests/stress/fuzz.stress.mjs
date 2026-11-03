// Randomized input stress: thousands of hostile payloads, CSV round trips and
// corrupted PDFs. Each test checks an invariant, not only "did not crash".
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";
import { SEED, nastyString, rng } from "./random.mjs";

const require = createRequire(import.meta.url);
const v = require("../../api/_validation.js");
const { toCsv } = require("../../api/_lib.js");
const g = require("../../api/_gradecard.js");

console.log(`# stress seed ${SEED}`);

const STUDENT_ID = /^[A-Z0-9_-]{3,30}$/;
const COURSE_ID = /^[A-Z]{2,5}[0-9A-Z-]{2,20}$/;
const SLOT_ID = /^[A-Z][A-Z0-9-]{0,10}$/;

function randomValue(r) {
  switch (r.int(0, 9)) {
    case 0: return undefined;
    case 1: return null;
    case 2: return r.int(-1000, 1000);
    case 3: return r.next() * 200 - 50;
    case 4: return nastyString(r);
    case 5: return ["EE1101", "ee5141", "CS6101-A", "MA1101"][r.int(0, 3)];
    case 6: return [];
    case 7: return {};
    case 8: return true;
    default: return "S" + r.int(0, 99999);
  }
}

function randomList(r, maker, maxLength) {
  if (r.chance(0.1)) return randomValue(r);
  return Array.from({ length: r.int(0, maxLength) }, () => maker());
}

test("5,000 random student payloads: valid output or a ValidationError, nothing else", () => {
  const r = rng(SEED);
  let accepted = 0;
  for (let i = 0; i < 5000; i += 1) {
    const payload = {
      student_id: r.chance(0.5) ? `S${r.int(0, 999)}` : randomValue(r),
      programme: r.chance(0.7) ? "B.Tech EE" : randomValue(r),
      year: r.chance(0.7) ? String(r.int(1, 5)) : randomValue(r),
      preferences: randomList(r, () => (r.chance(0.8) ? { course_id: r.chance(0.7) ? `EE${r.int(1000, 1020)}` : randomValue(r) } : randomValue(r)), 12)
    };
    try {
      const out = v.validateStudent(payload);
      accepted += 1;
      assert.match(out.student_id, STUDENT_ID);
      assert.ok(out.programme.length > 0 && out.programme.length <= 60);
      assert.ok(out.preferences.length >= 1 && out.preferences.length <= 10);
      assert.deepEqual(out.preferences.map(p => p.rank), out.preferences.map((_, k) => k + 1), "ranks are 1..n");
      assert.equal(new Set(out.preferences.map(p => p.course_id)).size, out.preferences.length, "no duplicate courses");
      out.preferences.forEach(p => assert.match(p.course_id, COURSE_ID));
    } catch (error) {
      if (!(error instanceof v.ValidationError)) {
        throw new Error(`iteration ${i}: ${error.name}: ${error.message}\npayload ${JSON.stringify(payload)}`);
      }
    }
  }
  assert.ok(accepted > 200, `expected a healthy share of valid payloads, got ${accepted}`);
});

test("5,000 random instructor payloads: valid output or a ValidationError, nothing else", () => {
  const r = rng(SEED + 1);
  let accepted = 0;
  for (let i = 0; i < 5000; i += 1) {
    const payload = {
      instructor: r.chance(0.7) ? `Prof ${r.int(0, 50)}` : randomValue(r),
      course_id: r.chance(0.6) ? `EE${r.int(1000, 1010)}` : randomValue(r),
      prerequisites: r.chance(0.5)
        ? (r.chance(0.5) ? `EE${r.int(1000, 1010)}, EE${r.int(1000, 1010)}` : randomList(r, () => `EE${r.int(1000, 1010)}`, 12))
        : randomValue(r),
      preferences: randomList(r, () => ({
        slot: r.chance(0.8) ? "ABCDEFG"[r.int(0, 6)] : randomValue(r),
        penalty: r.chance(0.8) ? r.int(0, 100) : randomValue(r),
        reason: r.chance(0.5) ? nastyString(r, 300) : randomValue(r)
      }), 22)
    };
    try {
      const out = v.validateInstructor(payload);
      accepted += 1;
      assert.ok(out.instructor.length >= 2 && out.instructor.length <= 80);
      assert.equal(out.instructor_key, out.instructor.toLowerCase());
      assert.ok(out.preferences.length >= 1 && out.preferences.length <= 20);
      for (const p of out.preferences) {
        assert.match(p.slot, SLOT_ID);
        assert.ok(Number.isInteger(p.penalty) && p.penalty >= 0 && p.penalty <= 100);
        assert.ok(p.reason.length <= 240);
        assert.ok(!/[\u0000-\u001f\u007f]/.test(p.reason), "control characters removed");
      }
      assert.equal(new Set(out.preferences.map(p => p.slot)).size, out.preferences.length);
      if (out.prerequisites !== null) {
        assert.ok(out.course_id, "prerequisites only with a course");
        assert.ok(!out.prerequisites.includes(out.course_id), "no self-prerequisite");
        assert.ok(out.prerequisites.length <= 10);
        assert.equal(new Set(out.prerequisites).size, out.prerequisites.length);
      }
    } catch (error) {
      if (!(error instanceof v.ValidationError)) {
        throw new Error(`iteration ${i}: ${error.name}: ${error.message}\npayload ${JSON.stringify(payload)}`);
      }
    }
  }
  // Calibration guard: if the generator stops producing valid payloads the
  // invariants above are never exercised. ~3% of these payloads are valid.
  assert.ok(accepted > 100, `expected enough valid payloads to exercise the invariants, got ${accepted}`);
});

// The admin page parses uploaded solver CSVs with the browser's parser in
// public/assets/common.js. Load that exact file and round-trip it against the
// server's toCsv with hostile cell values.
function browserParser() {
  const source = fs.readFileSync(new URL("../../public/assets/common.js", import.meta.url), "utf8");
  const context = vm.createContext({});
  vm.runInContext(`${source}\nthis.SlotbookPortal = SlotbookPortal;`, context);
  return context.SlotbookPortal.parseCsv;
}

test("2,000 CSV round trips: server toCsv -> browser parseCsv returns the same cells", () => {
  const parseCsv = browserParser();
  const r = rng(SEED + 2);
  const headers = ["course_id", "title", "reasons", "rejected_or_feasible_alternatives"];
  for (let i = 0; i < 2000; i += 1) {
    const rows = Array.from({ length: r.int(1, 8) }, () => ({
      course_id: `EE${r.int(1000, 9999)}`,
      title: nastyString(r, 30).replace(/\u0000/g, ""),
      reasons: nastyString(r, 60).replace(/\u0000/g, ""),
      rejected_or_feasible_alternatives: nastyString(r, 60).replace(/\u0000/g, "")
    }));
    const csv = toCsv(headers, rows);
    const parsed = parseCsv(csv);
    const normalise = value => value.replace(/\r\n/g, "\n");
    assert.equal(parsed.length, rows.length, `iteration ${i}: row count\n${JSON.stringify(csv)}`);
    rows.forEach((row, k) => headers.forEach(h =>
      assert.equal(normalise(parsed[k][h]), normalise(row[h]), `iteration ${i}, row ${k}, ${h}\ncsv ${JSON.stringify(csv)}`)));
  }
});

test("300 corrupted grade-card PDFs: a GradeCardError or a parse, never a server fault or a hang", async () => {
  const original = fs.readFileSync(new URL("../fixtures/synthetic_grade_card.pdf", import.meta.url));
  const r = rng(SEED + 3);
  const silence = console.log;
  const outcomes = { parsed: 0, rejected: 0 };
  for (let i = 0; i < 300; i += 1) {
    const bytes = Buffer.from(original);
    const mode = r.int(0, 3);
    if (mode === 0) for (let k = 0; k < r.int(1, 40); k += 1) bytes[r.int(8, bytes.length - 1)] = r.int(0, 255);
    const mutated = mode === 1 ? bytes.subarray(0, r.int(5, bytes.length - 1))
      : mode === 2 ? Buffer.concat([bytes.subarray(0, r.int(5, bytes.length)), Buffer.from(nastyString(r, 200))])
      : bytes;
    console.log = () => {};
    try {
      const card = await Promise.race([
        g.parseGradeCard(mutated.toString("base64")),
        new Promise((_, reject) => setTimeout(() => reject(new Error("timeout after 5 s")), 5000))
      ]);
      outcomes.parsed += 1;
      card.completed.forEach(c => assert.ok(!["U", "W", "I"].includes(c.grade)));
    } catch (error) {
      if (!(error instanceof g.GradeCardError)) {
        throw new Error(`mutation ${i} (mode ${mode}) escaped as ${error.name}: ${error.message}`);
      }
      outcomes.rejected += 1;
    } finally {
      console.log = silence;
    }
  }
  console.log(`# corrupted PDFs: ${outcomes.parsed} parsed, ${outcomes.rejected} rejected cleanly`);
});
