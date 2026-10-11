import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(scriptDir, "..");
const repo = path.resolve(root, "..");
const failures = [];

function assert(condition, message) {
  if (!condition) failures.push(message);
}

function exists(relativePath) {
  return fs.existsSync(path.join(root, relativePath));
}

const required = [
  "vercel.json", ".env.example", "google-apps-script/Code.gs",
  "api/_lib.js", "api/submit.js", "api/admin.js", "api/results.js", "api/solver.js", "api/health.js",
  "public/index.html", "public/student.html", "public/professor.html", "public/admin.html", "public/results.html",
  "public/assets/styles.css", "public/assets/common.js", "public/assets/student.js", "public/assets/professor.js",
  "public/assets/admin.js", "public/assets/results.js", "public/data/catalog.json"
];
required.forEach(file => assert(exists(file), `Missing required file: ${file}`));

for (const directory of ["api", "public/assets", "scripts"]) {
  for (const file of fs.readdirSync(path.join(root, directory)).filter(name => /\.(js|mjs)$/.test(name))) {
    try {
      execFileSync(process.execPath, ["--check", path.join(root, directory, file)], { stdio: "pipe" });
    } catch (error) {
      failures.push(`JavaScript syntax error in ${directory}/${file}: ${error.stderr?.toString().trim() || error.message}`);
    }
  }
}

for (const jsonFile of ["package.json", "vercel.json", "public/data/catalog.json"]) {
  try { JSON.parse(fs.readFileSync(path.join(root, jsonFile), "utf8")); }
  catch (error) { failures.push(`Invalid JSON in ${jsonFile}: ${error.message}`); }
}

const catalog = JSON.parse(fs.readFileSync(path.join(root, "public/data/catalog.json"), "utf8"));
assert(Array.isArray(catalog.courses) && catalog.courses.length >= 18, "Catalog must contain the repository courses.");
assert(Array.isArray(catalog.slots) && catalog.slots.some(slot => slot.category === "LECTURE"), "Catalog must contain lecture slots.");
assert(new Set(catalog.courses.map(course => course.course_id)).size === catalog.courses.length, "Course IDs must be unique.");
assert(new Set(catalog.slots.map(slot => slot.slot_id)).size === catalog.slots.length, "Slot IDs must be unique.");

const studentHeader = fs.readFileSync(path.join(repo, "data/feasible/student_preferences.csv"), "utf8").split(/\r?\n/, 1)[0];
const instructorHeader = fs.readFileSync(path.join(repo, "data/feasible/instructor_preferences.csv"), "utf8").split(/\r?\n/, 1)[0];
assert(studentHeader === "student_id,course_id,rank", `Unexpected student schema: ${studentHeader}`);
assert(instructorHeader === "instructor,slot,penalty,reason", `Unexpected instructor schema: ${instructorHeader}`);

for (const file of fs.readdirSync(path.join(root, "public")).filter(name => name.endsWith(".html"))) {
  const html = fs.readFileSync(path.join(root, "public", file), "utf8");
  const references = [...html.matchAll(/(?:src|href)="(\/assets\/[^"?#]+)"/g)].map(match => match[1]);
  references.forEach(reference => assert(exists(`public${reference}`), `${file} references missing asset ${reference}`));
  assert(/<title>[^<]+<\/title>/.test(html), `${file} needs a title.`);
  assert(/<meta name="viewport"/.test(html), `${file} needs a viewport meta tag.`);
}

const envText = fs.readFileSync(path.join(root, ".env.example"), "utf8");
assert(!/AIza[0-9A-Za-z_-]{20,}/.test(envText), ".env.example appears to contain a real Google API key.");

if (failures.length) {
  console.error(`Portal validation failed (${failures.length}):`);
  failures.forEach(failure => console.error(`- ${failure}`));
  process.exit(1);
}

console.log(`Portal validation passed: ${catalog.courses.length} courses, ${catalog.slots.length} slots, ${required.length} required files.`);
