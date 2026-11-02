"""Generates the synthetic grade-card PDFs used by the portal tests.

The layout copies the IITM grade-card PDF (column order and positions) but
every name, roll number and grade is invented. Real grade cards must never be
committed. Re-run after changing the rows:

    python3 portal/tests/fixtures/make_grade_cards.py
"""
from pathlib import Path

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

HERE = Path(__file__).parent
# x positions of: term, course, title, credit, grade, attendance, year
COLUMNS = (31, 85, 141, 441, 471, 509, 552)

ROWS = [
    ("01", "EE1100", "Basic Electrical Engineering", "10", "A", "VG", "2023"),
    ("01", "MA1101", "Functions of Several Variables", "10", "B", "G", "2023"),
    ("01", "GN1101", "Life Skills 1", "0", "P", "VG", "2023"),
    ("02", "EE1101", "Signals and Systems", "10", "U", "G", "2024"),       # failed
    ("02", "EE2001", "Digital Systems & Lab", "16", "W", "G", "2024"),     # withdrawn
    ("02", "MA1102", "Series and Matrices", "10", "I", "G", "2024"),       # incomplete
    ("03", "EE1101", "Signals and Systems", "10", "C", "VG", "2024"),      # retake, passed
    ("03", "EE2015", "Electric Circuits & Networks", "11", "S", "G", "2024"),
    ("04", "EE3110", "Probability Foundations for Electrical Engineers", "12", "D", "G", "2025"),
    ("04", "EE2004", "Digital Signal Processing", "11", "E", "VG", "2025"),
]


def header(pdf, page, pages):
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawString(200, 800, "INDIAN INSTITUTE OF TECHNOLOGY MADRAS")
    pdf.setFont("Helvetica", 9)
    pdf.drawString(31, 770, "Roll No: XX00Z999")
    pdf.drawString(160, 770, "Name: TEST STUDENT (SYNTHETIC)")
    pdf.drawString(31, 758, "Department: Electrical Engineering")
    pdf.drawString(520, 40, f"Page {page} of {pages}")


def grade_card(path):
    pdf = canvas.Canvas(str(path), pagesize=A4)
    header(pdf, 1, 2)
    pdf.setFont("Helvetica-Bold", 8)
    for x, label in zip(COLUMNS, ("Term No", "Course", "Title", "Credit", "Grade", "Attendance", "Year Of")):
        pdf.drawString(x - 10 if label == "Term No" else x, 697, label)
    pdf.drawString(552, 689, "Passing")
    pdf.setFont("Helvetica", 8)
    y = 678
    for row in ROWS:
        for x, value in zip(COLUMNS, row):
            pdf.drawString(x, y, value)
        y -= 11
    pdf.showPage()
    header(pdf, 2, 2)
    pdf.drawString(300, 700, "*** End of Statement ***.")
    pdf.drawString(300, 680, "Earned Credits : 80")
    pdf.save()


def no_text(path):
    """A PDF with no text layer, like a scanned or photographed card."""
    pdf = canvas.Canvas(str(path), pagesize=A4)
    pdf.rect(50, 50, 400, 600)
    pdf.save()


if __name__ == "__main__":
    grade_card(HERE / "synthetic_grade_card.pdf")
    no_text(HERE / "no_text_layer.pdf")
    print("wrote synthetic_grade_card.pdf and no_text_layer.pdf")
