"""
Front Matter: Title Page, Certificate, Declaration, Acknowledgement, Abstract, TOC
For Kunal Dhakad (Technical Documentation & Presentation)
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
    run = p.add_run("Technical Documentation & Presentation Strategy")
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
    run = p.add_run("KUNAL DHAKAD")
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
        "\"PROTCTUS - Technical Documentation & Presentation Strategy\" is a bonafide record of the individual project "
        "work carried out by KUNAL DHAKAD during the academic year 2025-2026 under my "
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
        "I hereby declare that the project report entitled \"PROTCTUS - Technical Documentation & Presentation Strategy\" "
        "submitted for the partial fulfillment of my diploma requirements represents my personal contribution "
        "to the Protctus mobile application. The information regarding documentation architectures, data flow diagrams, "
        "and presentation design strategies submitted within this report are authentic and have not been submitted "
        "elsewhere for any other degree or diploma.",
        size=11, space_after=12
    )

    add_styled_paragraph(doc,
        "As the lead for Documentation and Presentation, I personally authored the Software Requirements Specification (SRS), "
        "created the system architectural diagrams, formulated the technical user manuals, and designed the comprehensive "
        "slide decks used for project defense. The core application was built as a team, but the structural documentation and "
        "presentation methodologies outlined here are my individual work.",
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
    add_styled_paragraph(doc, "Name: KUNAL DHAKAD", size=11, bold=True)

    add_page_break(doc)


def add_acknowledgement(doc):
    h = doc.add_heading('ACKNOWLEDGEMENT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "I would like to express my sincere gratitude to my project guide for their continuous support, valuable insights, and patient mentorship. Their guidance in understanding the academic and industry standards for technical writing has been invaluable.",

        "I extend my heartfelt thanks to the Head of the Department and the entire faculty of the Computer Science & Engineering Department for fostering an environment that emphasizes the importance of clear communication in software engineering.",

        "I am deeply grateful to my institution, Government Polytechnic College, Shivpuri, for providing the necessary resources and platforms to refine our project presentation skills.",

        "I would also like to thank my teammates—Jatin Sharma, Vinayak Chourasiya, and Swapnil Chaurasiya—for providing the deep technical insights and data required to author accurate and comprehensive documentation.",

        "Finally, I extend my deepest appreciation to my family and friends for their unwavering encouragement and moral support."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=10)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "KUNAL DHAKAD", size=12, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT)
    add_page_break(doc)


def add_abstract(doc):
    h = doc.add_heading('ABSTRACT', level=1)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph("")
    paras = [
        "This Personal Contribution Report details my specific role in the creation of Protctus, an Android cyber-security application. In the field of software engineering, exceptional code holds little value if it cannot be understood, maintained, or communicated effectively to stakeholders. My primary responsibility within the team was to bridge this gap by leading the Technical Documentation and Presentation Strategy.",

        "My core objective was to translate complex architectural decisions, such as the implementation of the Jetpack Compose MVVM pattern and offline Room SQLite databases, into clear, standardized documentation. I took ownership of drafting the Software Requirements Specification (SRS), designing Data Flow Diagrams (DFDs), and writing the end-user manual. This ensured that the project maintained a professional standard of technical traceability throughout its lifecycle.",

        "Beyond written documentation, I was responsible for visually conveying our project's value. I designed the final presentation slides used for our academic defense, focusing on data visualization, competitive market advantages, and clear UI/UX walkthroughs. I also managed team alignment meetings to ensure that our oral presentation matched the technical reality of the application.",

        "This report outlines my methodology for technical writing, the tools utilized for creating architectural diagrams, the strategies employed to design high-impact presentations, and the challenges faced in keeping documentation synchronized with a rapidly evolving codebase."
    ]
    for text in paras:
        add_styled_paragraph(doc, text, size=11, space_after=8)

    doc.add_paragraph("")
    add_styled_paragraph(doc, "Keywords: ", bold=True, size=11, space_after=4)
    add_styled_paragraph(doc,
        "Technical Writing, Software Documentation, Presentation Design, "
        "Data Flow Diagrams, SRS, Stakeholder Communication",
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
        ("", "  1.2 The Importance of Technical Writing", "2"),
        ("", "  1.3 Overview of My Responsibilities", "3"),
        ("2", "ESTABLISHING DOCUMENTATION STANDARDS", "4"),
        ("", "  2.1 Selecting the Right Tools", "4"),
        ("", "  2.2 Standardizing Formats and Templates", "5"),
        ("", "  2.3 Managing Version Control for Documents", "6"),
        ("3", "PROJECT DOCUMENTATION DELIVERABLES", "7"),
        ("", "  3.1 Software Requirements Specification (SRS)", "7"),
        ("", "  3.2 Architectural and Data Flow Diagrams", "8"),
        ("", "  3.3 The User Manual", "9"),
        ("4", "PRESENTATION STRATEGY & DESIGN", "10"),
        ("", "  4.1 Designing for the Academic Viva", "10"),
        ("", "  4.2 Data Visualization Techniques", "11"),
        ("", "  4.3 Structuring the Demo Flow", "12"),
        ("5", "TEAM COMMUNICATION & ALIGNMENT", "13"),
        ("", "  5.1 Facilitating Technical Syncs", "13"),
        ("", "  5.2 Synchronizing Code with Documentation", "14"),
        ("", "  5.3 Final Review Protocols", "15"),
        ("6", "CONCLUSION & LEARNING OUTCOMES", "17"),
        ("", "  6.1 Conclusion", "17"),
        ("", "  6.2 Personal Learnings in Technical Communication", "18")
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
        ("1.1", "Detailed Breakdown of Documentation Responsibilities"),
        ("2.1", "Documentation Tools and Technologies Used"),
        ("3.1", "SRS Deliverable Modules"),
        ("3.2", "System Diagrams Created"),
        ("4.1", "Presentation Slide Structure"),
        ("5.1", "Internal Communication Matrices"),
        ("6.1", "Technical Writing Skill Matrix")
    ]
    for tbl_num, caption in tables:
        p = doc.add_paragraph()
        run = p.add_run(f"Table {tbl_num}:  {caption}")
        run.font.size = Pt(10)
    add_page_break(doc)
