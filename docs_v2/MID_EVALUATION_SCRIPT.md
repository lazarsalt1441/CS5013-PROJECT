# Two-Minute System Walkthrough (v2)

**0:00-0:20 - Problem**

"The Academic Section and departments build slotbooks while balancing fixed courses, curriculum clashes, instructor preferences, rooms, labs, historical slots and student demand. The current process is spreadsheet-heavy, so the consequences of moving one course are hard to trace."

**0:20-0:45 - Inputs**

"We import five CSV files: courses, the official IITM slot grid with real meeting times, protected course pairs, instructor preferences, and anonymised or synthetic student selections. Before solving, a validator runs eighteen checks, such as unknown references, lab slots used as lectures, duplicate preferences and labs that collide."

**0:45-1:15 - Solver**

"Version 2 is a wrapper around UniTime, an open-source university timetabling system whose solver has won International Timetabling Competition tracks. We translate each course into a UniTime class, each slotbook rule into a UniTime constraint, and our penalties into UniTime's criteria, so UniTime's score equals ours exactly. Its local search then improves the timetable until the time limit. We kept our original CP-SAT model as an exact reference. Across our test scenarios UniTime matches the proven optimum in ten of thirteen cases. On the largest one it beats CP-SAT's time-limited answer, which is why we keep both solvers."

**1:15-1:40 - Explainability**

"Explanations come from the input and the final timetable, not from the solver's internals, so they work the same for both solvers. For each course we show whether it was fixed, the penalties it incurred, and why every other slot is invalid or more costly. No LLM invents them."

**1:40-2:00 - Engineering process**

"The layers are separated, and one shared class defines overlap and another defines cost, so the solvers, explanations and test oracles cannot disagree. We have 52 automated tests. Every generated scenario is solved by both solvers and checked against an independent penalty calculator and a brute-force optimum. Each AI-assisted change is recorded in the prompt log with its CoSTAR prompt, review and commit."
