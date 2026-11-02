// Connection pool for the portal's Postgres database (Neon in production).
//
// Vercel runs each function as a short-lived instance, so the pool is small
// and created lazily on first use. Tests replace it with setPool(), which is
// how they run against pg-mem or a local Postgres without network access.

const { Pool } = require("pg");

let pool = null;

function sslFor(connectionString) {
  const host = new URL(connectionString).hostname;
  const local = ["localhost", "127.0.0.1", "::1"].includes(host);
  // Neon requires TLS; a local development database usually has none.
  return local ? false : { rejectUnauthorized: true };
}

function getPool() {
  if (pool) return pool;
  const connectionString = process.env.DATABASE_URL;
  if (!connectionString) {
    const error = new Error("DATABASE_URL is not configured.");
    error.status = 503;
    throw error;
  }
  pool = new Pool({ connectionString, ssl: sslFor(connectionString), max: 3, idleTimeoutMillis: 10_000 });
  return pool;
}

/** Replaces the pool; used by tests and by scripts/migrate.mjs. */
function setPool(replacement) {
  pool = replacement;
}

/** Runs fn(client) inside BEGIN/COMMIT, rolling back on any error. */
async function withTransaction(fn) {
  const client = await getPool().connect();
  try {
    await client.query("BEGIN");
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

module.exports = { getPool, setPool, sslFor, withTransaction };
