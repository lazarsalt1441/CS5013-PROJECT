-- 001: core tables that replace the Students, Instructors, Slotbook and
-- Explanations tabs of the v2 Google Sheet.
--
-- Rules that the Apps Script enforced in code are now database constraints,
-- so a bug in one API handler cannot write a row the solver would reject.

CREATE TABLE student_submissions (
    student_id    VARCHAR(30)  PRIMARY KEY,
    programme     VARCHAR(60)  NOT NULL,
    year          VARCHAR(20)  NOT NULL,
    submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE student_preferences (
    student_id  VARCHAR(30) NOT NULL REFERENCES student_submissions (student_id) ON DELETE CASCADE,
    course_id   VARCHAR(30) NOT NULL,
    rank        INTEGER     NOT NULL CHECK (rank BETWEEN 1 AND 10),
    PRIMARY KEY (student_id, course_id),
    UNIQUE (student_id, rank)
);

-- instructor_key is the lower-cased name, matching v2's case-insensitive
-- "resubmission replaces earlier rows" behaviour.
CREATE TABLE instructor_submissions (
    instructor_key  VARCHAR(80)  PRIMARY KEY,
    instructor      VARCHAR(80)  NOT NULL,
    course_id       VARCHAR(30),
    submitted_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE instructor_preferences (
    instructor_key  VARCHAR(80)  NOT NULL REFERENCES instructor_submissions (instructor_key) ON DELETE CASCADE,
    slot            VARCHAR(12)  NOT NULL,
    penalty         INTEGER      NOT NULL CHECK (penalty BETWEEN 0 AND 100),
    reason          VARCHAR(240) NOT NULL DEFAULT '',
    PRIMARY KEY (instructor_key, slot)
);

-- One row per publication. Public results always show the latest run, and
-- earlier runs are kept so a publication can be audited or rolled back.
CREATE TABLE published_runs (
    run_id        SERIAL       PRIMARY KEY,
    solver        VARCHAR(30)  NOT NULL,
    published_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE slotbook_rows (
    run_id          INTEGER       NOT NULL REFERENCES published_runs (run_id) ON DELETE CASCADE,
    course_id       VARCHAR(30)   NOT NULL,
    title           VARCHAR(160)  NOT NULL DEFAULT '',
    semester_group  VARCHAR(60)   NOT NULL DEFAULT '',
    course_type     VARCHAR(40)   NOT NULL DEFAULT '',
    instructor      VARCHAR(80)   NOT NULL DEFAULT '',
    assigned_slot   VARCHAR(20)   NOT NULL DEFAULT '',
    additional_slot VARCHAR(20)   NOT NULL DEFAULT '',
    room            VARCHAR(60)   NOT NULL DEFAULT '',
    class_size      INTEGER,
    PRIMARY KEY (run_id, course_id)
);

CREATE TABLE explanation_rows (
    run_id         INTEGER        NOT NULL REFERENCES published_runs (run_id) ON DELETE CASCADE,
    course_id      VARCHAR(30)    NOT NULL,
    assigned_slot  VARCHAR(20)    NOT NULL DEFAULT '',
    reasons        VARCHAR(5000)  NOT NULL DEFAULT '',
    alternatives   VARCHAR(10000) NOT NULL DEFAULT '',
    PRIMARY KEY (run_id, course_id)
);
