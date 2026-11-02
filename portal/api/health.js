const { json, requireMethod } = require("./_lib");

// Reports configuration only. It never connects to the database, so a
// health probe cannot exhaust Neon's connection limit or wake a suspended
// compute.
module.exports = async function handler(req, res) {
  if (!requireMethod(req, res, ["GET"])) return;
  return json(res, 200, {
    ok: true,
    database_configured: Boolean(process.env.DATABASE_URL),
    admin_configured: Boolean(process.env.ADMIN_PASSWORD),
    solver_configured: Boolean(process.env.SOLVER_API_URL)
  });
};
