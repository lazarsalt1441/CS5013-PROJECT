// Unit tests for api/_validation.js: pure functions, no database.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { instructor, student } from "./helpers.mjs";

const require = createRequire(import.meta.url);
const v = require("../api/_validation.js");

test("student: IDs are upper-cased and ranks follow list order", () => {
  const result = v.validateStudent(student({ student_id: " s001 ", preferences: [{ course_id: "ee5112" }, { course_id: "EE5141" }] }));
  assert.equal(result.student_id, "S001");
  assert.deepEqual(result.preferences, [{ course_id: "EE5112", rank: 1 }, { course_id: "EE5141", rank: 2 }]);
});

test("student: rejects malformed IDs, missing fields and bad list sizes", () => {
  assert.throws(() => v.validateStudent(student({ student_id: "a!" })), /Student ID/);
  assert.throws(() => v.validateStudent(student({ programme: "" })), /Programme and year/);
  assert.throws(() => v.validateStudent(student({ preferences: [] })), /between 1 and 10/);
  const eleven = Array.from({ length: 11 }, (_, i) => ({ course_id: `EE${1000 + i}` }));
  assert.throws(() => v.validateStudent(student({ preferences: eleven })), /between 1 and 10/);
});

test("student: rejects duplicate and invalid course IDs", () => {
  assert.throws(() => v.validateStudent(student({ preferences: [{ course_id: "EE5141" }, { course_id: "ee5141" }] })), /only once/);
  assert.throws(() => v.validateStudent(student({ preferences: [{ course_id: "5141" }] })), /Invalid course ID/);
});

test("validation errors carry HTTP status 400", () => {
  try {
    v.validateStudent({});
    assert.fail("expected a ValidationError");
  } catch (error) {
    assert.ok(error instanceof v.ValidationError);
    assert.equal(error.status, 400);
  }
});

test("clean strips control characters and truncates", () => {
  assert.equal(v.clean("  a\u0000b\nc  ", 10), "a b c");
  assert.equal(v.clean("abcdef", 3), "abc");
  assert.equal(v.clean(null, 5), "");
});

test("instructor: key is case-insensitive and penalties must be 0-100 integers", () => {
  const result = v.validateInstructor(instructor({ instructor: "Prof ABC" }));
  assert.equal(result.instructor_key, "prof abc");
  assert.throws(() => v.validateInstructor(instructor({ preferences: [{ slot: "A", penalty: 101 }] })), /0 to 100/);
  assert.throws(() => v.validateInstructor(instructor({ preferences: [{ slot: "A", penalty: 1.5 }] })), /0 to 100/);
  assert.throws(() => v.validateInstructor(instructor({ preferences: [{ slot: "a!", penalty: 1 }] })), /Invalid slot/);
  assert.throws(() => v.validateInstructor(instructor({ preferences: [{ slot: "A", penalty: 1 }, { slot: "a", penalty: 2 }] })), /only once/);
});

test("instructor: prerequisites are parsed from free text and de-duplicated", () => {
  const result = v.validateInstructor(instructor({ prerequisites: "ee1101, EE2015;EE1101  EE3110" }));
  assert.deepEqual(result.prerequisites, ["EE1101", "EE2015", "EE3110"]);
});

test("instructor: absent prerequisites mean unchanged, empty means none", () => {
  assert.equal(v.validateInstructor(instructor()).prerequisites, null);
  assert.deepEqual(v.validateInstructor(instructor({ prerequisites: "" })).prerequisites, []);
  assert.deepEqual(v.validateInstructor(instructor({ prerequisites: [] })).prerequisites, []);
});

test("instructor: prerequisites need a course and cannot include the course itself", () => {
  assert.throws(() => v.validateInstructor(instructor({ course_id: "", prerequisites: "EE1101" })), /course these prerequisites/);
  assert.throws(() => v.validateInstructor(instructor({ prerequisites: "EE5141" })), /own prerequisite/);
  assert.throws(() => v.validateInstructor(instructor({ prerequisites: "nonsense!" })), /Invalid course ID/);
});

test("publication: requires slotbook rows with unique course IDs", () => {
  assert.throws(() => v.validatePublication({ slotbook: [] }), /required/);
  assert.throws(() => v.validatePublication({ slotbook: [{ course_id: "EE1" }, { course_id: "EE1" }] }), /twice/);
  assert.throws(() => v.validatePublication({ slotbook: [{ title: "x" }] }), /needs a course_id/);
  const ok = v.validatePublication({ slotbook: [{ course_id: "EE1", class_size: "42" }], explanations: [{ course_id: "EE1", explanation: "why" }] });
  assert.equal(ok.solver, "manual");
  assert.equal(ok.slotbook[0].class_size, 42);
  assert.equal(ok.explanations[0].reasons, "why");
});
