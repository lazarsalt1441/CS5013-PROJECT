const { callSheets, errorResponse, json, requireMethod } = require("./_lib");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["GET"])) return;
  try {
    const result = await callSheets({ action: "public_results" });
    res.setHeader("Cache-Control", "public, max-age=30, s-maxage=60");
    return json(res, 200, result);
  } catch (error) {
    return errorResponse(res, error);
  }
};
