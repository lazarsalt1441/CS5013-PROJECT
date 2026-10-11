# IITM Slotbook Preference Portal

This directory is a dependency-free Vercel application for collecting the two preference inputs consumed by the slotbook wrapper:

- `student_preferences.csv`: `student_id,course_id,rank`
- `instructor_preferences.csv`: `instructor,slot,penalty,reason`

It provides public student and professor forms, an admin dashboard, exact CSV exports, an optional solver HTTP hook, and a public slotbook/explanation page. Submitted data is stored in Google Sheets through the included Apps Script bridge.

## Architecture

```text
browser forms -> Vercel /api functions -> Google Apps Script -> Google Sheet
                                      \-> optional Java solver API

admin dashboard <- CSV/JSON export <- Google Sheet
public results  <- published rows  <- admin upload or solver response
```

Vercel's filesystem is ephemeral, so the application never writes submissions to local files in production.

## 1. Create the free Google Sheet backend

1. Create an empty Google Sheet.
2. Open **Extensions -> Apps Script**.
3. Replace `Code.gs` with `google-apps-script/Code.gs` from this directory.
4. In **Project Settings -> Script properties**, add:
   - `SPREADSHEET_ID`: the ID between `/d/` and `/edit` in the Sheet URL.
   - `ADMIN_API_KEY`: a long random string.
5. Run `setupSheets` once and approve the requested spreadsheet permission.
6. Choose **Deploy -> New deployment -> Web app**.
7. Execute as yourself and allow access to **Anyone**. The bridge itself restricts administrative actions with `ADMIN_API_KEY`.
8. Copy the deployment URL ending in `/exec`.

The script creates `Students`, `Instructors`, `Slotbook`, and `Explanations` tabs. Re-submitting the same student or instructor replaces their previous rows atomically.

## 2. Deploy on Vercel

1. Import the GitHub repository in Vercel.
2. Set **Root Directory** to `portal`.
3. Framework preset: **Other**. No build command is required; output directory is `public`.
4. Add the variables from `.env.example`:
   - `GOOGLE_APPS_SCRIPT_URL`
   - `ADMIN_PASSWORD`
   - `GOOGLE_APPS_SCRIPT_ADMIN_KEY`
   - optionally `SOLVER_API_URL` and `SOLVER_API_KEY`
5. Deploy.

Run `npm run check` before deployment. To preview only the static pages locally, run `npm run serve`; Vercel CLI is required to exercise the `/api` routes locally.

## 3. Solver integration

Without `SOLVER_API_URL`, all collection, dashboard, validation, CSV export, and manual publication features work. The admin can download both CSVs, run the existing Java CLI, then upload `generated_slotbook.csv` and `assignment_explanations.csv` to publish results.

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

The Vercel API immediately publishes those rows to Google Sheets. This boundary lets the current Maven application remain unchanged while a small HTTP wrapper can be deployed separately on a JVM host.

## Privacy and demo status

This version intentionally has no student/faculty authentication, as selected for the demonstration. The UI prominently labels this limitation. Do not collect real institutional preferences until IITM login, consent, retention, and administrator access controls are added. The admin route is protected by a server-side password, but that is not a substitute for institutional identity management.
