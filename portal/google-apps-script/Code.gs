const SHEETS = Object.freeze({
  Students: ["submitted_at", "student_id", "programme", "year", "course_id", "rank"],
  Instructors: ["submitted_at", "instructor", "course_id", "slot", "penalty", "reason"],
  Slotbook: ["published_at", "solver", "course_id", "title", "semester_group", "course_type", "instructor", "assigned_slot", "additional_slot", "room", "class_size"],
  Explanations: ["published_at", "course_id", "assigned_slot", "reasons", "rejected_or_feasible_alternatives"]
});

function setupSheets() {
  Object.keys(SHEETS).forEach(name => sheetFor_(name));
}

function doGet() {
  return response_(publicResults_());
}

function doPost(event) {
  try {
    const request = JSON.parse((event && event.postData && event.postData.contents) || "{}");
    const action = String(request.action || "");
    let result;
    if (action === "submit_student") result = submitStudent_(request.payload || {});
    else if (action === "submit_instructor") result = submitInstructor_(request.payload || {});
    else if (action === "public_results") result = publicResults_();
    else if (action.indexOf("admin_") === 0) {
      requireAdmin_(request.admin_key);
      if (action === "admin_snapshot") result = adminSnapshot_();
      else if (action === "admin_publish_results") result = publishResults_(request.payload || {});
      else if (action === "admin_clear_demo_data") result = clearDemoData_();
      else throw new Error("Unsupported admin action.");
    } else throw new Error("Unsupported action.");
    return response_(result);
  } catch (error) {
    return response_({ ok: false, error: error && error.message ? error.message : String(error) });
  }
}

function spreadsheet_() {
  const id = PropertiesService.getScriptProperties().getProperty("SPREADSHEET_ID");
  if (!id) throw new Error("SPREADSHEET_ID is not configured in Script properties.");
  return SpreadsheetApp.openById(id);
}

function sheetFor_(name) {
  const headers = SHEETS[name];
  if (!headers) throw new Error("Unknown sheet: " + name);
  const workbook = spreadsheet_();
  let sheet = workbook.getSheetByName(name);
  if (!sheet) sheet = workbook.insertSheet(name);
  if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight("bold").setBackground("#d9ead3");
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function requireAdmin_(supplied) {
  const expected = PropertiesService.getScriptProperties().getProperty("ADMIN_API_KEY");
  if (!expected) throw new Error("ADMIN_API_KEY is not configured in Script properties.");
  if (!supplied || String(supplied) !== expected) throw new Error("Administrative authorization failed.");
}

function submitStudent_(payload) {
  const studentId = clean_(payload.student_id, 30).toUpperCase();
  const programme = clean_(payload.programme, 60);
  const year = clean_(payload.year, 20);
  const preferences = Array.isArray(payload.preferences) ? payload.preferences : [];
  if (!/^[A-Z0-9_-]{3,30}$/.test(studentId)) throw new Error("Student ID must use 3-30 letters, digits, underscores, or hyphens.");
  if (!programme || !year) throw new Error("Programme and year are required.");
  if (preferences.length < 1 || preferences.length > 10) throw new Error("Choose between 1 and 10 courses.");
  const seen = {};
  const now = new Date().toISOString();
  const rows = preferences.map((item, index) => {
    const courseId = clean_(item.course_id, 30).toUpperCase();
    if (!/^[A-Z]{2,5}[0-9A-Z-]{2,20}$/.test(courseId)) throw new Error("Invalid course ID: " + courseId);
    if (seen[courseId]) throw new Error("A course may appear only once.");
    seen[courseId] = true;
    return [now, studentId, programme, year, courseId, index + 1];
  });
  replaceIdentityRows_("Students", 2, studentId, rows);
  return { ok: true, message: "Student preferences saved.", rows_saved: rows.length, submitted_at: now };
}

function submitInstructor_(payload) {
  const instructor = clean_(payload.instructor, 80);
  const courseId = clean_(payload.course_id, 30).toUpperCase();
  const preferences = Array.isArray(payload.preferences) ? payload.preferences : [];
  if (instructor.length < 2) throw new Error("Instructor name is required.");
  if (courseId && !/^[A-Z]{2,5}[0-9A-Z-]{2,20}$/.test(courseId)) throw new Error("Invalid course ID.");
  if (preferences.length < 1 || preferences.length > 20) throw new Error("Provide at least one slot preference.");
  const seen = {};
  const now = new Date().toISOString();
  const identity = instructor.toLowerCase();
  const rows = preferences.map(item => {
    const slot = clean_(item.slot, 12).toUpperCase();
    const penalty = Number(item.penalty);
    const reason = clean_(item.reason, 240);
    if (!/^[A-Z][A-Z0-9-]{0,10}$/.test(slot)) throw new Error("Invalid slot: " + slot);
    if (seen[slot]) throw new Error("A slot may appear only once.");
    if (!Number.isInteger(penalty) || penalty < 0 || penalty > 100) throw new Error("Penalty must be an integer from 0 to 100.");
    seen[slot] = true;
    return [now, instructor, courseId, slot, penalty, reason];
  });
  replaceIdentityRows_("Instructors", 2, identity, rows, value => String(value).toLowerCase());
  return { ok: true, message: "Instructor preferences saved.", rows_saved: rows.length, submitted_at: now };
}

function replaceIdentityRows_(sheetName, identityColumn, identity, replacementRows, normalize) {
  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const sheet = sheetFor_(sheetName);
    const headers = SHEETS[sheetName];
    const values = sheet.getDataRange().getValues();
    const normalizer = normalize || (value => String(value));
    const kept = values.slice(1).filter(row => normalizer(row[identityColumn - 1]) !== identity);
    const all = kept.concat(replacementRows);
    if (sheet.getMaxRows() > 1) sheet.getRange(2, 1, sheet.getMaxRows() - 1, headers.length).clearContent();
    if (all.length) sheet.getRange(2, 1, all.length, headers.length).setValues(all);
  } finally {
    lock.releaseLock();
  }
}

