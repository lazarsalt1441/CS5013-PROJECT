// Postgres storage for the portal. This module is the only place that runs
// SQL; API handlers call these functions and never build queries themselves.
//
// Every query is parameterised. Writes that replace a person's earlier
// submission run in one transaction, so a reader never sees a half-replaced
// preference list (v2 achieved the same with an Apps Script lock).

const { getPool, withTransaction } = require("./_db");

function iso(value) {
  return value instanceof Date ? value.toISOString() : value;
}

/**
 * Saves a student's ranked preferences, replacing any earlier submission.
 * When `completions` is an array, the student's completions from `source`
 * are replaced too; when it is null they are left as they were, so a
 * resubmission without a new grade card keeps the previously parsed one.
 */
async function saveStudent(student, { completions = null, source = "grade_card" } = {}) {
  return withTransaction(async client => {
    const { rows } = await client.query(
      `INSERT INTO student_submissions (student_id, programme, year, submitted_at)
       VALUES ($1, $2, $3, now())
       ON CONFLICT (student_id) DO UPDATE
         SET programme = EXCLUDED.programme, year = EXCLUDED.year, submitted_at = now()
       RETURNING submitted_at`,
      [student.student_id, student.programme, student.year]
    );
    await client.query("DELETE FROM student_preferences WHERE student_id = $1", [student.student_id]);
    for (const preference of student.preferences) {
      await client.query(
        "INSERT INTO student_preferences (student_id, course_id, rank) VALUES ($1, $2, $3)",
        [student.student_id, preference.course_id, preference.rank]
      );
    }
    if (Array.isArray(completions)) {
      await client.query("DELETE FROM completed_courses WHERE student_id = $1 AND source = $2", [student.student_id, source]);
      for (const completion of completions) {
        await client.query(
          `INSERT INTO completed_courses (student_id, course_id, grade, term, source)
           VALUES ($1, $2, $3, $4, $5)
           ON CONFLICT (student_id, course_id, source) DO UPDATE
             SET grade = EXCLUDED.grade, term = EXCLUDED.term, recorded_at = now()`,
          [student.student_id, completion.course_id, completion.grade, completion.term || "", source]
        );
      }
    }
    return { rows_saved: student.preferences.length, submitted_at: iso(rows[0].submitted_at) };
  });
}

/** Saves an instructor's slot penalties and, if given, their course's prerequisites. */
async function saveInstructor(instructor) {
  return withTransaction(async client => {
    const { rows } = await client.query(
      `INSERT INTO instructor_submissions (instructor_key, instructor, course_id, submitted_at)
       VALUES ($1, $2, $3, now())
       ON CONFLICT (instructor_key) DO UPDATE
         SET instructor = EXCLUDED.instructor, course_id = EXCLUDED.course_id, submitted_at = now()
       RETURNING submitted_at`,
      [instructor.instructor_key, instructor.instructor, instructor.course_id || null]
    );
    await client.query("DELETE FROM instructor_preferences WHERE instructor_key = $1", [instructor.instructor_key]);
    for (const preference of instructor.preferences) {
      await client.query(
        "INSERT INTO instructor_preferences (instructor_key, slot, penalty, reason) VALUES ($1, $2, $3, $4)",
        [instructor.instructor_key, preference.slot, preference.penalty, preference.reason]
      );
    }
    if (Array.isArray(instructor.prerequisites) && instructor.course_id) {
      // Two professors may replace the same course's list at once. Without a
      // lock both delete, both insert, and the second commit hits the primary
      // key (found by the stress suite: 17 of 25 concurrent writes failed).
      // A transaction-scoped lock per course makes them take turns; the last
      // writer wins and the lock is released at COMMIT or ROLLBACK.
      await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", [`prerequisites:${instructor.course_id}`]);
      await client.query("DELETE FROM prerequisites WHERE course_id = $1", [instructor.course_id]);
      for (const prerequisite of instructor.prerequisites) {
        await client.query(
          "INSERT INTO prerequisites (course_id, prereq_course_id, declared_by) VALUES ($1, $2, $3)",
          [instructor.course_id, prerequisite, instructor.instructor]
        );
      }
    }
    return {
      rows_saved: instructor.preferences.length,
      prerequisites_saved: Array.isArray(instructor.prerequisites) ? instructor.prerequisites.length : null,
      submitted_at: iso(rows[0].submitted_at)
    };
  });
}

async function listPrerequisites() {
  const { rows } = await getPool().query(
    "SELECT course_id, prereq_course_id, declared_by FROM prerequisites ORDER BY course_id, prereq_course_id"
  );
  return rows;
}

