# IITM Explainable Slotbook

Builds the weekly course slotbook for an IITM department and explains every decision.

Each course gets one lecture slot from the institute's timetable grid. Hard rules are never broken: allowed and fixed slots, the course's own lab, protected course pairs, one class per instructor and one per room at a time. Within those rules the solver keeps three costs as low as possible: professors' slot penalties, moves away from last year's slot, and clashes between courses that students chose together. Every assignment comes with a written explanation: why this slot, and why not each of the others.

**CS5013 course project** by **Nishanth S (EE23B049)** and **Sparsh Gupta (EE23B117)**. Stakeholder: Meghana Nasre, in charge of the slotbook, CS Department.

## Live deployment

| What | Where |
|---|---|
| Preference portal (students and professors) | https://cs5013-project-portal.vercel.app |
| Published slotbook with explanations | https://cs5013-project-portal.vercel.app/results |
| Admin dashboard (password protected) | https://cs5013-project-portal.vercel.app/admin |
| Solver web service, used by "Generate slotbook" | https://slotbook-solver.onrender.com/health |

The solver service runs on a free plan that sleeps after about 15 minutes idle. Open its `/health` link first and allow up to a minute. If it is unavailable, the manual route works without it: download the CSVs from the admin page, run `sh scripts/live-demo.sh`, and upload the two output files.

## How it works

```
 Students ──┐                                   ┌──> Results page
 Professors ┼──> Portal (Vercel) ──> Postgres ──┤
 Admin ─────┘        │                (Neon)    └──> CSV exports
                     │ "Generate slotbook"
                     v
            Solver service (Docker on Render)
            = the Java engine: validate -> solve -> explain
```

- **Engine** (Java 17, `src/`): reads five CSV files, checks them with 18 validation rules, solves, and writes the slotbook and explanations. UniTime's [cpsolver](https://github.com/UniTime/cpsolver) is the default solver; an exact OR-Tools CP-SAT model is kept as a reference that proves optimality on small inputs.
- **Explanations** (`explain/`): rule-based, no AI. They are built from the input and the final assignment only, so they read the same whichever solver ran.
- **Portal** (`portal/`, Node 22 on Vercel, Postgres on Neon): students rank courses and upload their grade card; professors set slot penalties and declare prerequisites; the admin monitors submissions, exports CSVs and publishes results.
- **Prerequisite warnings:** a student who ranks a course without a passing grade in its prerequisite gets a warning. U, W and I grades do not count as passed. Warnings are advisory and never change what the solver receives. The portal and the CLI produce identical warnings, checked against a shared golden file.
- **Solver service** (`server/`): the same engine behind a small HTTP API, so the admin's button can solve and publish in one click.

## Quick start

### With Maven (JDK 17+, Maven 3.9+)

```bash
mvn clean test                                                            # 113 tests
mvn exec:java -Dexec.args="--input data/feasible --output output/demo"    # UniTime
mvn exec:java -Dexec.args="--input data/feasible --output output/cp --solver cpsat"
mvn exec:java -Dexec.args="--input data/demo-showcase --output output/show --time-limit 10"
mvn javafx:run                                                            # desktop view
man ./man/slotbook.1                                                      # all options
```

On `data/feasible`, both solvers reach objective 20. On the showcase dataset (11 courses, 36 students), UniTime reaches 9, the CP-SAT optimum.

### With Docker (no Java needed)

```bash
docker load -i slotbook-<sha>.tar.gz
docker run --rm slotbook:<sha>                                  # solves data/feasible
mkdir -p out && docker run --rm -u "$(id -u):$(id -g)" -v "$PWD/out:/out" \
    slotbook:<sha> --input data/demo-showcase --output /out     # CSVs land in ./out
docker run --rm -p 8080:8080 -e SOLVER_API_KEY=secret slotbook:<sha> serve   # web service
```

### The portal locally (Node 22, Postgres)

```bash
cd portal && npm ci && npm test
DATABASE_URL=postgres://localhost/slotbook npm run db:migrate
DATABASE_URL=postgres://localhost/slotbook ADMIN_PASSWORD=dev npm run dev   # http://localhost:4173
```

The [portal README](portal/README.md) covers Neon, Vercel and Render setup.

## The Docker image

GitHub Actions builds it on every push to `main` and on the `final-submission` tag. It tests the image, then saves it as a downloadable artifact named `slotbook-docker-<sha>`, kept for 90 days.

| In the image | Path |
|---|---|
| Java 17 runtime (Eclipse Temurin JRE, Ubuntu 22.04) | base image |
| The engine: CLI, both solvers, validation, explanations, solver service | `/app/app.jar` |
| Runtime libraries: cpsolver, OR-Tools with native code, Log4j, dom4j, JNA, protobuf, JavaFX | `/app/lib/` |
| Sample datasets: `feasible`, `demo-showcase`, `prerequisite-warnings`, `infeasible-fixed-conflict` | `/app/data/` |
| Man page, README, manual | `/app/man/`, `/app/` |
| Licence texts and list for all 16 runtime libraries | `/app/licenses/` |
| Start-up script: CLI by default, web service with `serve` or `SLOTBOOK_SERVE=1` | `/app/bin/slotbook` |

