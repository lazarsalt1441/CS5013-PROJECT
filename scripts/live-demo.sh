#!/bin/sh
# Live demo: merge what was just submitted on the portal into data/feasible
# (the dataset the portal's course catalog comes from) and solve it.
#
#   1. On the admin page, download student_preferences.csv and
#      instructor_preferences.csv (and, optionally, prerequisites.csv and
#      completed_courses.csv).
#   2. Run:  sh scripts/live-demo.sh [DOWNLOADS_DIR]     (default ~/Downloads)
#   3. Publish output/live/generated_slotbook.csv and
#      output/live/assignment_explanations.csv from the admin page.
#
# Portal rows replace the dataset's rows for the same student or instructor,
# so a professor who resubmits overrides that professor's sample preferences.
set -e
DL="${1:-$HOME/Downloads}"
BASE=data/feasible
OUT=demo-live

latest() { ls -t "$DL"/"$1"*.csv 2>/dev/null | head -n 1; }

merge() { # $1 = file name, $2 = downloaded copy
  awk -F, '
    FNR == 1 { file++ }
    file == 1 { if (FNR > 1) { key = $1; gsub(/"/, "", key); seen[key] = 1 } next }
    FNR == 1 { print; next }
    /^[[:space:]]*$/ { next }
    { key = $1; gsub(/"/, "", key); if (!(key in seen)) print }
  ' "$2" "$BASE/$1" > "$OUT/$1"
  tail -n +2 "$2" | grep -v '^[[:space:]]*$' >> "$OUT/$1" || true
  echo "  $1: $(($(tail -n +2 "$2" | grep -c . || true))) portal rows merged"
}

STUDENTS=$(latest student_preferences)
INSTRUCTORS=$(latest instructor_preferences)
if [ -z "$STUDENTS" ] && [ -z "$INSTRUCTORS" ]; then
  echo "No student_preferences*.csv or instructor_preferences*.csv in $DL."
  echo "Download them from the admin page first, or pass the folder: sh scripts/live-demo.sh /path/to/folder"
  exit 1
fi

rm -rf "$OUT"; cp -R "$BASE" "$OUT"; rm -rf "$OUT/generated"
echo "Merging portal submissions from $DL into $OUT/"
[ -n "$STUDENTS" ] && merge student_preferences.csv "$STUDENTS"
[ -n "$INSTRUCTORS" ] && merge instructor_preferences.csv "$INSTRUCTORS"

ARGS="--input $OUT --output output/live"
PRE=$(latest prerequisites); DONE=$(latest completed_courses)
if [ -n "$PRE" ] && [ -n "$DONE" ]; then ARGS="$ARGS --prerequisites $PRE --completed-courses $DONE"; fi

echo "Solving: $ARGS"
mvn -q exec:java -Dexec.args="$ARGS"
echo
echo "Publish these two files from the admin page (section 3):"
echo "  $(pwd)/output/live/generated_slotbook.csv"
echo "  $(pwd)/output/live/assignment_explanations.csv"
