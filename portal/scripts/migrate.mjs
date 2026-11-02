// Usage: DATABASE_URL=postgres://... npm run db:migrate
// Applies any pending db/migrations/*.sql files to the configured database.
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { getPool } = require("../api/_db.js");
const { migrate } = require("../db/migrate.js");

const pool = getPool();
try {
  const ran = await migrate(pool, { log: message => console.log(message) });
  console.log(ran.length ? `Migrated: ${ran.length} file(s).` : "Database already up to date.");
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await pool.end();
}
