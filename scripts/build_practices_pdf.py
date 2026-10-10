from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate,
    Frame,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "principles_and_practices.pdf"

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="DocTitle", parent=styles["Title"], fontName="Helvetica-Bold",
    fontSize=20, leading=24, textColor=colors.HexColor("#243447"),
    alignment=TA_CENTER, spaceAfter=10,
))
styles.add(ParagraphStyle(
    name="Section", parent=styles["Heading2"], fontName="Helvetica-Bold",
    fontSize=13, leading=16, textColor=colors.HexColor("#1F5D6B"),
    spaceBefore=7, spaceAfter=5,
))
styles.add(ParagraphStyle(
    name="BodySmall", parent=styles["BodyText"], fontName="Helvetica",
    fontSize=9.2, leading=12.2, textColor=colors.HexColor("#263238"),
    spaceAfter=5,
))
styles.add(ParagraphStyle(
    name="Prompt", parent=styles["BodyText"], fontName="Helvetica",
    fontSize=8.4, leading=11.2, leftIndent=8, rightIndent=8,
    borderColor=colors.HexColor("#D7C9A5"), borderWidth=0.8,
    borderPadding=7, backColor=colors.HexColor("#FBF7EE"), spaceAfter=7,
))


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(colors.HexColor("#B0BEC5"))
    canvas.line(18 * mm, 14 * mm, 192 * mm, 14 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#607D8B"))
    canvas.drawString(18 * mm, 9 * mm, "CS5013 - IITM Explainable Slotbook")
    canvas.drawRightString(192 * mm, 9 * mm, f"Page {doc.page}")
    canvas.restoreState()


doc = BaseDocTemplate(
    str(OUTPUT), pagesize=A4,
    leftMargin=18 * mm, rightMargin=18 * mm,
    topMargin=16 * mm, bottomMargin=18 * mm,
    title="Engineering Principles and Practices",
    author="Nishanth Senthil Kumar and Sparsh Gupta",
)
frame = Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="body")
doc.addPageTemplates(PageTemplate(id="main", frames=[frame], onPage=footer))

story = []
story.append(Paragraph("Engineering Principles &amp; Practices", styles["DocTitle"]))
story.append(Paragraph("IITM Explainable Slotbook | Nishanth Senthil Kumar (EE23B049) &amp; Sparsh Gupta (EE23B117)", styles["BodySmall"]))
story.append(Spacer(1, 4))
story.append(Paragraph("Project lifecycle", styles["Section"]))
story.append(Paragraph(
    "Our project converts IITM-style course, slot, conflict, instructor-preference, and synthetic student-demand data into an optimized slotbook. The lifecycle is deliberately traceable: reviewed prompt -> small implementation change -> automated verification -> documentation update -> Git commit. The solver produces structured evidence, and explanations are derived from that evidence rather than generated independently.",
    styles["BodySmall"],
))
story.append(Paragraph("1. Prompt engineering", styles["Section"]))
story.append(Paragraph(
    "We treat prompts as versioned engineering inputs. Every substantial task is framed with <b>CoSTAR</b>: Context identifies the current architecture and data assumptions; Objective states one testable outcome; Style constrains implementation structure; Tone controls the level of certainty; Audience names the evaluator, stakeholder, or maintainer; Response specifies concrete deliverables. Each prompt also contains acceptance criteria and explicit negative instructions.",
    styles["BodySmall"],
))
prompt_table = Table([
    ["Review gate", "Question asked before accepting output"],
    ["Specificity", "Are files, interfaces, inputs, outputs, and scope named?"],
    ["Assumptions", "Are unknown institutional facts labelled or requested?"],
    ["Safety/privacy", "Does the output exclude identities, secrets, and fabricated data?"],
    ["Architecture", "Does the change respect parser/validator/solver/UI boundaries?"],
    ["Verification", "Is there a test or observable acceptance command?"],
], colWidths=[38 * mm, 132 * mm])
prompt_table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#315C6B")),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTNAME", (0, 1), (0, -1), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 8.2),
    ("LEADING", (0, 0), (-1, -1), 10.5),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B0BEC5")),
    ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#F5F7F8")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
]))
story.append(prompt_table)
story.append(Spacer(1, 6))
story.append(Paragraph(
    "Representative negative instructions include: do not represent synthetic preferences as real IITM data; do not let an LLM decide or justify slots; do not ignore unknown identifiers; do not place optimization logic in JavaFX controllers; and do not claim global optimality unless CP-SAT returns OPTIMAL.",
    styles["BodySmall"],
))

