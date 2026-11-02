const { errorResponse, json, requireMethod } = require("./_lib");
const store = require("./_store");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["GET"])) return;
  try {
    const result = await store.publicResults();
    res.setHeader("Cache-Control", "public, max-age=30, s-maxage=60");
    return json(res, 200, { ok: true, ...result });
  } catch (error) {
    return errorResponse(res, error);
  }
};
