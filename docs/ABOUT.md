# About the Tool

## Problem

IITM slot labels represent recurring weekly meeting patterns. Creating a departmental slotbook requires administrators to reconcile fixed course placements, curriculum clashes, instructor availability, historical stability, and student demand. Spreadsheet-based construction makes the impact of a change difficult to trace.

## Product

IITM Explainable Slotbook is a standalone Java demonstrator for the EE course-to-slot stage. It converts explicit constraints into a CP-SAT optimization model and exposes the evidence behind each result.

## What “explainable” means here

The optimizer is mathematical rather than learned, but a raw objective value still does not answer an administrator's operational questions. The tool therefore preserves structured evidence:

- whether a slot was fixed or merely preferred;
- which hard conflicts ruled out alternatives;
- the instructor penalty attached to each slot;
- the stability cost of moving from the historical assignment;
- the weighted student co-demand affected by a placement.

An explanation is assembled only from this evidence. No generative model participates in optimization or invents causal reasons.

## Data provenance

Course names, historical slots, broad course categories, instructors, rooms, and capacities are adapted into a small demonstration subset from a supplied EE slotbook. Student IDs, selections, instructor penalties, allowed-slot domains, and some conflict relations are synthetic. The distinction is documented so a successful demo is not misrepresented as validation on complete institutional data.
