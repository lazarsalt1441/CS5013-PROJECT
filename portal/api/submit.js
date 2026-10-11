const { callSheets, errorResponse, json, readBody, requireMethod } = require("./_lib");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["POST"])) return;
  try {
    const body = await readBody(req);
    if (!["student", "instructor"].includes(body.type)) {
      return json(res, 400, { ok: false, error: "type must be student or instructor." });
    }
    const result = await callSheets({ action: `submit_${body.type}`, payload: body.payload });
    return json(res, 200, result);
  } catch (error) {
    return errorResponse(res, error);
  }
};
