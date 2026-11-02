// Builds the CSV files the Java CLI reads from a store snapshot. Headers here
// must match DatasetLoader exactly; scripts/validate.mjs checks them against
// the repository fixtures.

const { toCsv } = require("./_lib");

const STUDENT_HEADERS = ["student_id", "course_id", "rank"];
const INSTRUCTOR_HEADERS = ["instructor", "slot", "penalty", "reason"];
const PREREQUISITE_HEADERS = ["course_id", "prereq_course_id"];
const COMPLETION_HEADERS = ["student_id", "course_id", "grade", "term", "source"];
const WARNING_HEADERS = ["student_id", "code", "course_id", "prereq_course_id", "message"];

function buildExports(snapshot) {
  return {
    student_preferences_csv: toCsv(STUDENT_HEADERS, snapshot.students || []),
    instructor_preferences_csv: toCsv(INSTRUCTOR_HEADERS, snapshot.instructors || []),
    prerequisites_csv: toCsv(PREREQUISITE_HEADERS, snapshot.prerequisites || []),
    completed_courses_csv: toCsv(COMPLETION_HEADERS, snapshot.completions || [])
  };
}

module.exports = {
  COMPLETION_HEADERS,
  INSTRUCTOR_HEADERS,
  PREREQUISITE_HEADERS,
  STUDENT_HEADERS,
  WARNING_HEADERS,
  buildExports
};
