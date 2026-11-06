# Third-party notices

This project, IITM Explainable Slotbook (CS5013, Nishanth S and Sparsh Gupta), uses the libraries below. The Docker image **redistributes** the Java runtime libraries, so it carries this file, the dependency list generated from each library's POM (`/app/licenses/THIRD-PARTY.txt`), and the licence texts (`/app/licenses/texts/`). The portal's npm packages run on Vercel and are not redistributed in the image.

The exact list for each build is generated, not hand-maintained. This file explains what the licences mean for this project.

## Java engine (in the Docker image)

| Library | Licence | Notes |
|---|---|---|
| UniTime cpsolver (`org.unitime:cpsolver`) | LGPL-3.0 | Used unmodified as a separate jar in `/app/lib/`, so it can be replaced; that is what the LGPL requires of a program that uses the library. |
| Google OR-Tools (`com.google.ortools:ortools-java` and its native jars) | Apache-2.0 | |
| JavaFX (`org.openjfx:javafx-*`) | GPL-2.0 with the Classpath Exception | The exception allows linking from a program under any licence. Only the desktop view uses it; the CLI in the image does not load it. |
| Transitive dependencies (JNA, protobuf, log4j, dom4j and others) | See `THIRD-PARTY.txt` | |

Test-only libraries (JUnit 5, jqwik: EPL-2.0) are not in the image.

## Portal (deployed on Vercel, not redistributed)

| Package | Licence |
|---|---|
| `pg` and its 12 dependencies | MIT, ISC |
| `unpdf` | MIT; bundles a build of Mozilla PDF.js (Apache-2.0) |
| `pg-mem` (tests only) | MIT |

Read from `portal/package-lock.json`; `npm audit` checks the same set in CI.

## Data

The IITM slot grid in `data/` and `portal/public/data/` is taken from the institute's published timetable. All course, student, instructor and grade-card data is synthetic.

## AI assistance

Much of this code was written with AI assistants and reviewed by the authors. Each task is recorded in `prompts/PROMPT_LOG.md`, and AI-co-authored commits carry a `Co-Authored-By` trailer. The authors are responsible for the code; no licensed third-party code was knowingly reproduced.
