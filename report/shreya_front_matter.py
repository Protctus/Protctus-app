"""
Front Matter: Title Page, Certificate, Declaration, Acknowledgement, Abstract, TOC
For Shreya Bhadouriya (Quality Control: Documentation, Presentation & UI Aesthetics Review)
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import *


def add_title_page(doc):
    for _ in range(4):
        doc.add_paragraph("")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("A PERSONAL CONTRIBUTION REPORT")
    run.font.size = Pt(14)
    run.bold = True
    run.font.color.rgb = TEXT_SECONDARY
    run.font.name = FONT_HEADING

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("on")
    run.font.size = Pt(12)
    run.font.color.rgb = TEXT_SECONDARY

    doc.add_paragraph("")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("PROTCTUS")
    run.font.size = Pt(36)
    run.bold = True
    run.font.color.rgb = PRIMARY_BLUE
    run.font.name = FONT_HEADING

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("Quality Control: Documentation, Presentation & UI Review")
    run.font.size = Pt(14)
    run.font.color.rgb = TEXT_PRIMARY
    run.font.name = FONT_HEADING

    doc.add_paragraph("")
    doc.add_paragraph("")

    lines = [
        ("Submitted in partial fulfillment of the requirements", False),
        ("for the award of the", False),
        ("Diploma in Computer Science & Engineering", True),
    ]
    for text, bold in lines:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(text)
        run.font.size = Pt(11)
        run.bold = bold
        run.font.name = FONT_BODY

    doc.add_paragraph("")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("Submitted By:")
    run.font.size = Pt(11)
    run.bold = True

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("SHREYA BHADOURIYA")
    run.font.size = Pt(14)
    run.bold = True
    run.font.color.rgb = PRIMARY_BLUE

    doc.add_paragraph("")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("Under the Guidance of:")
    run.font.size = Pt(11)
    run.bold = True

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("MRS. ASHA GRACE KERKETTA, HOD CSE")
    run.font.size = Pt(12)
    run.font.color.rgb = TEXT_SECONDARY

    doc.add_paragraph("")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("[Department of Computer Science & Engineering]")
    run.font.size = Pt(11)
    run.bold = True

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("Government Polytechnic College, Shivpuri")
    run.font.size = Pt(12)
    run.font.color.rgb = TEXT_SECONDARY

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run("Academic Year: 2025-2026")
    run.font.size = Pt(12)
    run.bold = True

    add_page_break(doc)


def add_certificate(doc):
    h = doc.add_heading('CERTIFICATE', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    add_styled_paragraph(doc,
        "This is to certify that the Personal Contribution Report entitled "
        "\"PROTCTUS - Quality Control: Documentation, Presentation & UI Review\" is a bonafide record of the individual project "
        "work carried out by SHREYA BHADOURIYA during the academic year 2025-2026 under my "
        "supervision and guidance, submitted in partial fulfillment of the requirements "
        "for the award of the Diploma in Computer Science & Engineering.",
        size=11, space_after=24
    )

    add_styled_paragraph(doc,
        "The work embodied in this report has not been submitted for the award of any "
        "other degree or diploma to the best of my knowledge and belief.",
        size=11, space_after=36
    )

    doc.add_paragraph("")
    doc.add_paragraph("")
    doc.add_paragraph("")

    p = doc.add_paragraph()
    run = p.add_run("_______________________\t\t\t\t_______________________")
    run.font.size = Pt(11)
    p = doc.add_paragraph()
    run = p.add_run("   Project Guide\t\t\t\t\t   Head of Department")
    run.font.size = Pt(10)
    run.bold = True

    doc.add_paragraph("")
    doc.add_paragraph("")

    p = doc.add_paragraph()
    run = p.add_run("_______________________")
    run.font.size = Pt(11)
    p = doc.add_paragraph()
    run = p.add_run("   External Examiner")
    run.font.size = Pt(10)
    run.bold = True

    add_page_break(doc)


def add_declaration(doc):
    h = doc.add_heading('DECLARATION', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    add_styled_paragraph(doc,
        "I hereby declare that the project report entitled \"PROTCTUS - Quality Control: Documentation, Presentation & UI Review\" "
        "submitted for the partial fulfillment of my diploma requirements represents my personal contribution "
        "to the Protctus mobile application. The methodologies regarding UX auditing, documentation peer review, "
        "and presentation optimization submitted within this report are authentic and have not been submitted "
        "elsewhere for any other degree or diploma.",
        size=11, space_after=12
    )

    add_styled_paragraph(doc,
        "As the lead for Quality Control across design and documentation, I personally audited the Jetpack Compose user interfaces, "
        "proofread and standardized the technical documentation (SRS, architecture diagrams), and conducted the final "
        "reviews for our academic presentation slides. The application code was written collaboratively, but the review methodologies "
        "and quality assurance standards outlined in this report are my individual work.",
        size=11, space_after=36
    )

    doc.add_paragraph("")
    doc.add_paragraph("")
    doc.add_paragraph("")
    doc.add_paragraph("")

    add_styled_paragraph(doc, "Place: _______________", size=11)
    add_styled_paragraph(doc, "Date:  _______________", size=11, space_after=24)
    doc.add_paragraph("")
    add_styled_paragraph(doc, "Signature: _______________________", size=11)
    add_styled_paragraph(doc, "Name: SHREYA BHADOURIYA", size=11, bold=True)

    add_page_break(doc)


def add_acknowledgement(doc):
    h = doc.add_heading('ACKNOWLEDGEMENT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "I would like to express my sincere gratitude to my project guide for their continuous support, valuable insights, and patient mentorship. Their guidance in understanding software quality assurance, UX design principles, and academic formatting standards has been invaluable.",

        "I extend my heartfelt thanks to the Head of the Department and the entire faculty of the Computer Science & Engineering Department for fostering an environment that emphasizes the importance of design thinking and rigorous peer review in software engineering.",

        "I am deeply grateful to my institution, Government Polytechnic College, Shivpuri, for providing the necessary resources to conduct my research and reviews effectively.",

        "I would also like to thank my teammates—Jatin, Vinayak, Swapnil, and Kunal—for their receptiveness to constructive criticism. Our continuous feedback loops ensured Protctus became a polished, accessible, and well-documented product.",

        "Finally, I extend my deepest appreciation to my family and friends for their unwavering encouragement and moral support."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=10)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "SHREYA BHADOURIYA", size=12, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT)
    add_page_break(doc)


def add_abstract(doc):
    h = doc.add_heading('ABSTRACT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "This Personal Contribution Report details my specific role in the creation of Protctus, an Android cyber-security application. In the modern software development lifecycle, a product's success is not determined solely by its code, but by how effectively it communicates with its end-users and stakeholders. My primary responsibility within the team was to act as the final gatekeeper for quality across three major domains: UI Aesthetics, Technical Documentation, and Academic Presentation.",

        "In software engineering, User Interface (UI) and User Experience (UX) are critical for user retention. I conducted comprehensive heuristic evaluations of the app's Jetpack Compose interfaces, ensuring high-contrast readability, intuitive navigation, and consistent visual hierarchy, particularly for the gamified Cyber Lab module.",

        "Furthermore, peer review is a cornerstone of academic and industry projects. I audited the team's technical documentation—including the Software Requirements Specification (SRS) and architecture diagrams—to ensure grammatical accuracy, logical flow, and strict adherence to IEEE formatting standards.",

        "Finally, I reviewed and optimized the project's Viva presentation, refining the slide deck for visual impact and coaching the team through demo rehearsals to ensure a cohesive, professional delivery. This report outlines my methodologies for UX auditing, documentation proofreading, and presentation optimization that collectively elevated the overall quality of the Protctus project."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=8)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "Keywords: ", bold=True, size=11, space_after=4)
    add_styled_paragraph(doc,
        "Quality Control, UI/UX Review, Heuristic Evaluation, "
        "Documentation Audit, Presentation Strategy, Peer Review",
        size=10, italic=True, color=TEXT_SECONDARY, space_after=12
    )
    add_page_break(doc)


def add_table_of_contents(doc):
    h = doc.add_heading('TABLE OF CONTENTS', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("")

    toc_entries = [
        ("", "Front Matter", ""),
        ("", "  Certificate", "ii"),
        ("", "  Declaration", "iii"),
        ("", "  Acknowledgement", "iv"),
        ("", "  Abstract", "v"),
        ("", "  Table of Contents", "vi"),
        ("", "  List of Tables", "vii"),
        ("1", "INTRODUCTION & MY REVIEW ROLE", "1"),
        ("", "  1.1 Project Motivation", "1"),
        ("", "  1.2 The Importance of Quality Control", "2"),
        ("", "  1.3 Overview of My Responsibilities", "3"),
        ("2", "UI AESTHETICS & UX REVIEW", "4"),
        ("", "  2.1 Heuristic Evaluation Methodology", "4"),
        ("", "  2.2 Auditing the Jetpack Compose Design", "5"),
        ("", "  2.3 Accessibility and Color Contrast Standards", "6"),
        ("3", "TECHNICAL DOCUMENTATION AUDIT", "7"),
        ("", "  3.1 Establishing Review Protocols", "7"),
        ("", "  3.2 Auditing the SRS and System Diagrams", "8"),
        ("", "  3.3 Standardizing the User Manual", "9"),
        ("4", "PRESENTATION & DEMO REHEARSAL REVIEW", "10"),
        ("", "  4.1 Reviewing the Viva Slide Deck", "10"),
        ("", "  4.2 Optimizing Data Visualization", "11"),
        ("", "  4.3 Rehearsal and Timing Coordination", "12"),
        ("5", "FEEDBACK LOOPS & TEAM ALIGNMENT", "13"),
        ("", "  5.1 Delivering Constructive Criticism", "13"),
        ("", "  5.2 Tracking Feedback Implementation", "14"),
        ("", "  5.3 Final Approval Gatekeeping", "15"),
        ("6", "CONCLUSION & LEARNING OUTCOMES", "17"),
        ("", "  6.1 Conclusion", "17"),
        ("", "  6.2 Personal Learnings in Quality Assurance", "18")
    ]

    for ch_num, title, page in toc_entries:
        p = doc.add_paragraph()
        is_chapter = ch_num != ""
        if is_chapter:
            run = p.add_run(f"Chapter {ch_num}:  {title}")
            run.bold = True
            run.font.size = Pt(11)
        else:
            run = p.add_run(title)
            run.font.size = Pt(10)
            run.font.color.rgb = TEXT_SECONDARY
        if page:
            tab_run = p.add_run(f"  {'.' * (50 - len(title))}  {page}")
            tab_run.font.size = Pt(9)
            tab_run.font.color.rgb = TEXT_SECONDARY

    add_page_break(doc)


def add_list_of_tables(doc):
    h = doc.add_heading('LIST OF TABLES', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("")

    tables = [
        ("1.1", "Detailed Breakdown of Quality Control Responsibilities"),
        ("2.1", "UI/UX Heuristic Evaluation Results"),
        ("2.2", "Accessibility and Contrast Adjustments"),
        ("3.1", "Documentation Audit Checklist"),
        ("4.1", "Presentation Optimization Matrix"),
        ("5.1", "Feedback Resolution Log"),
        ("6.1", "Quality Control Skill Matrix")
    ]
    for tbl_num, caption in tables:
        p = doc.add_paragraph()
        run = p.add_run(f"Table {tbl_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)
