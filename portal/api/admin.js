const { callSheets, errorResponse, json, readBody, requireAdmin, requireMethod, toCsv } = require("./_lib");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["POST"])) return;
  if (!requireAdmin(req, res)) return;
  try {
    const body = await readBody(req);
    const action = body.action || "snapshot";
    if (!["snapshot", "publish_results", "clear_demo_data"].includes(action)) {
      return json(res, 400, { ok: false, error: "Unsupported admin action." });
    }
    const result = await callSheets({ action: `admin_${action}`, payload: body.payload || {} }, { admin: true });
    if (action === "snapshot") {
      result.student_preferences_csv = toCsv(
        ["student_id", "course_id", "rank"],
        result.students || []
      );
      result.instructor_preferences_csv = toCsv(
        ["instructor", "slot", "penalty", "reason"],
        result.instructors || []
      );
    }
    return json(res, 200, result);
  } catch (error) {
    return errorResponse(res, error);
  }
};
