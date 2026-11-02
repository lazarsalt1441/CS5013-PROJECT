// Unit tests for api/_store.js and db/migrate.js against in-memory Postgres.
// Transaction rollback is not covered here because pg-mem does not
// implement it; tests/integration/postgres.test.mjs covers that on real
// Postgres.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { freshDatabase, instructor, student } from "./helpers.mjs";

const require = createRequire(import.meta.url);
const store = require("../api/_store.js");
const { migrate, migrationFiles } = require("../db/migrate.js");
const { validateInstructor, validatePublication, validateStudent } = require("../api/_validation.js");

test("migrations apply in order and are recorded once", async () => {
  const pool = await freshDatabase();
  const versions = (await pool.query("SELECT version FROM schema_migrations ORDER BY version")).rows.map(row => row.version);
  assert.deepEqual(versions, migrationFiles());
  assert.deepEqual(await migrate(pool), [], "second run applies nothing");
});

test("a student resubmission replaces earlier preferences", async () => {
  await freshDatabase();
  await store.saveStudent(validateStudent(student()));
  await store.saveStudent(validateStudent(student({ preferences: [{ course_id: "EE5150" }] })));
  const snap = await store.snapshot();
  assert.deepEqual(snap.students, [{ student_id: "S001", course_id: "EE5150", rank: 1 }]);
  assert.equal(snap.student_count, 1);
});

test("completions are replaced only when a new list is supplied", async () => {
  await freshDatabase();
  const s = validateStudent(student());
  await store.saveStudent(s, { completions: [{ course_id: "EE1101", grade: "A", term: "02" }] });
  await store.saveStudent(s);
  assert.deepEqual((await store.listCompletions("S001")).map(row => row.course_id), ["EE1101"], "kept when null");
  await store.saveStudent(s, { completions: [{ course_id: "EE2015", grade: "B", term: "05" }] });
  const rows = await store.listCompletions("S001");
  assert.deepEqual(rows.map(row => [row.course_id, row.grade, row.source]), [["EE2015", "B", "grade_card"]]);
});

test("instructor names are matched case-insensitively", async () => {
  await freshDatabase();
  await store.saveInstructor(validateInstructor(instructor()));
  await store.saveInstructor(validateInstructor(instructor({ instructor: "PROF A", preferences: [{ slot: "C", penalty: 3, reason: "" }] })));
  const snap = await store.snapshot();
  assert.equal(snap.instructor_count, 1);
  assert.deepEqual(snap.instructors, [{ instructor: "PROF A", slot: "C", penalty: 3, reason: "" }]);
});

test("prerequisites are replaced per course and left alone when absent", async () => {
  await freshDatabase();
  await store.saveInstructor(validateInstructor(instructor({ prerequisites: "EE1101 EE2015" })));
  await store.saveInstructor(validateInstructor(instructor()));
  assert.equal((await store.listPrerequisites()).length, 2, "null leaves the list unchanged");
  await store.saveInstructor(validateInstructor(instructor({ prerequisites: "EE3110" })));
  assert.deepEqual(await store.listPrerequisites(), [{ course_id: "EE5141", prereq_course_id: "EE3110", declared_by: "Prof A" }]);
  await store.saveInstructor(validateInstructor(instructor({ prerequisites: "" })));
  assert.deepEqual(await store.listPrerequisites(), [], "empty clears");
});

test("database constraints reject rows the validator would also reject", async () => {
  const pool = await freshDatabase();
  await assert.rejects(pool.query("INSERT INTO student_preferences VALUES ('NOBODY', 'EE1101', 1)"), /foreign key|violates/i);
  await assert.rejects(pool.query("INSERT INTO prerequisites (course_id, prereq_course_id, declared_by) VALUES ('EE1', 'EE1', 'p')"), /check/i);
  await assert.rejects(pool.query("INSERT INTO completed_courses (student_id, course_id, grade, source) VALUES ('S', 'EE1', 'A', 'rumour')"), /check/i);
});

test("public results show only the latest published run", async () => {
  await freshDatabase();
  assert.deepEqual(await store.publicResults(), { slotbook: [], explanations: [], published_at: null, solver: null });
  await store.publishResults(validatePublication({ solver: "cpsat", slotbook: [{ course_id: "EE1", assigned_slot: "A" }] }));
  await store.publishResults(validatePublication({
    solver: "unitime",
    slotbook: [{ course_id: "EE2", assigned_slot: "B", class_size: 30 }],
    explanations: [{ course_id: "EE2", assigned_slot: "B", reasons: "r", alternatives: "a" }]
  }));
  const result = await store.publicResults();
  assert.equal(result.solver, "unitime");
  assert.deepEqual(result.slotbook.map(row => row.course_id), ["EE2"]);
  assert.equal(result.slotbook[0].class_size, 30);
  assert.equal(result.explanations[0].rejected_or_feasible_alternatives, "a");
});

test("clearAll empties every table", async () => {
  const pool = await freshDatabase();
  await store.saveStudent(validateStudent(student()), { completions: [{ course_id: "EE1101", grade: "A" }] });
  await store.saveInstructor(validateInstructor(instructor({ prerequisites: "EE1101" })));
  await store.publishResults(validatePublication({ slotbook: [{ course_id: "EE1" }] }));
  await store.clearAll();
  for (const table of ["student_submissions", "student_preferences", "instructor_submissions", "instructor_preferences",
    "prerequisites", "completed_courses", "published_runs", "slotbook_rows", "explanation_rows"]) {
    const { rows } = await pool.query(`SELECT count(*)::int AS n FROM ${table}`);
    assert.equal(rows[0].n, 0, table);
  }
});
