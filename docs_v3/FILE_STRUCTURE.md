# File Structure (v3)

Only files added, changed or removed in v3 are listed. The full v2 tree is in [docs_v2/FILE_STRUCTURE.md](../docs_v2/FILE_STRUCTURE.md).

```text
.
├── man/
│   ├── slotbook.1                    NEW  man page source (roff); `man ./man/slotbook.1`
│   └── slotbook.1.txt                NEW  plain-text rendering, diffed against the source in CI
├── docs_v3/                          NEW  this version's documentation
├── MANUAL.md                         CHANGED  now mirrors docs_v3/MANUAL.md
├── data/
│   └── prerequisite-warnings/        NEW  data/feasible + optional prerequisite inputs
│       ├── prerequisites.csv              4 rules
│       ├── completed_courses.csv          grade-card rows incl. U and W
│       └── expected_warnings.csv          golden file shared by Java and portal tests
├── src/main/java/in/ac/iitm/slotbook/
│   ├── model/
│   │   ├── Prerequisite.java         NEW
│   │   ├── CompletedCourse.java      NEW  isCompleted(): U, W, I are not
│   │   ├── PrerequisiteData.java     NEW  kept outside TimetableProblem
│   │   └── PrerequisiteWarning.java  NEW  codes shared with the portal
│   ├── io/
│   │   ├── PrerequisiteInputs.java   NEW  file locations, directory defaults, CLI overrides
│   │   ├── PrerequisiteLoader.java   NEW  reads the two optional CSVs
│   │   └── SlotbookExporter.java     CHANGED  writes prerequisite_warnings.csv when checked
│   ├── validation/
│   │   └── PrerequisiteChecker.java  NEW  advisory rule, mirrors api/_prerequisites.js
│   ├── service/
│   │   ├── SlotbookRun.java          CHANGED  + warnings, + checked flag; v2 constructor kept
│   │   └── SlotbookService.java      CHANGED  run(dir, PrerequisiteInputs)
│   └── cli/SlotbookCli.java          CHANGED  3 options; unknown options are errors
├── src/test/java/in/ac/iitm/slotbook/
│   ├── io/PrerequisiteLoaderTest.java              NEW  6 tests
│   ├── io/PrerequisiteWarningsDatasetTest.java     NEW  5 tests
│   └── validation/PrerequisiteCheckerTest.java     NEW  8 tests
├── portal/
│   ├── api/
│   │   ├── _db.js                    NEW  pg pool, TLS rule, transactions
│   │   ├── _store.js                 NEW  all SQL
│   │   ├── _validation.js            NEW  input rules (from Code.gs)
│   │   ├── _exports.js               NEW  CSV builders for the Java CLI
│   │   ├── _gradecard.js             NEW  PDF grade-card parser
│   │   ├── _prerequisites.js         NEW  advisory checker
│   │   ├── _completions.js           NEW  completion-source registry (extension point)
│   │   ├── _lib.js                   CHANGED  no Sheets bridge; status-aware errors
│   │   └── submit/admin/results/solver/health.js   CHANGED  use the store
│   ├── db/
│   │   ├── migrate.js                NEW  ordered, recorded, transactional migrations
│   │   └── migrations/001_initial_schema.sql, 002_prerequisites.sql   NEW
│   ├── google-apps-script/Code.gs    REMOVED  (in Git history)
│   ├── public/                       CHANGED  grade-card upload, prerequisite field, warnings panel
│   ├── scripts/
│   │   ├── migrate.mjs               NEW  npm run db:migrate
│   │   ├── dev-server.mjs            NEW  npm run dev
│   │   └── validate.mjs              CHANGED  migration, secret and fixture checks
│   ├── tests/
│   │   ├── helpers.mjs               NEW  fresh in-memory database per test
│   │   ├── validation/store/gradecard/prerequisites/parity.test.mjs   NEW
│   │   ├── api.test.mjs              REWRITTEN  database-backed
│   │   ├── integration/postgres.test.mjs   NEW  real Postgres only
│   │   └── fixtures/                 NEW  make_grade_cards.py + generated synthetic PDFs
│   ├── package.json, package-lock.json   CHANGED  pg, unpdf; pg-mem (dev)
│   └── .env.example                  CHANGED  DATABASE_URL replaces the Apps Script variables
├── .github/workflows/ci.yml          CHANGED  Postgres service, integration tests, man-page lint, prerequisite demo
└── prompts/PROMPT_LOG.md             CHANGED  P015
```
