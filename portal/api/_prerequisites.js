// Pure prerequisite checking: given what a student ranked, the declared
// prerequisite rules and the courses the student has completed, list the
// warnings. Prerequisites are advisory in v3 (P015): a warning never blocks a
// submission or changes what the solver receives.
//
// The Java PrerequisiteChecker applies the same rule and uses the same codes,
// so the portal and the CLI report the same thing for the same data.

const { isCompleted } = require("./_gradecard");

const CODES = Object.freeze({
  MISSING: "MISSING_PREREQUISITE",
  NO_RECORD: "NO_COMPLETION_RECORD"
});

/** @returns {Map<string, string[]>} course -> its prerequisites, sorted */
function rulesByCourse(prerequisites) {
  const rules = new Map();
  for (const { course_id: course, prereq_course_id: prereq } of prerequisites) {
    if (!rules.has(course)) rules.set(course, []);
    rules.get(course).push(prereq);
  }
  for (const list of rules.values()) list.sort();
  return rules;
}

/**
 * @param {{course_id: string, rank: number}[]} ranked  the student's ranked courses
 * @param {{course_id: string, prereq_course_id: string}[]} prerequisites
 * @param {Iterable<string>} completedCourseIds  course IDs that count as completed
 * @param {{hasRecord?: boolean}} options  hasRecord=false when the student has
 *        never supplied completions; one NO_COMPLETION_RECORD warning then
 *        replaces the per-course list, which would otherwise flag everything.
 */
function checkPrerequisites(ranked, prerequisites, completedCourseIds, { hasRecord = true } = {}) {
  const rules = rulesByCourse(prerequisites);
  const completed = new Set(completedCourseIds);
  const constrained = ranked.filter(item => rules.has(item.course_id));
  if (!constrained.length) return [];

  if (!hasRecord) {
    const courses = constrained.map(item => item.course_id);
    return [{
      code: CODES.NO_RECORD,
      course_id: courses.join("|"),
      prereq_course_id: "",
      message: `No grade card on record, so prerequisites for ${courses.join(", ")} could not be checked.`
    }];
  }

  const warnings = [];
  for (const { course_id: course } of constrained) {
    for (const prereq of rules.get(course)) {
      if (!completed.has(prereq)) {
        warnings.push({
          code: CODES.MISSING,
          course_id: course,
          prereq_course_id: prereq,
          message: `${course} lists ${prereq} as a prerequisite, which is not on your completed courses.`
        });
      }
    }
  }
  return warnings;
}

/**
 * Warnings for every student in an admin snapshot, as flat rows ready for
 * display or CSV export.
 */
function warningsForSnapshot(snapshot) {
  const ranksByStudent = new Map();
  for (const row of snapshot.students) {
    if (!ranksByStudent.has(row.student_id)) ranksByStudent.set(row.student_id, []);
    ranksByStudent.get(row.student_id).push(row);
  }
  // Any row is a record; only rows with a completing grade count as done.
  // Grade-card parsing already drops U/W/I, but other sources (admin records,
  // CSV imports) may include them.
  const completedByStudent = new Map();
  for (const row of snapshot.completions) {
    if (!completedByStudent.has(row.student_id)) completedByStudent.set(row.student_id, new Set());
    if (isCompleted(row.grade)) completedByStudent.get(row.student_id).add(row.course_id);
  }
  const rows = [];
  for (const [studentId, ranked] of ranksByStudent) {
    const completed = completedByStudent.get(studentId);
    for (const warning of checkPrerequisites(ranked, snapshot.prerequisites, completed || [], { hasRecord: Boolean(completed) })) {
      rows.push({ student_id: studentId, ...warning });
    }
  }
  return rows;
}

module.exports = { CODES, checkPrerequisites, isCompleted, warningsForSnapshot };
