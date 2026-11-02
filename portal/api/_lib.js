const crypto = require("node:crypto");

function json(res, status, body) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  // Default to no caching, but keep a policy the handler already chose
  // (results.js). v2 overwrote it here, so results were never cached.
  if (!res.getHeader || !res.getHeader("Cache-Control")) res.setHeader("Cache-Control", "no-store");
  res.end(JSON.stringify(body));
}

// Grade cards arrive base64-encoded inside the JSON body, so the limit sits
// just under Vercel's 4.5 MB request cap rather than v2's 1 MB.
const MAX_BODY_BYTES = 4_400_000;

function readBody(req) {
  if (req.body && typeof req.body === "object") return Promise.resolve(req.body);
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > MAX_BODY_BYTES) reject(httpError(413, "Request body is too large."));
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(httpError(400, "Request body must be valid JSON."));
      }
    });
    req.on("error", reject);
  });
}

function requireMethod(req, res, methods) {
  if (!methods.includes(req.method)) {
    res.setHeader("Allow", methods.join(", "));
    json(res, 405, { ok: false, error: "Method not allowed." });
    return false;
  }
  return true;
}

// Constant-time comparison so response timing does not reveal how much of a
// guessed password was correct.
function sameSecret(supplied, expected) {
  const a = Buffer.from(supplied);
  const b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

function requireAdmin(req, res) {
  const expected = process.env.ADMIN_PASSWORD;
  const supplied = req.headers["x-admin-password"];
  if (!expected) {
    json(res, 503, { ok: false, error: "ADMIN_PASSWORD is not configured." });
    return false;
  }
  if (typeof supplied !== "string" || !sameSecret(supplied, expected)) {
    json(res, 401, { ok: false, error: "Incorrect admin password." });
    return false;
  }
  return true;
}

function csvEscape(value) {
  const text = String(value ?? "");
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(headers, rows) {
  return [headers.join(","), ...rows.map(row => headers.map(key => csvEscape(row[key])).join(","))].join("\n") + "\n";
}

function httpError(status, message) {
  const error = new Error(message);
  error.status = status;
  return error;
}

// Errors that carry an explicit status (validation 400, upstream solver 502,
// missing configuration 503) describe something the caller can act on, so their message is returned. Anything else is a server
// fault: it is logged in full, and the client gets a generic message so
// database details never reach the browser.
function errorResponse(res, error) {
  const status = Number.isInteger(error && error.status) ? error.status : 500;
  if (status === 500) {
    console.error(error);
    return json(res, 500, { ok: false, error: "Unexpected server error." });
  }
  return json(res, status, { ok: false, error: error.message });
}

module.exports = { MAX_BODY_BYTES, errorResponse, httpError, json, readBody, requireAdmin, requireMethod, toCsv };
