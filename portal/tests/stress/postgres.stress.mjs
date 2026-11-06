// Concurrency and load against a real Postgres, through the real HTTP
// handlers. Skipped without TEST_DATABASE_URL. Drops and recreates the public
// schema: never point it at a database you care about.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import pg from "pg";
import { SEED, courseIds, rng } from "./random.mjs";

const require = createRequire(import.meta.url);
const db = require("../../api/_db.js");
const { migrate } = require("../../db/migrate.js");
const submit = require("../../api/submit.js");
const admin = require("../../api/admin.js");
const results = require("../../api/results.js");

const url = process.env.TEST_DATABASE_URL;
const skip = url ? false : "TEST_DATABASE_URL is not set";
// Below Postgres's default max_connections (100) with room for other clients.
const POOL_SIZE = 20;
process.env.ADMIN_PASSWORD = "stress-admin";
const ADMIN = { "x-admin-password": "stress-admin" };

async function freshPool() {
  const pool = new pg.Pool({ connectionString: url, max: POOL_SIZE });
  await pool.query("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
  await migrate(pool);
  db.setPool(pool);
  return pool;
}

async function call(handler, method, body = {}, headers = {}) {
  const res = {
    statusCode: 200, headers: {}, body: "",
    setHeader(k, v) { this.headers[k.toLowerCase()] = v; },
    getHeader(k) { return this.headers[k.toLowerCase()]; },
    end(v = "") { this.body = v; }
  };
  await handler({ method, body, headers }, res);
  return { status: res.statusCode, body: JSON.parse(res.body) };
}

const student = (student_id, courses) => ({
  type: "student",
  payload: { student_id, programme: "B.Tech EE", year: "3", preferences: courses.map(course_id => ({ course_id })) }
});
const instructor = (name, course_id, prerequisites) => ({
  type: "instructor",
  payload: { instructor: name, course_id, prerequisites, preferences: [{ slot: "A", penalty: 0, reason: "" }] }
});

console.log(`# stress seed ${SEED}`);

test("300 different students submitting at once are all stored exactly", { skip }, async () => {
  const pool = await freshPool();
  try {
    const r = rng(SEED);
    const ids = courseIds(40);
    const submissions = Array.from({ length: 300 }, (_, i) => [`S${String(i).padStart(4, "0")}`, r.sample(ids, r.int(1, 10))]);
    const responses = await Promise.all(submissions.map(([sid, courses]) => call(submit, "POST", student(sid, courses))));
    assert.deepEqual([...new Set(responses.map(res => res.status))], [200], "every submission succeeds");
    const { body } = await call(admin, "POST", { action: "snapshot" }, ADMIN);
    assert.equal(body.student_count, 300);
    assert.equal(body.student_rows, submissions.reduce((n, [, courses]) => n + courses.length, 0));
    const stored = new Map();
    body.students.forEach(row => stored.set(row.student_id, [...(stored.get(row.student_id) || []), row.course_id]));
    for (const [sid, courses] of submissions) assert.deepEqual(stored.get(sid), courses, sid);
  } finally {
    await pool.end();
  }
});

test("60 simultaneous resubmissions by one student all succeed and leave exactly one complete list", { skip }, async () => {
  const pool = await freshPool();
  try {
    const r = rng(SEED + 1);
    const ids = courseIds(30);
    const lists = Array.from({ length: 60 }, () => r.sample(ids, r.int(1, 10)));
    const responses = await Promise.all(lists.map(courses => call(submit, "POST", student("S0001", courses))));
    assert.deepEqual([...new Set(responses.map(res => res.status))], [200], "no lost or failed write");
    const { rows } = await pool.query("SELECT course_id, rank FROM student_preferences WHERE student_id = 'S0001' ORDER BY rank");
    assert.deepEqual(rows.map(row => row.rank), rows.map((_, k) => k + 1), "ranks 1..n, nothing interleaved");
    const stored = JSON.stringify(rows.map(row => row.course_id));
    assert.ok(lists.some(list => JSON.stringify(list) === stored), `stored list ${stored} matches no submission`);
  } finally {
    await pool.end();
  }
});

test("professors replacing the same course's prerequisites at once: all succeed, last writer wins", { skip }, async () => {
  const pool = await freshPool();
  try {
    const r = rng(SEED + 2);
    const ids = courseIds(20).filter(id => id !== "EE1000");
    // Overlapping lists make the race visible: the same (course, prereq) key
    // is inserted by several transactions at once.
    const lists = Array.from({ length: 25 }, () => r.sample(ids.slice(0, 6), r.int(1, 4)));
    const responses = await Promise.all(lists.map((list, i) => call(submit, "POST", instructor(`Prof ${i}`, "EE1000", list.join(",")))));
    const statuses = responses.map(res => res.status);
    assert.deepEqual([...new Set(statuses)], [200], `statuses ${JSON.stringify(statuses)}`);
    const { rows } = await pool.query("SELECT prereq_course_id FROM prerequisites WHERE course_id = 'EE1000' ORDER BY 1");
    const stored = JSON.stringify(rows.map(row => row.prereq_course_id));
    assert.ok(lists.some(list => JSON.stringify([...list].sort()) === stored),
      `stored ${stored} is not any single professor's list (a merge of concurrent writes)`);
  } finally {
    await pool.end();
  }
});

test("400 mixed valid and invalid requests: only 200s and 400s, never a 500", { skip }, async () => {
  const pool = await freshPool();
  try {
    const r = rng(SEED + 3);
    const ids = courseIds(15);
    const requests = Array.from({ length: 400 }, (_, i) => {
      switch (r.int(0, 4)) {
        case 0: return student(`S${r.int(0, 50)}`, r.sample(ids, r.int(1, 10)));
        case 1: return student(r.pick(["!", "", "ab", "x".repeat(40)]), ["EE1001"]);
        case 2: return student(`S${r.int(0, 50)}`, ["EE1001", "EE1001"]);
        case 3: return instructor(`Prof ${r.int(0, 10)}`, r.pick(ids), r.sample(ids, r.int(0, 3)).join(" "));
        default: return { type: "instructor", payload: { instructor: "P", preferences: [{ slot: "A", penalty: 101 }] }, i };
      }
    });
    const responses = await Promise.all(requests.map(body => call(submit, "POST", body)));
    const counts = {};
    responses.forEach(res => counts[res.status] = (counts[res.status] || 0) + 1);
    console.log(`# mixed workload statuses ${JSON.stringify(counts)}`);
    assert.deepEqual(Object.keys(counts).sort(), ["200", "400"], JSON.stringify(counts));
  } finally {
    await pool.end();
  }
});

test("readers during heavy writes always see whole submissions and whole published runs", { skip }, async () => {
  const pool = await freshPool();
  try {
    const r = rng(SEED + 4);
    const ids = courseIds(25);
    const lists = new Map();
    const writes = Array.from({ length: 150 }, () => {
      const sid = `S${r.int(0, 20)}`;
      const courses = r.sample(ids, r.int(1, 10));
      lists.set(sid, [...(lists.get(sid) || []), JSON.stringify(courses)]);
      return call(submit, "POST", student(sid, courses));
    });
    const publishes = Array.from({ length: 10 }, (_, run) => call(admin, "POST", {
      action: "publish_results",
      payload: { solver: `run-${run}`, slotbook: ids.slice(0, 12).map(course_id => ({ course_id, assigned_slot: "A" })), explanations: [] }
    }, ADMIN));
    const snapshots = Array.from({ length: 40 }, () => call(admin, "POST", { action: "snapshot" }, ADMIN));
    const reads = Array.from({ length: 40 }, () => call(results, "GET"));
    const [, , snaps, published] = await Promise.all([Promise.all(writes), Promise.all(publishes), Promise.all(snapshots), Promise.all(reads)]);
    for (const { status, body } of snaps) {
      assert.equal(status, 200);
      const seen = new Map();
      body.students.forEach(row => seen.set(row.student_id, [...(seen.get(row.student_id) || []), row.course_id]));
      for (const [sid, courses] of seen) {
        assert.ok(lists.get(sid).includes(JSON.stringify(courses)), `${sid} seen half-written: ${courses}`);
      }
    }
    for (const { status, body } of published) {
      assert.equal(status, 200);
      assert.ok(body.slotbook.length === 0 || body.slotbook.length === 12, `partial run visible: ${body.slotbook.length} rows`);
    }
  } finally {
    await pool.end();
  }
});
