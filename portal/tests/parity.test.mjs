// Cross-language contract: the portal checker must produce exactly the
// warnings the Java PrerequisiteChecker produces for the same CSV files.
// data/prerequisite-warnings/expected_warnings.csv is the shared golden
// file; PrerequisiteWarningsDatasetTest checks the Java side against it.
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";

const require = createRequire(import.meta.url);
const { warningsForSnapshot } = require("../api/_prerequisites.js");
const { WARNING_HEADERS } = require("../api/_exports.js");

const dir = new URL("../../data/prerequisite-warnings/", import.meta.url);

function parseCsv(text) {
  const rows = [];
  let row = [], field = "", quoted = false;
  for (let i = 0; i < text.length; i += 1) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i += 1; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ",") { row.push(field); field = ""; }
    else if (c === "\n") { row.push(field.replace(/\r$/, "")); if (row.some(Boolean)) rows.push(row); row = []; field = ""; }
    else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  const [headers, ...body] = rows;
  return { headers, rows: body.map(values => Object.fromEntries(headers.map((h, i) => [h, values[i] ?? ""]))) };
}

const read = name => parseCsv(fs.readFileSync(new URL(name, dir), "utf8"));

test("portal warnings equal the shared golden file used by the Java tests", () => {
  const golden = read("expected_warnings.csv");
  assert.deepEqual(golden.headers, WARNING_HEADERS, "golden file uses the export columns");
  const warnings = warningsForSnapshot({
    students: read("student_preferences.csv").rows.map(row => ({ ...row, rank: Number(row.rank) })),
    prerequisites: read("prerequisites.csv").rows,
    completions: read("completed_courses.csv").rows
  });
  assert.deepEqual(warnings.map(w => WARNING_HEADERS.map(key => w[key])), golden.rows.map(r => WARNING_HEADERS.map(key => r[key])));
});

test("U and W rows from a CSV import do not count as completed", () => {
  const golden = read("expected_warnings.csv").rows;
  assert.ok(golden.some(r => r.student_id === "S002" && r.prereq_course_id === "EE3110"), "S002 has EE3110 only with grade U");
  assert.equal(golden.filter(r => r.student_id === "S005").length, 3, "S005 has EE3110 only with grade W");
  assert.ok(!golden.some(r => r.student_id === "S001"), "S001 completed both prerequisites");
});
