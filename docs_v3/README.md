# IITM Explainable Slotbook (v3: Postgres portal and prerequisite warnings)

v3 keeps the v2 engine (UniTime cpsolver by default, CP-SAT as the exact reference) unchanged and changes the system around it:

- the preference portal stores data in **Postgres (Neon)** instead of Google Sheets;
- professors declare **prerequisites**, students upload their **grade card**, and both the portal and the CLI report **advisory warnings** for missing prerequisites;
- a `slotbook(1)` **man page**, and a test plan that lists every test.

Earlier versions: [docs_v2](../docs_v2/README.md), [docs_v1](../docs_v1/README.md). The change was made under prompt [P015](../prompts/PROMPT_LOG.md#p015---v3-postgres-backend-grade-card-prerequisite-warnings-man-page).

## What changed from v2

| | v2 | v3 |
|---|---|---|
| Portal storage | Google Sheet through an Apps Script bridge | Postgres on Neon; numbered SQL migrations; FKs and CHECK constraints |
| Concurrency | Apps Script lock around a whole-sheet rewrite | One database transaction per replacing write |
| Published results | Overwritten | Every run kept; public page shows the latest |
| Prerequisites | Not modelled | Professors declare them; warnings in portal and CLI, never enforced |
| Completed courses | Not modelled | Parsed from the student's PDF grade card (U, W, I not completed); `admin` source reserved |
| CLI options | 4 | 7 (`--prerequisites`, `--completed-courses`, `--no-prerequisite-check`); unknown options now an error |
| Java tests (Maven) | 52 | 75 (23 new, incl. 4 stress) |
| Portal tests | 7 (mocked Apps Script) | 57 unit/contract (in-memory Postgres) + 4 integration + 14 stress (real Postgres 16) |
| Man page | none | [`man/slotbook.1`](../man/slotbook.1), rendered in [`slotbook.1.txt`](../man/slotbook.1.txt) |

The solver input, model and objective are unchanged: prerequisite data is kept outside `TimetableProblem`, and a test checks that the solver receives identical input with and without it.

## Documents

Start here for an overview: [DESIGN.md](../DESIGN.md) (the design and its rationale) and [FUNCTIONALITY_AND_PROGRESS.md](../FUNCTIONALITY_AND_PROGRESS.md) (features by status and the project timeline).

New or changed for v3:

| Document | Contents |
|---|---|
| [MANUAL.md](MANUAL.md) | Name, synopsis, description, every CLI option and portal setting, inputs, outputs, examples, limitations |
| [`slotbook(1)`](../man/slotbook.1.txt) | The same CLI reference as a man page (`man ./man/slotbook.1`) |
| [ABOUT.md](ABOUT.md) | What the tool is for, what v3 adds, and what is and is not trusted |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Portal layers, schema, the prerequisite flow and the extension point for admin records |
| [TEST_PLAN.md](TEST_PLAN.md) | **Every** test, by suite and file, with the command that runs it |
| [VERIFICATION.md](VERIFICATION.md) | What was run for v3, where, and the results |
| [DEBUGGING.md](DEBUGGING.md) | Faults found while building v3 and how each was isolated |
| [PRINCIPLES_AND_PRACTICES.md](PRINCIPLES_AND_PRACTICES.md) | Prompt engineering, AI-use rules, version control and testing as applied in v3 |
| [FILE_STRUCTURE.md](FILE_STRUCTURE.md) | Files added, changed and removed in v3 |

Unchanged from v2, still accurate: [code walkthrough](../docs_v2/CODE_WALKTHROUGH.md) (UniTime mapping), [solver comparison](../docs_v2/TEST_PLAN.md#solver-comparison), [live-modification practice](../docs_v2/LIVE_MODIFICATION_PRACTICE.md), [mid-evaluation pack](../docs_v2/mid_evaluation/README.md).

Also: [portal README](../portal/README.md) (Neon setup, deployment, prerequisite rules), [prompt log](../prompts/PROMPT_LOG.md), [prompt review checklist](../prompts/PROMPT_REVIEW_CHECKLIST.md).

## Quick start

Engine (JDK 17+, Maven 3.9+):

```bash
mvn clean test
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"
mvn exec:java -Dexec.args="--input data/prerequisite-warnings --output output/prereq"
man ./man/slotbook.1
```

Portal (Node 22+):

```bash
cd portal
npm install
npm test                      # 57 tests, no network or database needed
TEST_DATABASE_URL=postgres://... npm run test:stress       # 14 stress tests
TEST_DATABASE_URL=postgres://... npm run test:integration   # 4 tests on a throwaway Postgres
npm run check
DATABASE_URL=postgres://... npm run db:migrate
DATABASE_URL=postgres://... ADMIN_PASSWORD=dev npm run dev  # http://localhost:4173
```
