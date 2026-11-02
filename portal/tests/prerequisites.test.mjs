// Prerequisite warnings: the pure checker, the completion-source registry,
// and the full HTTP flow from professor declaration to admin export.
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { ADMIN, freshDatabase, instructor, parsed, request, responseRecorder, student } from "./helpers.mjs";

const require = createRequire(import.meta.url);
const { CODES, checkPrerequisites, warningsForSnapshot } = require("../api/_prerequisites.js");
const { SOURCES, completionSource } = require("../api/_completions.js");
const submit = require("../api/submit.js");
const admin = require("../api/admin.js");

const RULES = [
  { course_id: "EE5141", prereq_course_id: "EE3110" },
  { course_id: "EE5141", prereq_course_id: "EE1101" },
  { course_id: "EE5112", prereq_course_id: "EE3110" }
];
const ranked = ids => ids.map((course_id, index) => ({ course_id, rank: index + 1 }));
const card = fs.readFileSync(new URL("./fixtures/synthetic_grade_card.pdf", import.meta.url)).toString("base64");

async function call(handler, req) {
  const res = responseRecorder();
  await handler(req, res);
  return { status: res.statusCode, body: parsed(res) };
}

test("no warnings when every prerequisite is completed", () => {
  assert.deepEqual(checkPrerequisites(ranked(["EE5141"]), RULES, ["EE3110", "EE1101"]), []);
});

test("one warning per missing prerequisite, in a stable order", () => {
  const warnings = checkPrerequisites(ranked(["EE5141", "EE5112"]), RULES, []);
  assert.deepEqual(warnings.map(w => [w.code, w.course_id, w.prereq_course_id]), [
    [CODES.MISSING, "EE5141", "EE1101"],
    [CODES.MISSING, "EE5141", "EE3110"],
    [CODES.MISSING, "EE5112", "EE3110"]
  ]);
  assert.match(warnings[0].message, /EE5141 lists EE1101/);
});

test("courses without rules never warn", () => {
  assert.deepEqual(checkPrerequisites(ranked(["EE9999"]), RULES, []), []);
  assert.deepEqual(checkPrerequisites(ranked(["EE5141"]), [], []), []);
});

test("without any completion record a single summary warning is given", () => {
  const warnings = checkPrerequisites(ranked(["EE5141", "EE9999", "EE5112"]), RULES, [], { hasRecord: false });
  assert.equal(warnings.length, 1);
  assert.equal(warnings[0].code, CODES.NO_RECORD);
  assert.equal(warnings[0].course_id, "EE5141|EE5112");
});

test("snapshot warnings cover every student", () => {
  const rows = warningsForSnapshot({
    students: [
      { student_id: "S1", course_id: "EE5112", rank: 1 },
      { student_id: "S2", course_id: "EE5112", rank: 1 },
      { student_id: "S3", course_id: "EE5112", rank: 1 }
    ],
    prerequisites: RULES,
    completions: [{ student_id: "S1", course_id: "EE3110" }, { student_id: "S2", course_id: "EE1100" }]
  });
  assert.deepEqual(rows.map(row => [row.student_id, row.code]), [["S2", CODES.MISSING], ["S3", CODES.NO_RECORD]]);
});

test("the grade card is the default completion source and unknown sources fail loudly", () => {
  assert.equal(completionSource().id, "grade_card");
  assert.equal(SOURCES.grade_card.verified, false);
  assert.throws(() => completionSource("transcript_api"), /Unknown COMPLETION_SOURCE/);
});

test("end to end: professor declares, student uploads, warnings returned but submission saved", async () => {
  await freshDatabase();
  const prof = await call(submit, request("POST", { type: "instructor", payload: instructor({ prerequisites: "EE1101, EE2001" }) }));
  assert.equal(prof.status, 200);
  assert.equal(prof.body.prerequisites_saved, 2);

  // The synthetic card passes EE1101 (retake) but withdrew from EE2001.
  const { status, body } = await call(submit, request("POST", {
    type: "student",
    payload: student({ student_id: "XX00Z999", preferences: [{ course_id: "EE5141" }], grade_card: card })
  }));
  assert.equal(status, 200, "a warning must not block the submission");
  assert.equal(body.rows_saved, 1);
  assert.equal(body.completed_courses_on_record, 7);
  assert.deepEqual(body.warnings.map(w => [w.code, w.prereq_course_id]), [[CODES.MISSING, "EE2001"]]);
  assert.match(body.message, /with warnings/);
});

test("end to end: an unreadable card and a roll mismatch are warnings, not errors", async () => {
  await freshDatabase();
  const unreadable = await call(submit, request("POST", {
    type: "student", payload: student({ grade_card: Buffer.from("not a pdf").toString("base64") })
  }));
  assert.equal(unreadable.status, 200);
  assert.equal(unreadable.body.warnings[0].code, "GRADE_CARD_UNREADABLE");

  const mismatch = await call(submit, request("POST", { type: "student", payload: student({ student_id: "S777", grade_card: card }) }));
  assert.equal(mismatch.status, 200);
  assert.equal(mismatch.body.warnings[0].code, "ROLL_NUMBER_MISMATCH");
});

test("end to end: resubmitting without a card keeps the earlier card", async () => {
  await freshDatabase();
  await call(submit, request("POST", { type: "instructor", payload: instructor({ prerequisites: "EE1101" }) }));
  await call(submit, request("POST", { type: "student", payload: student({ student_id: "XX00Z999", grade_card: card }) }));
  const again = await call(submit, request("POST", { type: "student", payload: student({ student_id: "XX00Z999" }) }));
  assert.equal(again.body.completed_courses_on_record, 7);
  assert.deepEqual(again.body.warnings, []);
});

test("end to end: admin snapshot exports prerequisites, completions and warnings for the Java CLI", async () => {
  await freshDatabase();
  await call(submit, request("POST", { type: "instructor", payload: instructor({ prerequisites: "EE1101" }) }));
  await call(submit, request("POST", { type: "student", payload: student({ student_id: "S100" }) }));
  const { body } = await call(admin, request("POST", { action: "snapshot" }, ADMIN));
  assert.equal(body.prerequisites_csv, "course_id,prereq_course_id\nEE5141,EE1101\n");
  assert.equal(body.completed_courses_csv, "student_id,course_id,grade,term,source\n");
  assert.equal(body.prerequisite_warnings.length, 1);
  assert.match(body.prerequisite_warnings_csv, /^student_id,code,course_id,prereq_course_id,message\nS100,NO_COMPLETION_RECORD,EE5141,,/);
});
