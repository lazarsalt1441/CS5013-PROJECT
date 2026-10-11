import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const portalDir = path.resolve(scriptDir, "..");
const repoDir = path.resolve(portalDir, "..");

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (quoted) {
      if (char === '"' && text[index + 1] === '"') { field += '"'; index += 1; }
      else if (char === '"') quoted = false;
      else field += char;
    } else if (char === '"') quoted = true;
    else if (char === ",") { row.push(field); field = ""; }
    else if (char === "\n") { row.push(field.replace(/\r$/, "")); if (row.some(Boolean)) rows.push(row); row = []; field = ""; }
    else field += char;
  }
  if (field || row.length) { row.push(field.replace(/\r$/, "")); rows.push(row); }
  if (!rows.length) return [];
  const headers = rows[0];
  return rows.slice(1).map(values => Object.fromEntries(headers.map((header, index) => [header, values[index] || ""])));
}

function read(relativePath) {
  return parseCsv(fs.readFileSync(path.join(repoDir, relativePath), "utf8"));
}

const courses = read("data/feasible/courses.csv").map(course => ({
  course_id: course.course_id,
  title: course.title,
  semester_group: course.semester_group,
  course_type: course.course_type,
  instructor: course.instructor,
  allowed_slots: course.allowed_slots.split("|").filter(Boolean)
}));

const slots = read("data/feasible/slots.csv").map(slot => {
  const days = slot.days.split("|");
  const times = slot.start_times.split("|");
  return {
    slot_id: slot.slot_id,
    category: slot.category,
    summary: days.map((day, index) => `${day} ${times[index] || ""}`.trim()).join(" · ")
  };
});

const output = {
  generated_from: "data/feasible/courses.csv and data/feasible/slots.csv",
  source_sha256: {
    courses: crypto.createHash("sha256").update(fs.readFileSync(path.join(repoDir, "data/feasible/courses.csv"))).digest("hex"),
    slots: crypto.createHash("sha256").update(fs.readFileSync(path.join(repoDir, "data/feasible/slots.csv"))).digest("hex")
  },
  courses,
  slots
};

const outputPath = path.join(portalDir, "public", "data", "catalog.json");
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, JSON.stringify(output, null, 2) + "\n");
console.log(`Wrote ${courses.length} courses and ${slots.length} slots to ${outputPath}`);