function adminSnapshot_() {
  const students = objects_("Students").map(row => ({
    student_id: row.student_id,
    course_id: row.course_id,
    rank: Number(row.rank)
  }));
  const instructors = objects_("Instructors").map(row => ({
    instructor: row.instructor,
    slot: row.slot,
    penalty: Number(row.penalty),
    reason: row.reason
  }));
  return {
    ok: true,
    students: students,
    instructors: instructors,
    student_count: uniqueCount_(students, "student_id"),
    instructor_count: uniqueCount_(instructors, "instructor"),
    student_rows: students.length,
    instructor_rows: instructors.length,
    generated_at: new Date().toISOString()
  };
}

function publishResults_(payload) {
  const slotbook = Array.isArray(payload.slotbook) ? payload.slotbook : [];
  const explanations = Array.isArray(payload.explanations) ? payload.explanations : [];
  if (!slotbook.length) throw new Error("Slotbook rows are required.");
  const solver = clean_(payload.solver || "manual", 30);
  const now = new Date().toISOString();
  const slotRows = slotbook.map(row => [
    now, solver, clean_(row.course_id, 30), clean_(row.title, 160), clean_(row.semester_group, 60),
    clean_(row.course_type, 40), clean_(row.instructor, 80), clean_(row.assigned_slot, 20),
    clean_(row.additional_slot, 20), clean_(row.room, 60), numberOrBlank_(row.class_size)
  ]);
  const explanationRows = explanations.map(row => [
    now, clean_(row.course_id, 30), clean_(row.assigned_slot, 20),
    clean_(row.reasons || row.explanation, 5000),
    clean_(row.rejected_or_feasible_alternatives || row.alternatives, 10000)
  ]);
  replaceAllRows_("Slotbook", slotRows);
  replaceAllRows_("Explanations", explanationRows);
  return { ok: true, message: "Published slotbook results.", slotbook_rows: slotRows.length, explanation_rows: explanationRows.length, published_at: now };
}

function publicResults_() {
  const slotbook = objects_("Slotbook");
  const explanations = objects_("Explanations");
  return {
    ok: true,
    slotbook: slotbook,
    explanations: explanations,
    published_at: slotbook.length ? slotbook[0].published_at : null,
    solver: slotbook.length ? slotbook[0].solver : null
  };
}

function clearDemoData_() {
  Object.keys(SHEETS).forEach(name => replaceAllRows_(name, []));
  return { ok: true, message: "All demo submissions and published results were cleared." };
}

function replaceAllRows_(sheetName, rows) {
  const sheet = sheetFor_(sheetName);
  const headers = SHEETS[sheetName];
  if (sheet.getMaxRows() > 1) sheet.getRange(2, 1, sheet.getMaxRows() - 1, headers.length).clearContent();
  if (rows.length) sheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
}

function objects_(sheetName) {
  const sheet = sheetFor_(sheetName);
  const headers = SHEETS[sheetName];
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) return [];
  return sheet.getRange(2, 1, lastRow - 1, headers.length).getValues().map(row => {
    const object = {};
    headers.forEach((header, index) => object[header] = row[index]);
    return object;
  });
}

function uniqueCount_(rows, field) {
  const seen = {};
  rows.forEach(row => seen[String(row[field]).toLowerCase()] = true);
  return Object.keys(seen).length;
}

function clean_(value, maxLength) {
  return String(value == null ? "" : value).trim().replace(/[\u0000-\u001f\u007f]/g, " ").slice(0, maxLength);
}

function numberOrBlank_(value) {
  if (value === "" || value == null) return "";
  const number = Number(value);
  return Number.isFinite(number) ? number : "";
}

function response_(body) {
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}
