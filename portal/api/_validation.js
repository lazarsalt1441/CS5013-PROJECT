// Pure input validation for portal submissions.
//
// These rules were previously inside the Google Apps Script bridge (v2). They
// are kept identical so stored rows still satisfy the Java CSV contracts, and
// they live here, free of I/O, so they can be unit tested directly. The
// database repeats the critical ones as constraints (db/migrations).

class ValidationError extends Error {
  constructor(message) {
    super(message);
    this.name = "ValidationError";
    this.status = 400;
  }
}

const STUDENT_ID = /^[A-Z0-9_-]{3,30}$/;
const COURSE_ID = /^[A-Z]{2,5}[0-9A-Z-]{2,20}$/;
const SLOT_ID = /^[A-Z][A-Z0-9-]{0,10}$/;
const MAX_STUDENT_PREFERENCES = 10;
const MAX_INSTRUCTOR_PREFERENCES = 20;
const MAX_PREREQUISITES = 10;

/** Trims, replaces control characters with spaces and truncates. */
function clean(value, maxLength) {
  return String(value ?? "").trim().replace(/[\u0000-\u001f\u007f]/g, " ").slice(0, maxLength);
}

function courseId(value) {
  const id = clean(value, 30).toUpperCase();
  if (!COURSE_ID.test(id)) throw new ValidationError(`Invalid course ID: ${id || "(blank)"}`);
  return id;
}

function validateStudent(payload = {}) {
  const studentId = clean(payload.student_id, 30).toUpperCase();
  const programme = clean(payload.programme, 60);
  const year = clean(payload.year, 20);
  const preferences = Array.isArray(payload.preferences) ? payload.preferences : [];
  if (!STUDENT_ID.test(studentId)) {
    throw new ValidationError("Student ID must use 3-30 letters, digits, underscores, or hyphens.");
  }
  if (!programme || !year) throw new ValidationError("Programme and year are required.");
  if (preferences.length < 1 || preferences.length > MAX_STUDENT_PREFERENCES) {
    throw new ValidationError(`Choose between 1 and ${MAX_STUDENT_PREFERENCES} courses.`);
  }
  const seen = new Set();
  const ranked = preferences.map((item, index) => {
    const id = courseId(item && item.course_id);
    if (seen.has(id)) throw new ValidationError("A course may appear only once.");
    seen.add(id);
    return { course_id: id, rank: index + 1 };
  });
  return { student_id: studentId, programme, year, preferences: ranked };
}

function validateInstructor(payload = {}) {
  const instructor = clean(payload.instructor, 80);
  const rawCourse = clean(payload.course_id, 30);
  const preferences = Array.isArray(payload.preferences) ? payload.preferences : [];
  // Absent means "leave this course's prerequisites unchanged"; an empty list
  // means "this course has none" and clears earlier declarations.
  const prerequisitesGiven = payload.prerequisites !== undefined && payload.prerequisites !== null;
  const rawPrerequisites = prerequisitesGiven ? splitCourseList(payload.prerequisites) : [];
  if (instructor.length < 2) throw new ValidationError("Instructor name is required.");
  const course = rawCourse ? courseId(rawCourse) : "";
  if (preferences.length < 1 || preferences.length > MAX_INSTRUCTOR_PREFERENCES) {
    throw new ValidationError("Provide at least one slot preference.");
  }
  const seenSlots = new Set();
  const slots = preferences.map(item => {
    const slot = clean(item && item.slot, 12).toUpperCase();
    const penalty = Number(item && item.penalty);
    const reason = clean(item && item.reason, 240);
    if (!SLOT_ID.test(slot)) throw new ValidationError(`Invalid slot: ${slot || "(blank)"}`);
    if (seenSlots.has(slot)) throw new ValidationError("A slot may appear only once.");
    if (!Number.isInteger(penalty) || penalty < 0 || penalty > 100) {
      throw new ValidationError("Penalty must be an integer from 0 to 100.");
    }
    seenSlots.add(slot);
    return { slot, penalty, reason };
  });

  if (rawPrerequisites.length && !course) {
    throw new ValidationError("Choose the course these prerequisites belong to.");
  }
  if (rawPrerequisites.length > MAX_PREREQUISITES) {
    throw new ValidationError(`List at most ${MAX_PREREQUISITES} prerequisites.`);
  }
  const prerequisites = [...new Set(rawPrerequisites.map(courseId))];
  if (prerequisites.includes(course)) throw new ValidationError("A course cannot be its own prerequisite.");

  return {
    instructor,
    instructor_key: instructor.toLowerCase(),
    course_id: course,
    preferences: slots,
    prerequisites: prerequisitesGiven && course ? prerequisites : null
  };
}

/** Accepts "EE1101, EE2015 ee3001" or an array and returns upper-case IDs. */
function splitCourseList(value) {
  if (Array.isArray(value)) return value.map(item => clean(item, 30)).filter(Boolean);
  return clean(value, 400).split(/[\s,;]+/).filter(Boolean);
}

function numberOrNull(value) {
  if (value === "" || value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) ? Math.trunc(number) : null;
}

function validatePublication(payload = {}) {
  const slotbook = Array.isArray(payload.slotbook) ? payload.slotbook : [];
  const explanations = Array.isArray(payload.explanations) ? payload.explanations : [];
  if (!slotbook.length) throw new ValidationError("Slotbook rows are required.");
  for (const [name, rows] of [["slotbook", slotbook], ["explanation", explanations]]) {
    const seen = new Set();
    for (const row of rows) {
      const id = clean(row && row.course_id, 30);
      if (!id) throw new ValidationError(`Every ${name} row needs a course_id.`);
      if (seen.has(id)) throw new ValidationError(`Course ${id} appears twice in the ${name} rows.`);
      seen.add(id);
    }
  }
  return {
    solver: clean(payload.solver || "manual", 30),
    slotbook: slotbook.map(row => ({
      course_id: clean(row.course_id, 30),
      title: clean(row.title, 160),
      semester_group: clean(row.semester_group, 60),
      course_type: clean(row.course_type, 40),
      instructor: clean(row.instructor, 80),
      assigned_slot: clean(row.assigned_slot, 20),
      additional_slot: clean(row.additional_slot, 20),
      room: clean(row.room, 60),
      class_size: numberOrNull(row.class_size)
    })),
    explanations: explanations.map(row => ({
      course_id: clean(row.course_id, 30),
      assigned_slot: clean(row.assigned_slot, 20),
      reasons: clean(row.reasons || row.explanation, 5000),
      rejected_or_feasible_alternatives: clean(row.rejected_or_feasible_alternatives || row.alternatives, 10000)
    }))
  };
}

module.exports = {
  ValidationError,
  clean,
  splitCourseList,
  validateInstructor,
  validatePublication,
  validateStudent
};
