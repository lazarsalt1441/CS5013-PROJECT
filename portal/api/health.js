const { json, requireMethod } = require("./_lib");

module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["GET"])) return;
  return json(res, 200, {
    ok: true,
    sheets_configured: Boolean(process.env.GOOGLE_APPS_SCRIPT_URL),
    admin_configured: Boolean(process.env.ADMIN_PASSWORD && process.env.GOOGLE_APPS_SCRIPT_ADMIN_KEY),
    solver_configured: Boolean(process.env.SOLVER_API_URL)
  });
};
