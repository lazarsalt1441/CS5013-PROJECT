const { errorResponse, json, requireMethod } = require("./_lib");
const store = require("./_store");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["GET"])) return;
  try {
    const result = await store.publicResults();
    // No caching: a slotbook published from the admin page must show on the
    // next refresh. With a 60-second CDN cache the page kept showing "No
    // slotbook has been published yet" after a successful publish (P020 demo).
    res.setHeader("Cache-Control", "no-store");
    return json(res, 200, { ok: true, ...result });
  } catch (error) {
    return errorResponse(res, error);
  }
};