story.append(PageBreak())
story.append(Paragraph("2. Structured debugging", styles["Section"]))
story.append(Paragraph(
    "We use an eight-step cycle: <b>reproduce, observe, hypothesize, isolate, correct, test, regress, document</b>. Failures are first reduced to one command or minimal CSV fixture. We collect the input row, exception or solver status, expected result, and relevant module. Competing hypotheses are listed before code is edited. The smallest correction is made at the module that owns the behaviour, a regression assertion is added, and then the complete suite is rerun.",
    styles["BodySmall"],
))
debug_table = Table([
    ["Observed", "Exporter test failed although both CSV files existed."],
    ["Hypotheses", "Missing output; empty explanations; expectation aimed at the wrong formatter."],
    ["Evidence", "CSV contained course IDs and alternative descriptions, but not the UI heading."],
    ["Root cause", "Test expected text-view presentation inside structured CSV output."],
    ["Correction", "Assert the semantic marker 'rejected because'."],
    ["Regression", "All 11 tests rerun and passed."],
], colWidths=[30 * mm, 140 * mm])
debug_table.setStyle(TableStyle([
    ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 8.4),
    ("LEADING", (0, 0), (-1, -1), 11),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B0BEC5")),
    ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#E9F1F3")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
]))
story.append(debug_table)
story.append(Paragraph("3. Version control", styles["Section"]))
story.append(Paragraph(
    "Commits are coherent, reviewable increments rather than end-of-project snapshots. The initial sequence separates architecture and prompt governance, mock data, the solver/application, and evaluator documentation. Before committing we inspect the diff and run the relevant tests. Generated outputs, build directories, IDE settings, and secrets are ignored. Prompt files live under <font name='Courier'>prompts/</font> and are committed with the code they influenced, preserving revisions, rejected assumptions, debugging evidence, and verification results.",
    styles["BodySmall"],
))
commits = Table([
    ["Increment", "Evidence committed"],
    ["Architecture", "Maven skeleton, module flow, prompt checklist"],
    ["Data", "Feasible and infeasible fixtures, 30 synthetic preference profiles"],
    ["Application", "Parser, validator, CP-SAT model, explanations, CLI, UI, tests"],
    ["Documentation", "Manual, code walkthrough, test plan, debugging guide, presentation"],
], colWidths=[38 * mm, 132 * mm])
commits.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#315C6B")),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
    ("FONTSIZE", (0, 0), (-1, -1), 8.2),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B0BEC5")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("TOPPADDING", (0, 0), (-1, -1), 4),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
]))
story.append(commits)

story.append(PageBreak())
story.append(Paragraph("4. Testing, refactoring, and documentation", styles["Section"]))
story.append(Paragraph(
    "Tests target guarantees rather than implementation details. They cover quoted CSV parsing, fixture loading, student co-demand aggregation, valid and contradictory inputs, complete assignment, allowed and fixed slots, every hard-conflict pair, explanation completeness, infeasibility, and both exports. Because several timetables may be equally optimal, tests assert invariants instead of one fragile assignment. The JavaFX interface has a separate manual smoke checklist for the headless test environment.",
    styles["BodySmall"],
))
story.append(Paragraph(
    "Refactoring is supported by one-way dependencies: immutable model records; CSV translation; deterministic validation; CP-SAT formulation; evidence-based explanation; an application service; and thin CLI/UI adapters. This means a live change such as a new validation rule can be implemented in one class with one focused test. Public concepts have JavaDoc. The repository also includes a man-page-style manual with NAME, SYNOPSIS, DESCRIPTION, OPTIONS, INPUTS, OUTPUTS, EXAMPLES, EXIT STATUS, and LIMITATIONS; plus architecture, mathematical walkthrough, test plan, debugging guide, and a two-minute script.",
    styles["BodySmall"],
))
story.append(Paragraph("Appendix A - sample reviewed prompts", styles["Section"]))
story.append(Paragraph(
    "<b>Architecture prompt.</b> <b>Context:</b> Java 17 IITM slotting application for an individual viva. <b>Objective:</b> define modular boundaries for parsing, validation, optimization, explanation, UI, export, and testing. <b>Response:</b> repository structure and verification plan before implementation. <b>Negative constraints:</b> do not hide solver logic in controllers; do not claim mock inputs are real; do not use an LLM to invent reasons.",
    styles["Prompt"],
))
story.append(Paragraph(
    "<b>Testing prompt.</b> <b>Context:</b> parser, validator, CP-SAT solver, explanation builder, exporter, CLI, and UI exist. <b>Objective:</b> test observable guarantees and important failure paths. <b>Response:</b> JUnit tests for parsing, validation, completeness, domains, fixed slots, conflicts, explanations, infeasibility, and exports. <b>Negative constraints:</b> do not assert one exact optimum; do not test private variable names; do not weaken validation to make tests pass.",
    styles["Prompt"],
))
story.append(Paragraph(
    "The complete CoSTAR prompts, prompt reviews, acceptance decisions, negative prompts, and debugging records are versioned in <font name='Courier'>prompts/PROMPT_LOG.md</font>. This keeps the three-page submission concise while preserving a full auditable history in Git.",
    styles["BodySmall"],
))

doc.build(story)
print(OUTPUT)
