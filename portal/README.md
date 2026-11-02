# IITM Slotbook Preference Portal

This directory is a Vercel application for collecting the preference inputs consumed by the slotbook engine:

- `student_preferences.csv`: `student_id,course_id,rank`
- `instructor_preferences.csv`: `instructor,slot,penalty,reason`

It provides public student and professor forms, an admin dashboard, exact CSV exports, an optional solver HTTP hook, and a public slotbook/explanation page. From v3, submissions are stored in **Postgres** (hosted on [Neon](https://neon.tech)'s free tier), replacing v2's Google Sheets bridge.

## Architecture

```text
browser forms -> Vercel /api functions -> api/_validation.js -> api/_store.js -> Postgres (Neon)
                                      \-> optional Java solver API

admin dashboard <- CSV/JSON export <- api/_store.js snapshot
public results  <- latest published_runs row
```

| Module | Responsibility |
|---|---|
| `api/_validation.js` | Pure input rules. No I/O, so every rule is unit tested directly. |
| `api/_store.js` | The only module that runs SQL. Parameterised queries; one transaction per replacing write. |
| `api/_db.js` | Lazily created connection pool from `DATABASE_URL`; tests swap it with `setPool()`. |
| `api/_exports.js` | Builds the Java CSV files from a snapshot. |
| `db/migrations/*.sql` | Numbered schema changes, applied once each by `db/migrate.js`. |

Vercel's filesystem is ephemeral, so the application never writes submissions to local files.

### Why Postgres on Neon

- Relational rules the Sheet could not hold are now constraints: foreign keys tie preferences to a submission, `CHECK` keeps ranks in 1–10 and penalties in 0–100, and primary keys stop duplicate courses or slots.
- Replacing a person's earlier submission runs in one transaction, instead of an Apps Script lock around a whole-sheet rewrite.
- Neon is serverless Postgres with a free tier and an official Vercel integration; the code uses the standard `pg` driver, so any Postgres works.
- Published runs are kept, not overwritten, so earlier slotbooks can be audited.

## 1. Create the database

1. Create a free project at <https://console.neon.tech> (or add the **Neon** integration from the Vercel Marketplace, which sets `DATABASE_URL` for you).
2. From **Dashboard → Connect**, copy the **pooled** connection string (its host contains `-pooler`).
3. Apply the schema once, from this directory:

   ```bash
   npm install
   DATABASE_URL='postgresql://...-pooler...neon.tech/neondb?sslmode=require' npm run db:migrate
   ```

   Re-running is safe: applied migrations are recorded in `schema_migrations` and skipped.

## 2. Deploy on Vercel

1. Import the GitHub repository in Vercel.
2. Set **Root Directory** to `portal`.
3. Framework preset: **Other**. No build command is required; output directory is `public`.
4. Add the variables from `.env.example`:
   - `DATABASE_URL` (skip if the Neon integration added it)
   - `ADMIN_PASSWORD`
   - optionally `SOLVER_API_URL` and `SOLVER_API_KEY`
5. Deploy, then open `/api/health` and check `database_configured` and `admin_configured` are `true`.

Run `npm test` and `npm run check` before deployment. To preview only the static pages locally, run `npm run serve`; Vercel CLI (`vercel dev`) is required to exercise the `/api` routes locally.

## 3. Solver integration

Without `SOLVER_API_URL`, all collection, dashboard, validation, CSV export, and manual publication features work. The admin can download the CSVs, run the existing Java CLI, then upload `generated_slotbook.csv` and `assignment_explanations.csv` to publish results.

If `SOLVER_API_URL` is configured, **Generate slotbook** sends:

```json
{
  "student_preferences_csv": "student_id,course_id,rank\n...",
  "instructor_preferences_csv": "instructor,slot,penalty,reason\n...",
  "solver": "unitime"
}
```

The endpoint should return:

```json
{
  "slotbook": [{ "course_id": "EE2002", "assigned_slot": "B" }],
  "explanations": [{ "course_id": "EE2002", "explanation": "..." }]
}
```

The Vercel API immediately stores those rows as a new published run. A solver error is shown to the admin as a 502 with the solver's message.

## 4. Tests

| Command | What it runs | Needs |
|---|---|---|
| `npm test` | Validation, store and HTTP contract tests on an in-memory Postgres (pg-mem) | nothing |
| `npm run test:integration` | Rollback, concurrency and constraint tests on real Postgres | `TEST_DATABASE_URL` (all tests skip without it) |
| `npm run check` | Static checks: required files, JS syntax, JSON, asset links, CSV headers, migration numbering | nothing |

The integration suite **drops and recreates the `public` schema**, so point `TEST_DATABASE_URL` at a throwaway database, never at Neon production. CI runs it against a `postgres:16` service container.

## Privacy and demo status

This version intentionally has no student/faculty authentication, as selected for the demonstration. The UI prominently labels this limitation. Do not collect real institutional preferences until IITM login, consent, retention, and administrator access controls are added. The admin route is protected by a server-side password (compared in constant time), but that is not a substitute for institutional identity management.

## Migrating from v2 (Google Sheets)

v2 data is demonstration data, so v3 starts empty. To carry rows over, export the v2 admin CSVs before switching and resubmit them, or insert them into `student_preferences` / `instructor_preferences` after creating the matching `*_submissions` rows. The v2 Apps Script is in Git history (`portal/google-apps-script/Code.gs` before the P015 commits).

## Prerequisite warnings (v3)

Professors list prerequisites for their course on the professor form. Students may upload their PDF grade card on the student form. When a student ranks a course whose prerequisite is not on their grade card, the response, the admin dashboard and `prerequisite_warnings.csv` show a warning. **Warnings never block a submission and never change the solver input.**

| Rule | Behaviour |
|---|---|
| Completed | Any grade except U, W and I (so S, A–E and P count). A retaken course counts if any attempt passed. |
| No grade card on record | One `NO_COMPLETION_RECORD` warning per student, not one per prerequisite. |
| Unreadable or non-PDF upload | `GRADE_CARD_UNREADABLE` warning; preferences still save. |
| Card roll number differs from the student ID | `ROLL_NUMBER_MISMATCH` warning; the card is still used. |
| Resubmission without a card | The previously parsed card is kept. |
| Professor resubmits with an empty prerequisites field | That course's prerequisites are cleared. |

What is stored: course code, grade and term per completed course (`completed_courses`). The PDF, course titles, the student's name and the roll number are not stored.

**Trust:** a student-uploaded grade card is not verified. `api/_completions.js` is the extension point for an `admin` source (records from the academic section). The `completed_courses.source` column already accepts `'admin'` and the checker already reads every source, so adding it needs a new source entry and an admin upload action, with no schema change and no change to the student form. Select the source with `COMPLETION_SOURCE` (default `grade_card`).

The admin dashboard also exports `prerequisites.csv` and `completed_courses.csv`, which the Java CLI reads to print the same warnings (`slotbook(1)`, `--prerequisites`).

## Local development

```bash
npm install
DATABASE_URL=postgres://localhost/slotbook npm run db:migrate
DATABASE_URL=postgres://localhost/slotbook ADMIN_PASSWORD=dev npm run dev   # http://localhost:4173
```

`scripts/dev-server.mjs` serves `public/` and routes `/api/*` to the same handlers Vercel runs, using the `rewrites` in `vercel.json`.
