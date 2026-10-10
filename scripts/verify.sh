#!/usr/bin/env bash
set -euo pipefail

mvn clean test
mvn -q exec:java -Dexec.args="--input data/feasible --output output/demo"
mvn -q exec:java -Dexec.args="--input data/feasible --output output/demo-cpsat --solver cpsat"

echo "Verification complete: tests passed and demonstration exports were generated."

