// Shared test fixtures. Each test gets a fresh in-memory Postgres (pg-mem)
// with every migration applied, so tests never touch the network or share
// state.
import { createRequire } from "node:module";
import { newDb } from "pg-mem";

const require = createRequire(import.meta.url);
export const db = require("../api/_db.js");
const { migrate } = require("../db/migrate.js");

export async function freshDatabase() {
  const memory = newDb();
  const { Pool } = memory.adapters.createPg();
  const pool = new Pool();
  await migrate(pool);
  db.setPool(pool);
  return pool;
}

export function responseRecorder() {
  return {
    statusCode: 200,
    headers: {},
    body: "",
    setHeader(key, value) { this.headers[key.toLowerCase()] = value; },
    getHeader(key) { return this.headers[key.toLowerCase()]; },
    end(value = "") { this.body = value; }
  };
}

export function request(method, body = {}, headers = {}) {
  return { method, body, headers };
}

export function parsed(response) {
  return JSON.parse(response.body);
}

export const ADMIN = { "x-admin-password": "test-admin-password" };
process.env.ADMIN_PASSWORD = "test-admin-password";

export function student(overrides = {}) {
  return {
    student_id: "S001",
    programme: "B.Tech EE",
    year: "3",
    preferences: [{ course_id: "EE5141" }, { course_id: "EE5112" }],
    ...overrides
  };
}

export function instructor(overrides = {}) {
  return {
    instructor: "Prof A",
    course_id: "EE5141",
    preferences: [{ slot: "A", penalty: 0, reason: "Preferred" }, { slot: "B", penalty: 8, reason: "Meeting, weekly" }],
    ...overrides
  };
}
