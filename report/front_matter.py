"""
Front Matter: Title Page, Certificate, Declaration, Acknowledgement, Abstract, TOC
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
    run = p.add_run("An Android Cyber-Security Application")
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
    run = p.add_run("JATIN SHARMA")
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
        "\"PROTCTUS - An Android Cyber-Security Application\" is a bonafide record of the individual project "
        "work carried out by JATIN SHARMA during the academic year 2025-2026 under my "
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
        "I hereby declare that the project entitled \"PROTCTUS - An Android "
        "Cyber-Security Application\" submitted for the partial fulfillment of my diploma requirements is "
        "an original piece of work conducted entirely by me. The information, backend setup, frontend code, and "
        "design strategies submitted within this report are authentic and have not been submitted "
        "elsewhere for any other degree or diploma.",
        size=11, space_after=12
    )

    add_styled_paragraph(doc,
        "As the sole developer of this application, I have personally researched, designed, and implemented "
        "the architecture, UI/UX, and core features. All external sources of information and third-party libraries "
        "(Firebase, Jetpack Compose, etc.) used to facilitate this project have been duly acknowledged.",
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
    add_styled_paragraph(doc, "Name: JATIN SHARMA", size=11, bold=True)

    add_page_break(doc)


def add_acknowledgement(doc):
    h = doc.add_heading('ACKNOWLEDGEMENT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "I would like to express my sincere gratitude to my project guide for their continuous support, valuable insights, and patient mentorship throughout the entire lifecycle of this project. Their guidance while I was researching and developing this mobile app has been invaluable.",

        "I extend my heartfelt thanks to the Head of the Department and the entire faculty of the Computer Science & Engineering Department for fostering an academic environment that encourages hands-on learning and individual effort.",

        "I am deeply grateful to my institution, Government Polytechnic College, Shivpuri, for providing the infrastructure and academic resources that made this solo project possible.",

        "Special thanks go to the open-source developer communities, whose tutorials and documentation helped me learn Jetpack Compose, Kotlin, and Firebase on my own.",

        "Finally, I extend my deepest appreciation to my family and friends for their unwavering encouragement and moral support during the long hours I spent coding and debugging this application."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=10)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "JATIN SHARMA", size=12, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT)
    add_page_break(doc)


def add_abstract(doc):
    h = doc.add_heading('ABSTRACT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "This Personal Contribution Report details my journey in designing and developing Protctus, a lightweight and user-friendly Android cyber-security application. I undertook this project as the sole developer, taking full responsibility for the strategy, research, UI/UX design, frontend implementation, backend architecture, and testing.",

        "The primary goal of my project was to create an essential security application that not only protects devices but also educates the user about digital safety. Through my research, I identified a gap in how mobile security apps engage users, leading me to adopt a gamified micro-learning approach alongside standard security features.",

        "I personally coded the frontend using Kotlin and Jetpack Compose, ensuring a modern, fluid user interface based on my UI/UX research. I implemented a local offline App Scanner and a URL Checker to help users verify links. For the backend, I designed and integrated Firebase to handle secure user authentication, database management, and progress tracking for the educational Cyber Lab feature.",

        "This report outlines my thought process behind the application's architecture (using MVVM principles), the challenges I faced while learning and implementing these modern Android development technologies, and how I overcame them. The successful deployment of Protctus serves as a comprehensive demonstration of my full-stack mobile development capabilities."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=8)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "Keywords: ", bold=True, size=11, space_after=4)
    add_styled_paragraph(doc,
        "Android Development, Full-Stack, Jetpack Compose, UI/UX Design, "
        "Kotlin, Firebase Architecture, Personal Project, Cybersecurity",
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
        ("", "  List of Figures", "vii"),
        ("", "  List of Tables", "viii"),
        ("1", "INTRODUCTION & MY ROLE", "1"),
        ("", "  1.1 Project Motivation", "1"),
        ("", "  1.2 My Objectives as a Solo Developer", "2"),
        ("", "  1.3 Overview of My Responsibilities", "3"),
        ("2", "RESEARCH & UI/UX STRATEGY", "4"),
        ("", "  2.1 Market Research", "4"),
        ("", "  2.2 UI/UX Design Strategy", "5"),
        ("", "  2.3 Planning the User Experience", "6"),
        ("3", "ARCHITECTURE & BACKEND DESIGN", "7"),
        ("", "  3.1 Choosing the Tech Stack", "7"),
        ("", "  3.2 MVVM Architecture Planning", "8"),
        ("", "  3.3 Designing the Firebase Backend", "9"),
        ("4", "FRONTEND & CORE IMPLEMENTATION", "10"),
        ("", "  4.1 Frontend UI with Jetpack Compose", "10"),
        ("", "  4.2 Coding the Core Features", "12"),
        ("", "  4.3 Backend Integration", "14"),
        ("5", "TESTING, CHALLENGES & CONCLUSION", "15"),
        ("", "  5.1 My Testing Methodology", "15"),
        ("", "  5.2 Challenges Faced & Solutions", "16"),
        ("", "  5.3 Conclusion & Personal Learnings", "18"),
        ("", "REFERENCES", "19")
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


def add_list_of_figures(doc):
    h = doc.add_heading('LIST OF FIGURES', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("")

    figures = [
        ("1.1", "Protctus App Concept Diagram"),
        ("2.1", "My Initial Wireframing and Design Approach"),
        ("3.1", "MVVM Architecture Diagram I Used"),
        ("3.2", "My Firebase Database Structure"),
        ("4.1", "Frontend Implementation of the Dashboard"),
        ("4.2", "My Code Implementation for the Scanner"),
        ("5.1", "Testing the Gamification Features"),
        ("5.2", "Resolving UI Bugs on Different Screen Sizes")
    ]
    for fig_num, caption in figures:
        p = doc.add_paragraph()
        run = p.add_run(f"Figure {fig_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)


def add_list_of_tables(doc):
    h = doc.add_heading('LIST OF TABLES', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("")

    tables = [
        ("1.1", "My Core Responsibilities Breakdown"),
        ("2.1", "Design Decisions and Rationale"),
        ("3.1", "Technologies I Selected"),
        ("4.1", "Key Classes I Developed"),
        ("5.1", "Bugs I Encountered and Fixed")
    ]
    for tbl_num, caption in tables:
        p = doc.add_paragraph()
        run = p.add_run(f"Table {tbl_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)
