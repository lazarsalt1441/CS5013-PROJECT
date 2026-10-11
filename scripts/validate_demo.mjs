import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const directory = path.join(repo, "data", "demo-showcase");

function parseCsv(filename) {
  const text = fs.readFileSync(path.join(directory, filename), "utf8");
  const records = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (quoted) {
      if (character === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (character === '"') quoted = false;
      else field += character;
    } else if (character === '"') quoted = true;
    else if (character === ",") {
      row.push(field);
      field = "";
    } else if (character === "\n") {
      row.push(field.replace(/\r$/, ""));
      if (row.some(value => value !== "")) records.push(row);
      row = [];
      field = "";
    } else field += character;
  }
  if (quoted) throw new Error(`${filename}: unclosed quoted field`);
  if (field || row.length) {
    row.push(field.replace(/\r$/, ""));
    records.push(row);
  }
  const [header, ...values] = records;
  if (!header) throw new Error(`${filename}: file is empty`);
  for (const [index, value] of values.entries()) {
    if (value.length !== header.length) throw new Error(`${filename}:${index + 2}: expected ${header.length} columns, found ${value.length}`);
  }
  return values.map(value => Object.fromEntries(header.map((column, index) => [column, value[index]])));
}

function requireCondition(condition, message) {
  if (!condition) throw new Error(message);
}

const courses = parseCsv("courses.csv");
const slots = parseCsv("slots.csv");
const conflicts = parseCsv("hard_conflicts.csv");
const instructors = parseCsv("instructor_preferences.csv");
const students = parseCsv("student_preferences.csv");

const courseIds = new Set(courses.map(row => row.course_id));
const slotIds = new Set(slots.map(row => row.slot_id));
const lectureSlots = new Set(slots.filter(row => row.category === "LECTURE").map(row => row.slot_id));
requireCondition(courseIds.size === courses.length, "courses.csv contains duplicate course IDs");
requireCondition(slotIds.size === slots.length, "slots.csv contains duplicate slot IDs");
requireCondition(courses.length === 11, `expected 11 courses, found ${courses.length}`);
requireCondition([...courseIds].filter(id => courses.find(row => row.course_id === id).course_type === "ELECTIVE").length === 8,
  "expected eight elective courses");

for (const course of courses) {
  const allowed = course.allowed_slots.split("|");
  requireCondition(allowed.length > 0 && allowed.every(slot => lectureSlots.has(slot)), `${course.course_id}: invalid allowed slot`);
  requireCondition(!course.fixed_slot || allowed.includes(course.fixed_slot), `${course.course_id}: fixed slot is not allowed`);
  requireCondition(!course.additional_slot || slotIds.has(course.additional_slot), `${course.course_id}: unknown laboratory slot`);
}

for (const conflict of conflicts) {
  requireCondition(courseIds.has(conflict.course_1) && courseIds.has(conflict.course_2), "hard conflict references an unknown course");
  requireCondition(conflict.course_1 !== conflict.course_2, "hard conflict references the same course twice");
}
requireCondition(conflicts.length === 6, `expected six protected course pairs, found ${conflicts.length}`);

const instructorSlots = new Set();
const knownInstructors = new Set(courses.map(row => row.instructor));
for (const preference of instructors) {
  requireCondition(knownInstructors.has(preference.instructor), `${preference.instructor}: preference does not match a course instructor`);
  requireCondition(lectureSlots.has(preference.slot), `${preference.instructor}: unknown lecture slot ${preference.slot}`);
  requireCondition(Number.isInteger(Number(preference.penalty)) && Number(preference.penalty) >= 0, "instructor penalty must be non-negative");
  const key = `${preference.instructor}\u0000${preference.slot}`;
  requireCondition(!instructorSlots.has(key), `${preference.instructor}: duplicate preference for ${preference.slot}`);
  instructorSlots.add(key);
}

const byStudent = new Map();
for (const preference of students) {
  requireCondition(/^DEMO\d{3}$/.test(preference.student_id), `${preference.student_id}: use only anonymous DEMO identifiers`);
  requireCondition(courseIds.has(preference.course_id), `${preference.student_id}: unknown course ${preference.course_id}`);
  byStudent.set(preference.student_id, [...(byStudent.get(preference.student_id) || []), preference]);
}
requireCondition(byStudent.size === 36, `expected 36 anonymous students, found ${byStudent.size}`);
requireCondition(students.length === 144, `expected 144 preference rows, found ${students.length}`);
for (const [studentId, preferences] of byStudent) {
  const ranks = preferences.map(row => Number(row.rank)).sort((a, b) => a - b);
  const selectedCourses = new Set(preferences.map(row => row.course_id));
  requireCondition(JSON.stringify(ranks) === JSON.stringify([1, 2, 3, 4]), `${studentId}: ranks must be exactly 1-4`);
  requireCondition(selectedCourses.size === 4, `${studentId}: courses must be unique`);
}

const electives = courses.filter(row => row.course_type === "ELECTIVE").map(row => row.course_id).sort();
const coveredPairs = new Set();
for (const preferences of byStudent.values()) {
  const selected = preferences.map(row => row.course_id).filter(id => electives.includes(id)).sort();
  for (let first = 0; first < selected.length; first += 1) {
    for (let second = first + 1; second < selected.length; second += 1) {
      coveredPairs.add(`${selected[first]}\u0000${selected[second]}`);
    }
  }
}
const expectedPairs = electives.length * (electives.length - 1) / 2;
requireCondition(coveredPairs.size === expectedPairs,
  `expected complete elective co-demand coverage (${expectedPairs} pairs), found ${coveredPairs.size}`);

console.log(`Demo fixture valid: ${courses.length} courses, ${byStudent.size} students, ${students.length} preferences, ${conflicts.length} protected pairs.`);
