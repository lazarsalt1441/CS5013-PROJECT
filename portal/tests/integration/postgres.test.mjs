// Integration tests against a real Postgres server.
//
// Run with: TEST_DATABASE_URL=postgres://... npm run test:integration
// CI provides a postgres:16 service container. Without the variable every
// test is skipped, so `npm test` stays network-free.
//
// These cover what pg-mem cannot: real transaction rollback, real
// constraint messages and concurrent writers.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import pg from "pg";
import { instructor, student } from "../helpers.mjs";

const require = createRequire(import.meta.url);
const db = require("../../api/_db.js");
const store = require("../../api/_store.js");
const { migrate } = require("../../db/migrate.js");
const { validateInstructor, validateStudent } = require("../../api/_validation.js");

const url = process.env.TEST_DATABASE_URL;
const skip = url ? false : "TEST_DATABASE_URL is not set";

async function resetSchema() {
  const pool = new pg.Pool({ connectionString: url });
  await pool.query("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
  await migrate(pool);
  db.setPool(pool);
  return pool;
}

test("migrations run on real Postgres and are idempotent", { skip }, async () => {
  const pool = await resetSchema();
  try {
    assert.deepEqual(await migrate(pool), []);
  } finally {
    await pool.end();
  }
});

test("a failing write rolls back the whole student submission", { skip }, async () => {
  const pool = await resetSchema();
  try {
    await store.saveStudent(validateStudent(student()));
    const bad = validateStudent(student({ preferences: [{ course_id: "EE5150" }] }));
    // Bypass the validator to force a CHECK violation on the second row.
    bad.preferences.push({ course_id: "EE5175", rank: 99 });
    await assert.rejects(store.saveStudent(bad), /check/i);
    const { rows } = await pool.query("SELECT course_id FROM student_preferences WHERE student_id = 'S001' ORDER BY rank");
    assert.deepEqual(rows.map(row => row.course_id), ["EE5141", "EE5112"], "earlier submission is intact");
  } finally {
    await pool.end();
  }
});

test("concurrent resubmissions leave exactly one complete preference list", { skip }, async () => {
  const pool = await resetSchema();
  try {
    const lists = [
      [{ course_id: "EE5141" }, { course_id: "EE5112" }],
      [{ course_id: "EE5150" }, { course_id: "EE5175" }, { course_id: "EE5178" }],
      [{ course_id: "EE3110" }]
    ];
    await Promise.allSettled(lists.map(preferences => store.saveStudent(validateStudent(student({ preferences })))));
    const { rows } = await pool.query("SELECT course_id FROM student_preferences WHERE student_id = 'S001' ORDER BY rank");
    const ids = rows.map(row => row.course_id);
    assert.ok(lists.some(list => JSON.stringify(list.map(item => item.course_id)) === JSON.stringify(ids)),
      `rows must equal one submitted list, got ${ids.join(",")}`);
  } finally {
    await pool.end();
  }
});

test("prerequisite replacement is atomic with the instructor submission", { skip }, async () => {
  const pool = await resetSchema();
  try {
    await store.saveInstructor(validateInstructor(instructor({ prerequisites: "EE1101" })));
    const bad = validateInstructor(instructor({ prerequisites: "EE2015" }));
    bad.preferences.push({ slot: "Z", penalty: 500, reason: "" });
    await assert.rejects(store.saveInstructor(bad), /check/i);
    assert.deepEqual((await store.listPrerequisites()).map(row => row.prereq_course_id), ["EE1101"]);
  } finally {
    await pool.end();
  }
});
