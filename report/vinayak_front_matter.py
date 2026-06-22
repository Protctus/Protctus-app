"""
Front Matter: Title Page, Certificate, Declaration, Acknowledgement, Abstract, TOC
For Vinayak Chourasiya (Testing & Marketing)
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
    run = p.add_run("UX Contributor")
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
    run = p.add_run("VINAYAK CHOURASIYA")
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
        "\"PROTCTUS - UX Contributor\" is a bonafide record of the individual project "
        "work carried out by VINAYAK CHOURASIYA during the academic year 2025-2026 under my "
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
        "I hereby declare that the project report entitled \"PROTCTUS - UX Contributor\" "
        "submitted for the partial fulfillment of my diploma requirements represents my personal contribution "
        "to the Protctus mobile application. The information regarding testing strategies, bug reporting, and "
        "market research submitted within this report are authentic and have not been submitted "
        "elsewhere for any other degree or diploma.",
        size=11, space_after=12
    )

    add_styled_paragraph(doc,
        "As the lead for Testing and Marketing, I personally conducted the competitive analysis, developed the "
        "go-to-market strategies, and performed rigorous quality assurance testing to ensure the application's stability. "
        "The core development of the application was handled collaboratively, but the strategies and test records outlined "
        "in this report are my individual work.",
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
    add_styled_paragraph(doc, "Name: VINAYAK CHOURASIYA", size=11, bold=True)

    add_page_break(doc)


def add_acknowledgement(doc):
    h = doc.add_heading('ACKNOWLEDGEMENT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "I would like to express my sincere gratitude to my project guide for their continuous support, valuable insights, and patient mentorship. Their guidance in understanding software quality assurance and market positioning has been invaluable.",

        "I extend my heartfelt thanks to the Head of the Department and the entire faculty of the Computer Science & Engineering Department for fostering an academic environment that encourages practical, industry-focused learning.",

        "I am deeply grateful to my institution, Government Polytechnic College, Shivpuri, for providing the necessary resources to conduct my research and testing effectively.",

        "I would also like to thank my teammate, Jatin Sharma, for his relentless work in coding the application. Our continuous collaboration between development and testing ensured Protctus became a polished, reliable product.",

        "Finally, I extend my deepest appreciation to my family and friends for their unwavering encouragement and moral support."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=10)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "VINAYAK CHOURASIYA", size=12, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT)
    add_page_break(doc)


def add_abstract(doc):
    h = doc.add_heading('ABSTRACT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "This Personal Contribution Report details my role in the creation of Protctus, an Android cyber-security application. While the software development was handled collaboratively, my individual focus and responsibility centered on two critical pillars of the software lifecycle: Quality Assurance (Testing) and Marketing Strategy.",

        "In the highly competitive landscape of mobile applications, a robust testing phase is crucial. My primary objective was to ensure that Protctus functioned flawlessly across different Android versions and screen sizes. I developed and executed comprehensive manual test cases covering the App Scanner, URL Checker, and the Gamified Cyber Lab. Through systematic bug reporting and performance monitoring, I helped secure the application's stability before its final deployment.",

        "Beyond technical quality, a product requires strong market positioning to succeed. I conducted extensive market research to analyze existing security applications, identifying their weaknesses—such as battery drain and intrusive ads. Using this data, I formulated a marketing strategy emphasizing Protctus's lightweight, offline, and educational nature to target specific user demographics.",

        "This report outlines my methodologies for software testing, the challenges encountered during the QA process, the market analysis data I gathered, and the promotional strategies I designed. My work ensures that Protctus is not only technically sound but also strategically positioned for adoption."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=8)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "Keywords: ", bold=True, size=11, space_after=4)
    add_styled_paragraph(doc,
        "Software Testing, Quality Assurance, Market Research, "
        "Marketing Strategy, Bug Reporting, Android Security",
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
        ("1", "INTRODUCTION & MY ROLE", "1"),
        ("", "  1.1 Project Motivation", "1"),
        ("", "  1.2 Objectives of QA & Marketing", "2"),
        ("", "  1.3 Overview of My Responsibilities", "3"),
        ("2", "MARKET RESEARCH & COMPETITIVE ANALYSIS", "4"),
        ("", "  2.1 Analyzing the Android Security Market", "4"),
        ("", "  2.2 Competitor Weaknesses", "5"),
        ("", "  2.3 Identifying the Target Audience", "6"),
        ("3", "MARKETING & BRANDING STRATEGY", "7"),
        ("", "  3.1 Value Proposition", "7"),
        ("", "  3.2 Brand Identity and Messaging", "8"),
        ("", "  3.3 Go-to-Market Plan", "9"),
        ("4", "SOFTWARE TESTING METHODOLOGY", "10"),
        ("", "  4.1 Quality Assurance Goals", "10"),
        ("", "  4.2 Test Environment Setup", "11"),
        ("", "  4.3 Types of Testing Performed", "12"),
        ("5", "TEST EXECUTION & BUG REPORTING", "13"),
        ("", "  5.1 Core Feature Test Cases", "13"),
        ("", "  5.2 UI/UX and Responsiveness Testing", "14"),
        ("", "  5.3 Bug Tracking and Resolution", "15"),
        ("6", "CONCLUSION & LEARNING OUTCOMES", "17"),
        ("", "  6.1 Conclusion", "17"),
        ("", "  6.2 Personal Learnings", "18")
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
        ("1.1", "My Core Responsibilities in QA and Marketing"),
        ("2.1", "Competitor Feature Matrix"),
        ("3.1", "Marketing Channels and Strategies"),
        ("4.1", "Testing Environment Details"),
        ("5.1", "App Scanner Test Cases"),
        ("5.2", "Gamification Database Test Cases"),
        ("5.3", "Summary of Major Bugs Found and Resolved")
    ]
    for tbl_num, caption in tables:
        p = doc.add_paragraph()
        run = p.add_run(f"Table {tbl_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)
