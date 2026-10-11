const { callSheets, errorResponse, json, readBody, requireAdmin, requireMethod, toCsv } = require("./_lib");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["POST"])) return;
  if (!requireAdmin(req, res)) return;
  try {
    const solverUrl = process.env.SOLVER_API_URL;
    if (!solverUrl) {
      return json(res, 503, {
        ok: false,
        code: "SOLVER_NOT_CONFIGURED",
        error: "SOLVER_API_URL is not configured. Export the CSV files, run the Java CLI, and upload its two outputs instead."
      });
    }
    const body = await readBody(req);
    const snapshot = await callSheets({ action: "admin_snapshot" }, { admin: true });
    const payload = {
      solver: body.solver === "cpsat" ? "cpsat" : "unitime",
      student_preferences_csv: toCsv(["student_id", "course_id", "rank"], snapshot.students || []),
      instructor_preferences_csv: toCsv(["instructor", "slot", "penalty", "reason"], snapshot.instructors || [])
    };
    const response = await fetch(solverUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(process.env.SOLVER_API_KEY ? { Authorization: `Bearer ${process.env.SOLVER_API_KEY}` } : {})
      },
      body: JSON.stringify(payload)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || `Solver returned ${response.status}.`);
    if (!Array.isArray(result.slotbook) || !Array.isArray(result.explanations)) {
      throw new Error("Solver response must contain slotbook and explanations arrays.");
    }
    await callSheets({
      action: "admin_publish_results",
      payload: { slotbook: result.slotbook, explanations: result.explanations, solver: payload.solver }
    }, { admin: true });
    return json(res, 200, { ok: true, ...result });
  } catch (error) {
    return errorResponse(res, error);
  }
};
