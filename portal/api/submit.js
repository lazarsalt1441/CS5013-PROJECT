const { errorResponse, json, readBody, requireMethod } = require("./_lib");
const { completionSource } = require("./_completions");
const { checkPrerequisites } = require("./_prerequisites");
const store = require("./_store");
const { validateInstructor, validateStudent } = require("./_validation");

// Order matters: the grade card is parsed before saving so its rows are
// written in the same transaction as the preferences; warnings are computed
// after saving so they reflect every stored completion, from any source.
async function submitStudent(payload) {
  const student = validateStudent(payload);
  const source = completionSource();
  const card = await source.fromSubmission(student.student_id, payload);
  const saved = await store.saveStudent(student, { completions: card.completions, source: source.id });

  const completions = await store.listCompletions(student.student_id);
  const prerequisiteWarnings = checkPrerequisites(
    student.preferences,
    await store.listPrerequisites(),
    completions.map(row => row.course_id),
    { hasRecord: completions.length > 0 }
  );
  const warnings = [...card.warnings, ...prerequisiteWarnings];
  return {
    ok: true,
    message: warnings.length ? "Student preferences saved with warnings." : "Student preferences saved.",
    ...saved,
    completed_courses_on_record: completions.length,
    warnings
  };
}

async function submitInstructor(payload) {
  const instructor = validateInstructor(payload);
  const saved = await store.saveInstructor(instructor);
  return { ok: true, message: "Instructor preferences saved.", ...saved };
}

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["POST"])) return;
  try {
    const body = await readBody(req);
    if (!["student", "instructor"].includes(body.type)) {
      return json(res, 400, { ok: false, error: "type must be student or instructor." });
    }
    const result = body.type === "student"
      ? await submitStudent(body.payload)
      : await submitInstructor(body.payload);
    return json(res, 200, result);
  } catch (error) {
    return errorResponse(res, error);
  }
};
