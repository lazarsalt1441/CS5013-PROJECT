// Grade-card parsing. Uses only synthetic cards (tests/fixtures); real grade
// cards must never be committed.
import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";

const require = createRequire(import.meta.url);
const g = require("../api/_gradecard.js");

const fixture = name => fs.readFileSync(new URL(`./fixtures/${name}`, import.meta.url)).toString("base64");
const row = (...cells) => cells;

test("U, W and I do not count as completed; every other grade does", () => {
  assert.deepEqual(g.NOT_COMPLETED_GRADES, ["U", "W", "I"]);
  for (const grade of ["S", "A", "B", "C", "D", "E", "P"]) assert.equal(g.isCompleted(grade), true, grade);
  for (const grade of ["U", "W", "I", "u"]) assert.equal(g.isCompleted(grade), false, grade);
});

test("course rows are read by column, with multi-cell titles joined", () => {
  const result = g.parseGradeCardRows([
    row("Term No", "Course", "Title", "Credit", "Grade", "Attendance", "Year Of"),
    row("01", "CY1001", "Chemistry I:", "Structure", "10", "S", "VG", "2023"),
    row("5", "EE2703", "Applied Programming Lab", "6", "S", "VG", "2024")
  ]);
  assert.equal(result.attempts.length, 2);
  assert.deepEqual(result.attempts[0], { term: "01", course_id: "CY1001", title: "Chemistry I: Structure", credits: 10, grade: "S", year: 2023 });
  assert.equal(result.attempts[1].term, "05", "terms are zero-padded so they sort correctly");
});

test("headers, footers and totals are ignored", () => {
  const result = g.parseGradeCardRows([
    row("INDIAN INSTITUTE OF TECHNOLOGY MADRAS"),
    row("Earned Credits : 384"),
    row("Page 1 of 3")
  ]);
  assert.deepEqual(result.attempts, []);
  assert.deepEqual(result.unreadable_rows, []);
});

test("a course row with a damaged column is reported, not guessed", () => {
  const result = g.parseGradeCardRows([row("02", "EE1101", "Signals and Systems", "ten", "A", "VG", "2024")]);
  assert.equal(result.attempts.length, 0);
  assert.equal(result.unreadable_rows.length, 1);
});

test("a retaken course counts once, from the latest passing attempt", () => {
  const result = g.parseGradeCardRows([
    row("02", "EE1101", "Signals", "10", "U", "G", "2024"),
    row("03", "EE1101", "Signals", "10", "C", "G", "2024"),
    row("04", "EE2001", "Digital", "16", "W", "G", "2025")
  ]);
  assert.deepEqual(result.completed, [{ course_id: "EE1101", grade: "C", term: "03" }]);
});

test("the roll number is read from the header", () => {
  assert.equal(g.parseGradeCardRows([row("Roll No: xx00z999", "Name: TEST")]).roll_number, "XX00Z999");
  assert.equal(g.parseGradeCardRows([row("no roll here")]).roll_number, null);
});

test("text items on the same baseline become one row, left to right", () => {
  const item = (str, x, y) => ({ str, transform: [1, 0, 0, 1, x, y] });
  const rows = g.itemsToRows([item("B", 50, 100), item(" ", 40, 100), item("A", 10, 100.8), item("C", 10, 90)]);
  assert.deepEqual(rows, [["A", "B"], ["C"]]);
});

test("the synthetic PDF grade card parses end to end", async () => {
  const card = await g.parseGradeCard(fixture("synthetic_grade_card.pdf"));
  assert.equal(card.roll_number, "XX00Z999");
  assert.equal(card.attempts.length, 10);
  assert.deepEqual(card.completed.map(item => item.course_id),
    ["EE1100", "EE1101", "EE2004", "EE2015", "EE3110", "GN1101", "MA1101"]);
  assert.ok(!card.completed.some(item => ["EE2001", "MA1102"].includes(item.course_id)), "W and I are excluded");
});

test("uploads that are not usable PDFs fail with a clear message", async () => {
  assert.throws(() => g.decodeUpload(""), /empty/);
  assert.throws(() => g.decodeUpload("not base64!"), /base64/);
  assert.throws(() => g.decodeUpload(Buffer.from("hello").toString("base64")), /must be a PDF/);
  assert.throws(() => g.decodeUpload(Buffer.alloc(g.MAX_PDF_BYTES + 1, 0x25).toString("base64")), /3 MB/);
  assert.doesNotThrow(() => g.decodeUpload(`data:application/pdf;base64,${fixture("synthetic_grade_card.pdf")}`));
  await assert.rejects(g.parseGradeCard(fixture("no_text_layer.pdf")), /No course rows/);
  await assert.rejects(g.parseGradeCard(Buffer.from("%PDF-1.4 garbage").toString("base64")), /could not be read/);
});
