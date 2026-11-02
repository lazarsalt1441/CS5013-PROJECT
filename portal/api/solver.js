const { errorResponse, httpError, json, readBody, requireAdmin, requireMethod } = require("./_lib");
const { buildExports } = require("./_exports");
const store = require("./_store");
const { validatePublication } = require("./_validation");

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
    const payload = {
      solver: body.solver === "cpsat" ? "cpsat" : "unitime",
      ...buildExports(await store.snapshot())
    };
    const response = await fetch(solverUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(process.env.SOLVER_API_KEY ? { Authorization: `Bearer ${process.env.SOLVER_API_KEY}` } : {})
      },
      body: JSON.stringify(payload)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw httpError(502, result.error || `Solver returned ${response.status}.`);
    if (!Array.isArray(result.slotbook) || !Array.isArray(result.explanations)) {
      throw httpError(502, "Solver response must contain slotbook and explanations arrays.");
    }
    await store.publishResults(validatePublication({
      slotbook: result.slotbook, explanations: result.explanations, solver: payload.solver
    }));
    return json(res, 200, { ok: true, ...result });
  } catch (error) {
    return errorResponse(res, error);
  }
};
