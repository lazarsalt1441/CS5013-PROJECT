// Applies db/migrations/*.sql in filename order, once each.
//
// Applied versions are recorded in schema_migrations, so running this again
// is a no-op. Each file runs in its own transaction: a failing migration
// leaves the database at the previous version instead of half-applied.

const fs = require("node:fs");
const path = require("node:path");

const MIGRATIONS_DIR = path.join(__dirname, "migrations");

function migrationFiles() {
  return fs.readdirSync(MIGRATIONS_DIR).filter(name => /^\d{3}_.+\.sql$/.test(name)).sort();
}

async function migrate(pool, { log = () => {} } = {}) {
  // Checked explicitly rather than with CREATE TABLE IF NOT EXISTS, which
  // pg-mem (used by the unit tests) cannot re-run on an existing table.
  const existing = await pool.query(
    "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'schema_migrations'"
  );
  if (!existing.rows.length) {
    await pool.query(`CREATE TABLE schema_migrations (
      version     VARCHAR(100) PRIMARY KEY,
      applied_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
    )`);
  }
  const applied = new Set((await pool.query("SELECT version FROM schema_migrations")).rows.map(row => row.version));
  const ran = [];
  for (const file of migrationFiles()) {
    if (applied.has(file)) continue;
    const sql = fs.readFileSync(path.join(MIGRATIONS_DIR, file), "utf8");
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations (version) VALUES ($1)", [file]);
      await client.query("COMMIT");
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      throw new Error(`Migration ${file} failed: ${error.message}`);
    } finally {
      client.release();
    }
    ran.push(file);
    log(`applied ${file}`);
  }
  return ran;
}

module.exports = { migrate, migrationFiles };
