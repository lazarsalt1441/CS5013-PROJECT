-- 002: prerequisite rules declared by professors and completed courses
-- recorded for students.
--
-- completed_courses.source says where a completion came from. v3 writes
-- only 'grade_card' (parsed from a student's own upload, unverified).
-- 'admin' is reserved for records supplied by the academic section later;
-- adding that source needs a new CompletionSource in api/_completions.js,
-- not a schema change.

CREATE TABLE prerequisites (
    course_id         VARCHAR(30)  NOT NULL,
    prereq_course_id  VARCHAR(30)  NOT NULL,
    declared_by       VARCHAR(80)  NOT NULL,
    declared_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (course_id, prereq_course_id),
    CHECK (course_id <> prereq_course_id)
);

CREATE TABLE completed_courses (
    student_id   VARCHAR(30)  NOT NULL,
    course_id    VARCHAR(30)  NOT NULL,
    grade        VARCHAR(4)   NOT NULL,
    term         VARCHAR(10)  NOT NULL DEFAULT '',
    source       VARCHAR(20)  NOT NULL CHECK (source IN ('grade_card', 'admin')),
    recorded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (student_id, course_id, source)
);
