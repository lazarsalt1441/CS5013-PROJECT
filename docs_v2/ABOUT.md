# About the Tool (v2)

## Problem

IITM slot labels represent recurring weekly meeting patterns. Creating a departmental slotbook requires administrators to reconcile fixed course placements, curriculum clashes, instructor availability, rooms, lab blocks, historical stability and student demand. Spreadsheet-based construction makes the impact of a change difficult to trace.

## Product

IITM Explainable Slotbook is a standalone Java demonstrator for the EE course-to-slot stage. Version 2 is a wrapper around UniTime, the open-source university timetabling system. It keeps the slotbook's own inputs, explanations, desktop app and exports, and hands the search to UniTime's solver engine, cpsolver. That engine was among the finalists in all three tracks of the International Timetabling Competition 2007, winning two, and was used to validate ITC 2019.

The wrapper translates the slotbook problem into UniTime's course timetabling model. Each course becomes a UniTime class whose possible times are its allowed IITM slots, and the slotbook's rules become UniTime's instructor, room, distribution and student constraints. The answer is translated back into slot letters. The v1 CP-SAT model is still available as an exact reference: it can prove optimality, which UniTime does not attempt.

## Why wrap UniTime instead of extending v1

- **Search that scales.** UniTime's local search keeps improving a feasible timetable instead of trying to prove optimality. On the 300-student test scenario it found a better timetable in about half a second than CP-SAT found in 10 seconds.
- **A proven model.** Room, instructor and student-conflict handling come from a system used for real university timetables.
- **A path to growth.** UniTime already supports rooms with capacities, distribution preferences such as back-to-back and precedence, and student sectioning, so later versions can adopt them without writing a new solver.

## What "explainable" means here

The optimiser is mathematical rather than learned, but a raw objective value still does not answer an administrator's operational questions. The tool therefore preserves structured evidence:

- whether a slot was fixed or merely preferred;
- which hard conflicts, instructor or room clashes, or lab overlaps ruled out alternatives;
- the instructor penalty attached to each slot;
- the stability cost of moving from the historical assignment;
- the weighted student co-demand affected by a placement.

An explanation is assembled only from this evidence and the final timetable, so it reads the same whichever solver produced the timetable. No generative model participates in optimisation or invents causal reasons.

## Data provenance

- **Adapted from a supplied EE slotbook:** course names, historical slots, broad course categories, instructors, rooms and capacities, reduced to a small demonstration subset.
- **Official slot grid:** slot days, start times and lengths follow <https://www.iitm.ac.in/timetable> (B.Tech other than first year, and M.A.).
- **Synthetic:** student IDs, selections, instructor penalties, allowed-slot sets and some conflict relations.

This is documented so that a successful demo is not mistaken for validation on complete institutional data.