Not in the image:
- the web portal (it runs on Vercel);
- the database;
- source code, tests, and any secrets or `.env` files.

The image runs as a non-root user (`slotbook`, uid 10001), and its `org.opencontainers.image.revision` label records the commit it was built from. CI checks all of this, plus every command in the report, before saving the image ([workflow](.github/workflows/docker.yml)).

## Testing and quality

| | Java engine | Portal |
|---|---|---|
| Tests | 113 (JUnit 5, jqwik) | 57 unit, 4 integration on real Postgres, 14 stress |
| Coverage | 81.4% lines, 76.8% branches (JaCoCo) | 96.4% lines, 90.6% branches |
| Mutation testing (PIT) | 98.7% of mutants killed; the build fails below 97% | |
| Static analysis | SpotBugs: 0 findings; compiler warnings are errors | |
| Dependencies | Licences generated in CI | `npm audit`: 0 vulnerabilities |

The solvers are judged by independent oracles: a separate constraint checker, a penalty calculator and a brute-force solver. Property-based tests check the clash rules against the textbook definition of overlapping intervals. Every fault found is recorded with reproduction steps and a regression test in [DEBUGGING.md](docs_v3/DEBUGGING.md). Details are in the [test plan](docs_v3/TEST_PLAN.md).

CI ([workflows](.github/workflows)) runs on every push and pull request:
- both test suites, with a Postgres service;
- CLI runs with both solvers;
- the prerequisite golden file;
- a man-page lint;
- the quality job: coverage, SpotBugs, PIT, licences and `npm audit`;
- the Docker build and checks.

## Repository layout

| Path | Contents |
|---|---|
| `src/main/java/in/ac/iitm/slotbook/` | `model`, `io`, `validation`, `solver` (UniTime and CP-SAT), `explain`, `service`, `cli`, `ui` (JavaFX), `server` (web service) |
| `src/test/java/` | Unit, property and end-to-end tests; `testing/` holds the independent oracles |
| `data/` | Sample datasets (synthetic students and preferences; times from the [official IITM grid](https://www.iitm.ac.in/timetable)) |
| `portal/` | Vercel portal: `api/`, `public/`, `db/migrations/`, `tests/` |
| `scripts/live-demo.sh` | Merges portal downloads into `data/feasible` and solves (manual route) |
| `man/`, `MANUAL.md` | `slotbook(1)` and the user manual |
| `prompts/` | Prompt log (P001–P020) and review checklist |
| `docs_v1/`, `docs_v2/`, `docs_v3/`, `docs_design/` | Documentation for each version and the design document |

## Documentation

- **Using it:** [MANUAL.md](MANUAL.md), [`slotbook(1)`](man/slotbook.1.txt), [portal README](portal/README.md).
- **Design and status:**
  - [DESIGN.md](DESIGN.md)
  - [FUNCTIONALITY_AND_PROGRESS.md](FUNCTIONALITY_AND_PROGRESS.md)
  - [Architecture](docs_v3/ARCHITECTURE.md)
  - [About the problem](docs_v3/ABOUT.md)
- **Evidence:**
  - [Test plan](docs_v3/TEST_PLAN.md)
  - [Verification record](docs_v3/VERIFICATION.md)
  - [Debugging log](docs_v3/DEBUGGING.md)
  - [Solver comparison](docs_v2/TEST_PLAN.md#solver-comparison)
- **AI-assisted development:**
  - [Prompt log](prompts/PROMPT_LOG.md), with each prompt in CoSTAR form plus negative prompts, review and acceptance
  - [Review checklist](prompts/PROMPT_REVIEW_CHECKLIST.md)
  - [Principles and practices](docs_v3/PRINCIPLES_AND_PRACTICES.md)
- **Earlier versions:** [docs_v2](docs_v2/README.md) (UniTime wrapper, code walkthrough, mid-evaluation script), [docs_v1](docs_v1/README.md) (original CP-SAT version).

## Version history

| Version | What it added |
|---|---|
| v1 | CP-SAT model, explanations, CLI, JavaFX view, test oracles and generated scenarios, CI |
| v2 | UniTime cpsolver as the default solver; real IITM meeting times, rooms and labs; 18 validation checks; preference portal on Google Sheets |
| v3 | Postgres on Neon; grade-card prerequisite warnings; man page; stress, property-based and mutation testing; static analysis; Docker image; one-click solver service |

## Limitations

- **Heuristic default solver:** UniTime finished 1–8 points above the CP-SAT optimum on 3 of 13 generated scenarios. Use `--solver cpsat` when proof of optimality matters.
- **No login on the portal:** grade cards are self-uploaded and unverified. Do not use it with real student data until IITM login is added.
- **Rooms:** each course's room is fixed and capacity is not modelled.
- **Grade cards:** only the text layout of the IITM PDF is read; scanned cards are rejected.

## Licence

The engine uses UniTime cpsolver (LGPL-3.0) unmodified, as a separate replaceable library, and OR-Tools (Apache-2.0). All 16 runtime dependencies and their licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
