# Two-Minute System Walkthrough

**0:00-0:20 - Problem**

“The Academic Section and departments construct slotbooks while balancing fixed courses, curriculum clashes, instructor preferences, historical assignments, and student demand. The current process is spreadsheet-heavy, so the consequences of moving one course are difficult to trace.”

**0:20-0:45 - Inputs**

“Our demonstrator imports five CSV files: courses, institutional slots, protected course pairs, instructor preferences, and anonymized or synthetic student selections. Before optimization, a validator rejects unknown references, invalid domains, and direct contradictions.”

**0:45-1:15 - Solver**

“Each course-slot pair becomes a Boolean CP-SAT variable. Hard constraints enforce exactly one allowed slot, fixed assignments, protected course separation, and instructor non-overlap. The objective minimizes faculty penalties, unnecessary movement from the historical slot, and weighted clashes between courses requested by the same students.”

**1:15-1:40 - Explainability**

“The solver returns structured evidence. For each assigned course we show whether it was fixed, the penalties it incurred, and why every alternative is either invalid or more costly. These explanations are deterministic and traceable; an LLM does not invent them.”

**1:40-2:00 - Engineering process**

“The parser, validator, solver, explanation layer, UI, and exporter are separated, which makes live changes local. We have 11 automated tests, feasible and infeasible fixtures, and a prompt log where every AI-assisted change records its CoSTAR prompt, negative constraints, human review, verification, and associated commit.”
