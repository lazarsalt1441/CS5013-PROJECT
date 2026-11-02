// HTTP contract tests: each Vercel handler is called with a fake request and
// response, backed by a fresh in-memory database.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { ADMIN, db, freshDatabase, instructor, parsed, request, responseRecorder, student } from "./helpers.mjs";

const require = createRequire(import.meta.url);
const lib = require("../api/_lib.js");
const submit = require("../api/submit.js");
const admin = require("../api/admin.js");
const results = require("../api/results.js");
const solver = require("../api/solver.js");
const health = require("../api/health.js");

async function call(handler, req) {
  const res = responseRecorder();
  await handler(req, res);
  return { status: res.statusCode, body: parsed(res), headers: res.headers };
}

test("CSV export preserves the Java parser contract and quotes unsafe text", () => {
  const csv = lib.toCsv(
    ["instructor", "slot", "penalty", "reason"],
    [{ instructor: "Prof A", slot: "B", penalty: 8, reason: "Meeting, weekly" }]
  );
  assert.equal(csv, 'instructor,slot,penalty,reason\nProf A,B,8,"Meeting, weekly"\n');
});

test("student submission is validated and stored", async () => {
  await freshDatabase();
  const { status, body } = await call(submit, request("POST", { type: "student", payload: student() }));
  assert.equal(status, 200);
  assert.equal(body.rows_saved, 2);
  assert.match(body.submitted_at, /^\d{4}-\d{2}-\d{2}T/);
});

test("invalid submissions return 400 with the validation message", async () => {
  await freshDatabase();
  const { status, body } = await call(submit, request("POST", { type: "student", payload: student({ student_id: "!" }) }));
  assert.equal(status, 400);
  assert.match(body.error, /Student ID/);
});

test("submission endpoint rejects an unknown preference type and wrong method", async () => {
  assert.equal((await call(submit, request("POST", { type: "observer", payload: {} }))).status, 400);
  const wrong = await call(submit, request("GET"));
  assert.equal(wrong.status, 405);
  assert.equal(wrong.headers.allow, "POST");
});

test("database faults return a generic 500 without internal details", async () => {
  db.setPool({
    async connect() { throw new Error("password authentication failed for user neondb_owner"); },
    async query() { throw new Error("unused"); }
  });
  const originalError = console.error;
  console.error = () => {};
  try {
    const { status, body } = await call(submit, request("POST", { type: "student", payload: student() }));
    assert.equal(status, 500);
    assert.equal(body.error, "Unexpected server error.");
  } finally {
    console.error = originalError;
  }
});

test("missing DATABASE_URL is reported as 503", async () => {
  db.setPool(null);
  const saved = process.env.DATABASE_URL;
  delete process.env.DATABASE_URL;
  try {
    const { status, body } = await call(results, request("GET"));
    assert.equal(status, 503);
    assert.match(body.error, /DATABASE_URL/);
  } finally {
    if (saved !== undefined) process.env.DATABASE_URL = saved;
  }
});

test("admin endpoint rejects a missing or wrong password before touching the database", async () => {
  db.setPool({ async query() { throw new Error("must not be called"); }, async connect() { throw new Error("must not be called"); } });
  assert.equal((await call(admin, request("POST", { action: "snapshot" }))).status, 401);
  assert.equal((await call(admin, request("POST", { action: "snapshot" }, { "x-admin-password": "test-admin-passwor" }))).status, 401);
});

test("admin snapshot includes exact student and instructor CSV outputs", async () => {
  await freshDatabase();
  await call(submit, request("POST", { type: "student", payload: student({ preferences: [{ course_id: "EE5141" }] }) }));
  await call(submit, request("POST", { type: "instructor", payload: instructor({ preferences: [{ slot: "A", penalty: 0, reason: "Preferred" }] }) }));
  const { status, body } = await call(admin, request("POST", { action: "snapshot" }, ADMIN));
  assert.equal(status, 200);
  assert.equal(body.student_preferences_csv, "student_id,course_id,rank\nS001,EE5141,1\n");
  assert.equal(body.instructor_preferences_csv, "instructor,slot,penalty,reason\nProf A,A,0,Preferred\n");
});

