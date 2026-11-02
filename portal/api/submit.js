const { errorResponse, json, readBody, requireMethod } = require("./_lib");
const store = require("./_store");
const { validateInstructor, validateStudent } = require("./_validation");

async function submitStudent(payload) {
  const student = validateStudent(payload);
  const saved = await store.saveStudent(student);
  return { ok: true, message: "Student preferences saved.", ...saved };
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
