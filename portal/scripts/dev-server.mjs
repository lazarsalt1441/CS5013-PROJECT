// Local development server: serves public/ and routes /api/<name> to
// api/<name>.js, mimicking Vercel's routing closely enough to exercise the
// forms end to end against a local or Neon database.
//
// Usage: DATABASE_URL=postgres://... ADMIN_PASSWORD=dev npm run dev
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const publicDir = path.join(root, "public");
const port = Number(process.env.PORT || 4173);
const rewrites = JSON.parse(fs.readFileSync(path.join(root, "vercel.json"), "utf8")).rewrites || [];
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".png": "image/png" };

function addHelpers(res) {
  res.getHeader = res.getHeader.bind(res);
  return res;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  const api = url.pathname.match(/^\/api\/([a-z]+)$/);
  if (api) {
    const file = path.join(root, "api", `${api[1]}.js`);
    if (!fs.existsSync(file)) { res.statusCode = 404; return res.end("Not found"); }
    return require(file)(req, addHelpers(res));
  }
  const rewrite = rewrites.find(rule => rule.source === url.pathname);
  const target = path.normalize(path.join(publicDir, rewrite ? rewrite.destination : url.pathname));
  if (!target.startsWith(publicDir) || !fs.existsSync(target) || fs.statSync(target).isDirectory()) {
    res.statusCode = 404;
    return res.end("Not found");
  }
  res.setHeader("Content-Type", types[path.extname(target)] || "application/octet-stream");
  fs.createReadStream(target).pipe(res);
});

server.listen(port, () => console.log(`Portal on http://localhost:${port} (database: ${process.env.DATABASE_URL ? "configured" : "NOT configured"})`));
