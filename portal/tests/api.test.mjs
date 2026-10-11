import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";

process.env.GOOGLE_APPS_SCRIPT_URL = "https://example.invalid/apps-script";
process.env.ADMIN_PASSWORD = "test-admin-password";
process.env.GOOGLE_APPS_SCRIPT_ADMIN_KEY = "test-script-key";

const require = createRequire(import.meta.url);
const lib = require("../api/_lib.js");
const submit = require("../api/submit.js");
const admin = require("../api/admin.js");
const solver = require("../api/solver.js");

function responseRecorder() {
  return {
    statusCode: 200,
    headers: {},
    body: "",
    setHeader(key, value) { this.headers[key.toLowerCase()] = value; },
    end(value = "") { this.body = value; }
  };
}

function request(method, body = {}, headers = {}) {
  return { method, body, headers };
}

function parsed(response) {
  return JSON.parse(response.body);
}

function sheetsResponse(body) {
  return {
    ok: true,
    status: 200,
    async text() { return JSON.stringify(body); }
  };
}

test("CSV export preserves the Java parser contract and quotes unsafe text", () => {
  const csv = lib.toCsv(
    ["instructor", "slot", "penalty", "reason"],
    [{ instructor: "Prof A", slot: "B", penalty: 8, reason: "Meeting, weekly" }]
  );
  assert.equal(csv, 'instructor,slot,penalty,reason\nProf A,B,8,"Meeting, weekly"\n');
});

test("public student submission is forwarded to the student action", async () => {
  let forwarded;
  global.fetch = async (_url, options) => {
    forwarded = JSON.parse(options.body);
    return sheetsResponse({ ok: true, message: "saved", rows_saved: 2 });
  };
  const res = responseRecorder();
  await submit(request("POST", { type: "student", payload: { student_id: "S100", preferences: [] } }), res);
  assert.equal(res.statusCode, 200);
  assert.equal(forwarded.action, "submit_student");
  assert.equal(forwarded.payload.student_id, "S100");
  assert.equal(parsed(res).rows_saved, 2);
});

test("Apps Script ContentService redirects are retrieved with a GET", async () => {
  const calls = [];
  global.fetch = async (url, options) => {
    calls.push({ url: String(url), method: options.method });
    if (calls.length === 1) {
      return {
        status: 302,
        headers: { get(name) { return name === "location" ? "https://script.googleusercontent.com/macros/echo?token=test" : null; } }
      };
    }
    return sheetsResponse({ ok: true, message: "saved", rows_saved: 1 });
  };
  const res = responseRecorder();
  await submit(request("POST", { type: "student", payload: { student_id: "S101", preferences: [] } }), res);
  assert.equal(res.statusCode, 200);
  assert.deepEqual(calls.map(call => call.method), ["POST", "GET"]);
  assert.match(calls[1].url, /^https:\/\/script\.googleusercontent\.com\//);
});

test("submission endpoint rejects an unknown preference type", async () => {
  const res = responseRecorder();
  await submit(request("POST", { type: "observer", payload: {} }), res);
  assert.equal(res.statusCode, 400);
  assert.match(parsed(res).error, /student or instructor/);
});

test("admin endpoint rejects a missing password before touching Sheets", async () => {
  let called = false;
  global.fetch = async () => { called = true; return sheetsResponse({ ok: true }); };
  const res = responseRecorder();
  await admin(request("POST", { action: "snapshot" }), res);
  assert.equal(res.statusCode, 401);
  assert.equal(called, false);
});

test("admin snapshot includes exact student and instructor CSV outputs", async () => {
  global.fetch = async (_url, options) => {
    const forwarded = JSON.parse(options.body);
    assert.equal(forwarded.admin_key, "test-script-key");
    return sheetsResponse({
      ok: true,
      students: [{ student_id: "S001", course_id: "EE5141", rank: 1 }],
      instructors: [{ instructor: "Prof A", slot: "A", penalty: 0, reason: "Preferred" }]
    });
  };
  const res = responseRecorder();
  await admin(request("POST", { action: "snapshot" }, { "x-admin-password": "test-admin-password" }), res);
  assert.equal(res.statusCode, 200);
  const body = parsed(res);
  assert.equal(body.student_preferences_csv, "student_id,course_id,rank\nS001,EE5141,1\n");
  assert.equal(body.instructor_preferences_csv, "instructor,slot,penalty,reason\nProf A,A,0,Preferred\n");
});

test("solver endpoint explains the manual fallback when no backend URL exists", async () => {
  delete process.env.SOLVER_API_URL;
  const res = responseRecorder();
  await solver(request("POST", { solver: "unitime" }, { "x-admin-password": "test-admin-password" }), res);
  assert.equal(res.statusCode, 503);
  assert.equal(parsed(res).code, "SOLVER_NOT_CONFIGURED");
});