test("admin rejects unknown actions, including inherited object keys", async () => {
  await freshDatabase();
  assert.equal((await call(admin, request("POST", { action: "drop_tables" }, ADMIN))).status, 400);
  assert.equal((await call(admin, request("POST", { action: "constructor" }, ADMIN))).status, 400);
});

test("published results are served publicly with a short cache", async () => {
  await freshDatabase();
  const publish = await call(admin, request("POST", {
    action: "publish_results",
    payload: { solver: "unitime", slotbook: [{ course_id: "EE5141", assigned_slot: "F" }], explanations: [] }
  }, ADMIN));
  assert.equal(publish.status, 200);
  const { status, body, headers } = await call(results, request("GET"));
  assert.equal(status, 200);
  assert.equal(body.slotbook[0].assigned_slot, "F");
  assert.match(headers["cache-control"], /max-age=30/);
});

test("clear_demo_data removes submissions", async () => {
  await freshDatabase();
  await call(submit, request("POST", { type: "student", payload: student() }));
  await call(admin, request("POST", { action: "clear_demo_data" }, ADMIN));
  const { body } = await call(admin, request("POST", { action: "snapshot" }, ADMIN));
  assert.equal(body.student_rows, 0);
});

test("solver endpoint explains the manual fallback when no backend URL exists", async () => {
  delete process.env.SOLVER_API_URL;
  const { status, body } = await call(solver, request("POST", { solver: "unitime" }, ADMIN));
  assert.equal(status, 503);
  assert.equal(body.code, "SOLVER_NOT_CONFIGURED");
});

test("solver endpoint sends the exports and publishes the returned slotbook", async () => {
  await freshDatabase();
  await call(submit, request("POST", { type: "student", payload: student() }));
  process.env.SOLVER_API_URL = "https://solver.example.invalid/run";
  let sent;
  const originalFetch = global.fetch;
  global.fetch = async (_url, options) => {
    sent = JSON.parse(options.body);
    return { ok: true, status: 200, async json() { return { slotbook: [{ course_id: "EE5141", assigned_slot: "F" }], explanations: [] }; } };
  };
  try {
    const { status } = await call(solver, request("POST", { solver: "cpsat" }, ADMIN));
    assert.equal(status, 200);
    assert.equal(sent.solver, "cpsat");
    assert.match(sent.student_preferences_csv, /^student_id,course_id,rank\nS001,EE5141,1\n/);
    assert.equal((await call(results, request("GET"))).body.solver, "cpsat");
  } finally {
    global.fetch = originalFetch;
    delete process.env.SOLVER_API_URL;
  }
});

test("solver failures are reported as 502 with the solver's message", async () => {
  await freshDatabase();
  process.env.SOLVER_API_URL = "https://solver.example.invalid/run";
  const originalFetch = global.fetch;
  global.fetch = async () => ({ ok: false, status: 500, async json() { return { error: "UniTime timed out" }; } });
  try {
    const { status, body } = await call(solver, request("POST", {}, ADMIN));
    assert.equal(status, 502);
    assert.equal(body.error, "UniTime timed out");
  } finally {
    global.fetch = originalFetch;
    delete process.env.SOLVER_API_URL;
  }
});

test("health reports configuration without connecting to the database", async () => {
  db.setPool({ async query() { throw new Error("must not connect"); } });
  process.env.DATABASE_URL = "postgres://user:pass@example.invalid/db";
  try {
    const { status, body } = await call(health, request("GET"));
    assert.equal(status, 200);
    assert.equal(body.database_configured, true);
    assert.equal(body.admin_configured, true);
  } finally {
    delete process.env.DATABASE_URL;
  }
});

test("TLS is required for remote databases and skipped for localhost", () => {
  assert.equal(db.sslFor("postgres://u:p@localhost:5432/x"), false);
  assert.deepEqual(db.sslFor("postgres://u:p@ep-cool-name.ap-southeast-1.aws.neon.tech/neondb"), { rejectUnauthorized: true });
});
