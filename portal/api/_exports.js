// Builds the CSV files the Java CLI reads from a store snapshot. Headers here
// must match DatasetLoader exactly; scripts/validate.mjs checks the two
// required ones against the repository fixtures.

const { toCsv } = require("./_lib");

const STUDENT_HEADERS = ["student_id", "course_id", "rank"];
const INSTRUCTOR_HEADERS = ["instructor", "slot", "penalty", "reason"];

function buildExports(snapshot) {
  return {
    student_preferences_csv: toCsv(STUDENT_HEADERS, snapshot.students || []),
    instructor_preferences_csv: toCsv(INSTRUCTOR_HEADERS, snapshot.instructors || [])
  };
}

module.exports = { INSTRUCTOR_HEADERS, STUDENT_HEADERS, buildExports };
