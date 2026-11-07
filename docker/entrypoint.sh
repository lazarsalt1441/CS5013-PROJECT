#!/bin/sh
# Container entry point.
#   docker run IMAGE [CLI options]        the slotbook CLI (default: solve data/feasible)
#   docker run IMAGE serve                the solver web service for the portal (P020)
#   SLOTBOOK_SERVE=1 docker run IMAGE     the same, for hosts that only let you set variables
set -e
CP="/app/app.jar:/app/lib/*"
if [ "$1" = "serve" ] || [ -n "$SLOTBOOK_SERVE" ]; then
  # Free hosts give about 512 MB; let the heap use most of it.
  exec java -XX:MaxRAMPercentage=75 -cp "$CP" in.ac.iitm.slotbook.server.SolverServer
fi
exec java -cp "$CP" in.ac.iitm.slotbook.cli.SlotbookCli "$@"
