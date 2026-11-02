// Where a student's completed courses come from.
//
// A completion source turns a student submission into completed-course rows
// tagged with the source's id (the `source` column of completed_courses).
// Prerequisite checking always reads every stored completion, whatever its
// source, so a new source is picked up without changing the checker.
//
// v3 ships one source, `grade_card`: the student uploads their own PDF grade
// card. It is unverified.
//
// Extension (not built yet): an `admin` source for records supplied by the
// academic section. Add an entry here whose `fromSubmission` returns
// { completions: null } (students supply nothing), plus an admin upload
// action that writes completed_courses rows with source = 'admin'. The
// schema already allows that value, the checker already reads it, and the
// student form needs no change.

const { GradeCardError, parseGradeCard } = require("./_gradecard");

const gradeCard = {
  id: "grade_card",
  verified: false,

  /**
   * @returns {{completions: object[] | null, warnings: object[]}}
   *   completions is null when the student did not upload a card, which
   *   keeps any card they uploaded earlier.
   */
  async fromSubmission(studentId, payload) {
    if (!payload || !payload.grade_card) return { completions: null, warnings: [] };
    try {
      const card = await parseGradeCard(payload.grade_card);
      const warnings = [];
      if (card.roll_number && card.roll_number !== studentId) {
        warnings.push({
          code: "ROLL_NUMBER_MISMATCH",
          message: `The grade card belongs to roll number ${card.roll_number}, not ${studentId}. It was still used.`
        });
      }
      if (card.unreadable_rows.length) {
        warnings.push({
          code: "GRADE_CARD_ROWS_SKIPPED",
          message: `${card.unreadable_rows.length} course row(s) on the grade card could not be read and were skipped.`
        });
      }
      return { completions: card.completed, warnings };
    } catch (error) {
      if (!(error instanceof GradeCardError)) throw error;
      // An unreadable card is reported, not fatal: preferences still save.
      return { completions: null, warnings: [{ code: "GRADE_CARD_UNREADABLE", message: error.message }] };
    }
  }
};

const SOURCES = Object.freeze({ [gradeCard.id]: gradeCard });

function completionSource(id = process.env.COMPLETION_SOURCE || "grade_card") {
  const source = SOURCES[id];
  if (!source) throw new Error(`Unknown COMPLETION_SOURCE "${id}". Known: ${Object.keys(SOURCES).join(", ")}.`);
  return source;
}

module.exports = { SOURCES, completionSource };
