"""
Front Matter: Title Page, Certificate, Declaration, Acknowledgement, Abstract, TOC
For Swapnil Chaurasiya (General Contributor: Coding, Testing, Documentation, Marketing)
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
    run = p.add_run("Project Documentation, Full-Stack Assistance & QA")
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
    run = p.add_run("SWAPNIL CHAURASIYA")
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
        "\"PROTCTUS - Project Documentation, Full-Stack Assistance & QA\" is a bonafide record of the individual project "
        "work carried out by SWAPNIL CHAURASIYA during the academic year 2025-2026 under my "
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
        "I hereby declare that the project report entitled \"PROTCTUS - Project Documentation, Full-Stack Assistance & QA\" "
        "submitted for the partial fulfillment of my diploma requirements represents my personal contribution "
        "to the Protctus mobile application. The information regarding coding assistance, testing execution, documentation strategies, "
        "and market research submitted within this report are authentic and have not been submitted "
        "elsewhere for any other degree or diploma.",
        size=11, space_after=12
    )

    add_styled_paragraph(doc,
        "As a core contributor across all domains of the project, I personally assisted in development sprints, wrote technical "
        "documentation, executed QA tests alongside the testing lead, and supported marketing initiatives. The application is a "
        "collaborative team effort, but the specific multidisciplinary tasks outlined in this report are my individual work.",
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
    add_styled_paragraph(doc, "Name: SWAPNIL CHAURASIYA", size=11, bold=True)

    add_page_break(doc)


def add_acknowledgement(doc):
    h = doc.add_heading('ACKNOWLEDGEMENT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "I would like to express my sincere gratitude to my project guide for their continuous support, valuable insights, and patient mentorship. Their guidance in understanding software project management and technical documentation has been invaluable.",

        "I extend my heartfelt thanks to the Head of the Department and the entire faculty of the Computer Science & Engineering Department for fostering an academic environment that encourages collaborative, industry-focused learning.",

        "I am deeply grateful to my institution, Government Polytechnic College, Shivpuri, for providing the necessary resources to conduct research, coding, and testing effectively.",

        "I would also like to thank my teammates, Jatin Sharma and Vinayak Chourasiya, for their relentless work. Our continuous collaboration across development, testing, and documentation ensured Protctus became a polished, reliable product.",

        "Finally, I extend my deepest appreciation to my family and friends for their unwavering encouragement and moral support."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=10)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "SWAPNIL CHAURASIYA", size=12, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT)
    add_page_break(doc)


def add_abstract(doc):
    h = doc.add_heading('ABSTRACT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "This Personal Contribution Report details my multi-disciplinary role in the creation of Protctus, a comprehensive Android cyber-security application. In modern software engineering, the success of an application relies not just on specialized developers, but on versatile contributors who bridge the gaps between coding, quality assurance, documentation, and product strategy. My role within the Protctus team encompassed exactly this broad, cross-functional spectrum.",

        "My primary objective was to facilitate the smooth execution of the project lifecycle. In the development phase, I assisted with frontend Jetpack Compose logic and Firebase integration debugging. In the Quality Assurance phase, I partnered with the QA lead to execute edge-case manual testing, specifically focusing on usability testing from a non-technical user's perspective.",

        "Furthermore, I took ownership of the project's technical documentation, ensuring that our codebase, architectural decisions, and API endpoints were thoroughly documented for future maintainability. I also actively participated in formulating the go-to-market strategy, gathering user feedback during beta testing, and refining our promotional messaging.",

        "This report outlines my cross-domain methodologies, the challenges encountered when balancing multiple roles, the specific coding and testing tasks I executed, and the documentation standards I enforced. My holistic contribution ensured that Protctus evolved from a raw codebase into a well-documented, rigorously tested, and market-ready application."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=8)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "Keywords: ", bold=True, size=11, space_after=4)
    add_styled_paragraph(doc,
        "Project Management, Technical Documentation, Software Engineering, "
        "Quality Assurance, Android Development, Cross-Functional Teamwork",
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
        ("1", "INTRODUCTION & MY MULTI-DISCIPLINARY ROLE", "1"),
        ("", "  1.1 Project Motivation", "1"),
        ("", "  1.2 The Need for a Cross-Functional Contributor", "2"),
        ("", "  1.3 Overview of My Responsibilities", "3"),
        ("2", "CODING & DEVELOPMENT CONTRIBUTIONS", "4"),
        ("", "  2.1 Pair Programming and Code Review", "4"),
        ("", "  2.2 Assisting with Jetpack Compose UI", "5"),
        ("", "  2.3 Firebase Database Integration Support", "6"),
        ("3", "QUALITY ASSURANCE & TESTING ASSISTANCE", "7"),
        ("", "  3.1 Usability and Accessibility Testing", "7"),
        ("", "  3.2 Edge Case Scenario Identification", "8"),
        ("", "  3.3 Collaborative Bug Resolution", "9"),
        ("4", "TECHNICAL DOCUMENTATION & PROJECT MANAGEMENT", "10"),
        ("", "  4.1 Establishing Documentation Standards", "10"),
        ("", "  4.2 Creating the User Manual", "11"),
        ("", "  4.3 Sprint Planning and Task Tracking", "12"),
        ("5", "MARKETING STRATEGY & MARKET ANALYSIS", "13"),
        ("", "  5.1 Beta Testing Coordination", "13"),
        ("", "  5.2 Gathering User Feedback", "14"),
        ("", "  5.3 Formulating Promotional Messaging", "15"),
        ("6", "CONCLUSION & LEARNING OUTCOMES", "17"),
        ("", "  6.1 Conclusion", "17"),
        ("", "  6.2 Personal Learnings in Full-Stack Dynamics", "18")
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
        ("1.1", "Cross-Functional Responsibilities Matrix"),
        ("2.1", "Codebase Contributions and Refactoring Log"),
        ("3.1", "Usability Test Cases Executed"),
        ("4.1", "Project Documentation Deliverables"),
        ("5.1", "Beta Testing Feedback and Resolutions"),
        ("5.2", "Marketing Content Strategy Matrix"),
        ("6.1", "Key Skill Acquisitions")
    ]
    for tbl_num, caption in tables:
        p = doc.add_paragraph()
        run = p.add_run(f"Table {tbl_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)
