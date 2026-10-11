const SHEETS_URL = process.env.GOOGLE_APPS_SCRIPT_URL;

function json(res, status, body) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  res.end(JSON.stringify(body));
}

function readBody(req) {
  if (req.body && typeof req.body === "object") return Promise.resolve(req.body);
  return new Promise((resolve, reject) => {
    let raw = "";
    req.on("data", chunk => {
      raw += chunk;
      if (raw.length > 1_000_000) reject(new Error("Request body is too large."));
    });
    req.on("end", () => {
      try {
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(new Error("Request body must be valid JSON."));
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

function requireAdmin(req, res) {
  const expected = process.env.ADMIN_PASSWORD;
  const supplied = req.headers["x-admin-password"];
  if (!expected) {
    json(res, 503, { ok: false, error: "ADMIN_PASSWORD is not configured." });
    return false;
  }
  if (typeof supplied !== "string" || supplied !== expected) {
    json(res, 401, { ok: false, error: "Incorrect admin password." });
    return false;
  }
  return true;
}

async function callSheets(payload, { admin = false } = {}) {
  if (!SHEETS_URL) throw new Error("GOOGLE_APPS_SCRIPT_URL is not configured.");
  const body = { ...payload };
  if (admin) body.admin_key = process.env.GOOGLE_APPS_SCRIPT_ADMIN_KEY || "";
  const response = await fetch(SHEETS_URL, {
    method: "POST",
    headers: { "Content-Type": "text/plain;charset=utf-8" },
    body: JSON.stringify(body),
    redirect: "follow"
  });
  const text = await response.text();
  let result;
  try {
    result = JSON.parse(text);
  } catch {
    throw new Error(`Google Sheets bridge returned ${response.status}, not JSON.`);
  }
  if (!response.ok || !result.ok) throw new Error(result.error || `Google Sheets bridge failed (${response.status}).`);
  return result;
}

function csvEscape(value) {
  const text = String(value ?? "");
  return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toCsv(headers, rows) {
  return [headers.join(","), ...rows.map(row => headers.map(key => csvEscape(row[key])).join(","))].join("\n") + "\n";
}

function errorResponse(res, error) {
  console.error(error);
  json(res, 500, { ok: false, error: error instanceof Error ? error.message : "Unexpected server error." });
}

module.exports = { callSheets, errorResponse, json, readBody, requireAdmin, requireMethod, toCsv };
