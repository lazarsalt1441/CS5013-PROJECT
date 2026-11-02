# About the Tool (v3)

## What it is

IITM Explainable Slotbook builds a department's weekly course slotbook. It assigns every course one lecture slot from the institute grid, respecting hard rules (fixed slots, labs, protected course pairs, instructors, rooms). Within those rules it minimises instructor penalties, movement from historical slots and student clashes. Every assignment comes with a deterministic explanation of why that slot was chosen and why each other slot was not.

It has two parts:

- **Engine** (Java, `slotbook` CLI and a JavaFX view). UniTime's cpsolver searches by default; an exact OR-Tools CP-SAT model is available for comparison.
- **Preference portal** (Vercel, Postgres). Students rank courses, professors give slot penalties, an administrator exports the CSVs the engine reads and publishes the result.

The problem it addresses, what "explainable" means and the data provenance are described in [v2's ABOUT](../docs_v2/ABOUT.md); they are unchanged.

## What v3 adds

**A proper database.** The portal's submissions, prerequisites, completions and published runs now live in Postgres. The rules that keep data solver-ready are database constraints, not only application code. Replacing an earlier submission is a single transaction.

**Prerequisite awareness.**

- A professor can say "EE5141 expects EE3110 and EE1101".
- A student can upload their grade card; the portal reads which courses they completed.
- Ranking a course whose prerequisite is not completed gives the student a warning at submission. The admin sees all such warnings, and the CLI prints the same ones.

Prerequisites are **advisory by design**. A warning never rejects a submission and never changes the timetable. A department can waive a prerequisite, and a self-uploaded grade card is not proof, so the tool informs rather than decides.

## What is trusted, and what is not

| Data | Origin | Trusted? |
|---|---|---|
| Course list, slots, conflicts | Administrator CSVs | Yes, validated before solving |
| Student rankings, instructor penalties | Public portal forms, no login | Demonstration only |
| Prerequisite rules | Professor form, no login | Demonstration only |
| Completed courses (`grade_card`) | Student's own PDF upload | **No**; self-reported |
| Completed courses (`admin`) | Academic section (not built yet) | Intended verified source |

## Privacy

- The uploaded PDF is read in memory and discarded. Only course code, grade and term are stored per completed course.
- The student's name, roll number and course titles from the card are not stored. The roll number is used only to warn when it differs from the entered student ID.
- The repository contains no real grade cards. Test fixtures are generated, marked `SYNTHETIC`, and the static check rejects any committed PDF without that marker.
- The portal still has no authentication. Do not collect real grade cards until IITM login, consent and retention rules exist.