async function listCompletions(studentId = null) {
  const { rows } = studentId
    ? await getPool().query(
      "SELECT student_id, course_id, grade, term, source FROM completed_courses WHERE student_id = $1 ORDER BY course_id, source",
      [studentId])
    : await getPool().query(
      "SELECT student_id, course_id, grade, term, source FROM completed_courses ORDER BY student_id, course_id, source");
  return rows;
}

/** Everything the admin dashboard and CSV exports need, in one read. */
async function snapshot() {
  const db = getPool();
  const students = (await db.query(
    "SELECT student_id, course_id, rank FROM student_preferences ORDER BY student_id, rank"
  )).rows;
  const instructors = (await db.query(
    `SELECT s.instructor, p.slot, p.penalty, p.reason
       FROM instructor_preferences p
       JOIN instructor_submissions s ON s.instructor_key = p.instructor_key
      ORDER BY s.instructor, p.slot`
  )).rows;
  return {
    students,
    instructors,
    prerequisites: await listPrerequisites(),
    completions: await listCompletions(),
    student_count: new Set(students.map(row => row.student_id)).size,
    instructor_count: new Set(instructors.map(row => row.instructor.toLowerCase())).size,
    student_rows: students.length,
    instructor_rows: instructors.length,
    generated_at: new Date().toISOString()
  };
}

/** Stores a new published run; public results then show this run. */
async function publishResults(publication) {
  return withTransaction(async client => {
    const { rows } = await client.query(
      "INSERT INTO published_runs (solver, published_at) VALUES ($1, now()) RETURNING run_id, published_at",
      [publication.solver]
    );
    const runId = rows[0].run_id;
    for (const row of publication.slotbook) {
      await client.query(
        `INSERT INTO slotbook_rows (run_id, course_id, title, semester_group, course_type, instructor,
                                    assigned_slot, additional_slot, room, class_size)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
        [runId, row.course_id, row.title, row.semester_group, row.course_type, row.instructor,
          row.assigned_slot, row.additional_slot, row.room, row.class_size]
      );
    }
    for (const row of publication.explanations) {
      await client.query(
        `INSERT INTO explanation_rows (run_id, course_id, assigned_slot, reasons, alternatives)
         VALUES ($1, $2, $3, $4, $5)`,
        [runId, row.course_id, row.assigned_slot, row.reasons, row.rejected_or_feasible_alternatives]
      );
    }
    return {
      run_id: runId,
      published_at: iso(rows[0].published_at),
      slotbook_rows: publication.slotbook.length,
      explanation_rows: publication.explanations.length
    };
  });
}

/** The latest published run, in the same shape v2's Sheets bridge returned. */
async function publicResults() {
  const db = getPool();
  const latest = (await db.query(
    "SELECT run_id, solver, published_at FROM published_runs ORDER BY run_id DESC LIMIT 1"
  )).rows[0];
  if (!latest) return { slotbook: [], explanations: [], published_at: null, solver: null };
  const publishedAt = iso(latest.published_at);
  const slotbook = (await db.query(
    `SELECT course_id, title, semester_group, course_type, instructor, assigned_slot,
            additional_slot, room, class_size
       FROM slotbook_rows WHERE run_id = $1 ORDER BY course_id`,
    [latest.run_id]
  )).rows.map(row => ({ published_at: publishedAt, solver: latest.solver, ...row, class_size: row.class_size ?? "" }));
  const explanations = (await db.query(
    `SELECT course_id, assigned_slot, reasons, alternatives AS rejected_or_feasible_alternatives
       FROM explanation_rows WHERE run_id = $1 ORDER BY course_id`,
    [latest.run_id]
  )).rows.map(row => ({ published_at: publishedAt, ...row }));
  return { slotbook, explanations, published_at: publishedAt, solver: latest.solver };
}

/** Deletes every submission, prerequisite, completion and published run. */
async function clearAll() {
  await withTransaction(async client => {
    for (const table of ["explanation_rows", "slotbook_rows", "published_runs", "completed_courses",
      "prerequisites", "instructor_preferences", "instructor_submissions", "student_preferences", "student_submissions"]) {
      await client.query(`DELETE FROM ${table}`);
    }
  });
  return { message: "All demo submissions, prerequisites, completions and published results were cleared." };
}

module.exports = {
  clearAll,
  listCompletions,
  listPrerequisites,
  publicResults,
  publishResults,
  saveInstructor,
  saveStudent,
  snapshot
};
