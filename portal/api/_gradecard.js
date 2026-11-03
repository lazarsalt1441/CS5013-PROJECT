// Parses an IITM grade-card PDF into course attempts.
//
// The PDF is read in memory and discarded; only course ID, grade and term are
// returned. Names, titles and the roll number are never stored (the roll
// number is used only for a mismatch warning).
//
// Layout (from the IITM "grade card" PDF): one table row per course,
//   Term No | Course | Title | Credit | Grade | Attendance | Year Of Passing
// e.g.  01 | CY1001 | Chemistry I: ... | 10 | S | VG | 2023
// The parser works on table cells rebuilt from text positions, so it does not
// depend on how many spaces a PDF renderer puts between columns.

const MAX_PDF_BYTES = 3_000_000;

/**
 * True when pdf.js rejected the file itself. pdf.js signals file problems with
 * its own exception classes (InvalidPDFException, UnknownErrorException,
 * FormatError, ...); a plain JavaScript error (ReferenceError, TypeError) means
 * the library or the server is broken. The stress suite found that a list of
 * known names missed some of them, so classification is by origin instead.
 */
function isPdfFileError(error) {
  if (!error || typeof error.name !== "string") return false;
  return /Exception$/.test(error.name) || error.name === "FormatError";
}

/** Grades that do not count as completing a course (agreed in P015). */
const NOT_COMPLETED_GRADES = Object.freeze(["U", "W", "I"]);

const TERM = /^\d{1,2}$/;
const COURSE = /^[A-Z]{2,4}\d{3,4}[A-Z]?$/;
const CREDIT = /^\d{1,2}$/;
const GRADE = /^[A-Z]{1,2}$/;
const ATTENDANCE = /^[A-Z]{1,2}$/;
const YEAR = /^(19|20)\d{2}$/;
const ROLL = /Roll\s*No\s*:?\s*([A-Z]{2}\d{2}[A-Z]\d{3})/i;

class GradeCardError extends Error {
  constructor(message) {
    super(message);
    this.name = "GradeCardError";
  }
}

function isCompleted(grade) {
  return !NOT_COMPLETED_GRADES.includes(String(grade).toUpperCase());
}

/**
 * Turns one row of cells into a course attempt, or null when the row is not
 * a course row (headers, footers, totals).
 */
function parseRow(cells) {
  if (cells.length < 7) return null;
  const [term, course] = cells;
  const [credit, grade, attendance, year] = cells.slice(-4);
  if (!TERM.test(term) || !COURSE.test(course)) return null;
  if (!CREDIT.test(credit) || !GRADE.test(grade) || !ATTENDANCE.test(attendance) || !YEAR.test(year)) return null;
  return {
    term: term.padStart(2, "0"),
    course_id: course,
    title: cells.slice(2, -4).join(" "),
    credits: Number(credit),
    grade,
    year: Number(year)
  };
}

/**
 * Pure parser over rows of cell strings, so it can be tested without a PDF.
 * Returns every attempt, the courses that count as completed, and rows that
 * looked like course rows but could not be read.
 */
function parseGradeCardRows(rows) {
  const attempts = [];
  const unreadable = [];
  let rollNumber = null;
  for (const cells of rows) {
    const text = cells.join(" ");
    const roll = text.match(ROLL);
    if (roll && !rollNumber) rollNumber = roll[1].toUpperCase();
    const attempt = parseRow(cells);
    if (attempt) attempts.push(attempt);
    else if (cells.length >= 2 && TERM.test(cells[0]) && COURSE.test(cells[1])) unreadable.push(text);
  }

  // A course may be attempted more than once (e.g. U, then a pass). It counts
  // as completed if any attempt completed it; the latest such attempt is kept.
  const byCourse = new Map();
  for (const attempt of attempts) {
    if (!isCompleted(attempt.grade)) continue;
    const previous = byCourse.get(attempt.course_id);
    if (!previous || attempt.term >= previous.term) byCourse.set(attempt.course_id, attempt);
  }
  const completed = [...byCourse.values()]
    .map(({ course_id, grade, term }) => ({ course_id, grade, term }))
    .sort((a, b) => a.course_id.localeCompare(b.course_id));

  return { roll_number: rollNumber, attempts, completed, unreadable_rows: unreadable };
}

/** Groups PDF text items into rows (same baseline) of cells (left to right). */
function itemsToRows(items, tolerance = 2) {
  const rows = [];
  const cells = items
    .filter(item => item.str && item.str.trim())
    .map(item => ({ text: item.str.trim(), x: item.transform[4], y: item.transform[5] }))
    .sort((a, b) => b.y - a.y || a.x - b.x);
  for (const cell of cells) {
    const row = rows.find(candidate => Math.abs(candidate.y - cell.y) <= tolerance);
    if (row) row.cells.push(cell);
    else rows.push({ y: cell.y, cells: [cell] });
  }
  return rows.map(row => row.cells.sort((a, b) => a.x - b.x).map(cell => cell.text));
}

async function extractRows(bytes) {
  const { getDocumentProxy } = require("unpdf");
  // Every step that reads the file is inside the try: a corrupt page can fail
  // in getPage or getTextContent, not only when the document is opened.
  try {
    const pdf = await getDocumentProxy(new Uint8Array(bytes));
    const rows = [];
    for (let pageNumber = 1; pageNumber <= pdf.numPages; pageNumber += 1) {
      const page = await pdf.getPage(pageNumber);
      const { items } = await page.getTextContent();
      rows.push(...itemsToRows(items));
    }
    return rows;
  } catch (error) {
    // Only problems with the uploaded file become a user-facing message.
    // Anything else (e.g. the PDF library failing to load on an unsupported
    // Node version) is a server fault and must surface as a logged 500.
    if (error && error.name === "PasswordException") {
      throw new GradeCardError("The grade card is password-protected; upload an unlocked copy.");
    }
    if (isPdfFileError(error)) throw new GradeCardError("The grade card could not be read as a PDF.");
    throw error;
  }
}

/** Decodes the base64 upload sent by the student form. */
function decodeUpload(base64) {
  const text = String(base64 || "").replace(/^data:application\/pdf;base64,/, "");
  if (!text) throw new GradeCardError("The grade card upload is empty.");
  if (!/^[A-Za-z0-9+/=\s]+$/.test(text)) throw new GradeCardError("The grade card upload is not valid base64.");
  const bytes = Buffer.from(text, "base64");
  if (bytes.length > MAX_PDF_BYTES) throw new GradeCardError("The grade card must be smaller than 3 MB.");
  if (bytes.subarray(0, 5).toString("latin1") !== "%PDF-") throw new GradeCardError("The grade card must be a PDF file.");
  return bytes;
}

async function parseGradeCard(base64) {
  const result = parseGradeCardRows(await extractRows(decodeUpload(base64)));
  if (!result.attempts.length) {
    throw new GradeCardError("No course rows were found. Upload the PDF grade card downloaded from the IITM portal, not a scan or photo.");
  }
  return result;
}

module.exports = {
  GradeCardError,
  MAX_PDF_BYTES,
  NOT_COMPLETED_GRADES,
  decodeUpload,
  isCompleted,
  isPdfFileError,
  itemsToRows,
  parseGradeCard,
  parseGradeCardRows
};
