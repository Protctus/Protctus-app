"""
Chapters 1 to 6 for Kunal Dhakad's Contribution Report
(Technical Documentation & Presentation - Expanded)
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_bullet_list, add_styled_table

def expand_spacing(doc, count=2):
    for _ in range(count):
        doc.add_paragraph("")

def add_chapter1_introduction(doc):
    doc.add_heading('CHAPTER 1: INTRODUCTION & MY ROLE', level=1)
    
    doc.add_heading('1.1 Project Motivation', level=2)
    add_styled_paragraph(doc,
        "In the realm of software engineering, a project is only as strong as the documentation that supports it. Code, no matter how elegant or optimized, "
        "becomes legacy technical debt if it is not properly documented. As part of my diploma project, my motivation was to master the art of technical "
        "communication. I wanted to ensure that Protctus, our cyber-security application, was backed by industry-standard documentation that allowed anyone "
        "to understand its architecture, data flow, and user capabilities without needing to read the source code."
    )
    expand_spacing(doc)

    doc.add_heading('1.2 The Importance of Technical Writing', level=2)
    add_styled_paragraph(doc,
        "Technical writing bridges the gap between software developers and stakeholders. For this project, our stakeholders included our academic evaluators, "
        "future maintainers of the codebase, and end-users. I took ownership of translating the complex Jetpack Compose UI logic, Firebase NoSQL structures, "
        "and offline SQLite integration into readable, accessible documentation."
    )
    expand_spacing(doc)

    doc.add_heading('1.3 Overview of My Responsibilities', level=2)
    add_styled_paragraph(doc,
        "Below is a detailed breakdown of my core responsibilities throughout the project lifecycle."
    )

    headers = ["Domain", "Specific Tasks Executed", "Project Impact"]
    rows_data = [
        ["Requirement Analysis", "Drafted the comprehensive Software Requirements Specification (SRS) defining functional and non-functional goals.", "Provided a clear roadmap preventing scope creep during development."],
        ["System Design", "Created Data Flow Diagrams (DFDs), Entity-Relationship (ER) diagrams, and Use Case UMLs.", "Allowed developers to visualize database structures before writing actual code."],
        ["User Documentation", "Wrote the end-user manual and compiled troubleshooting guides.", "Ensured non-technical users could easily navigate the app's security features."],
        ["Presentation", "Designed the final academic viva PowerPoint presentation and rehearsed delivery strategies.", "Ensured the team could confidently defend our architectural choices during the final exam."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 1.1: Detailed Breakdown of Documentation Responsibilities")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter2_standards(doc):
    doc.add_heading('CHAPTER 2: ESTABLISHING DOCUMENTATION STANDARDS', level=1)
    
    doc.add_heading('2.1 Selecting the Right Tools', level=2)
    add_styled_paragraph(doc,
        "To ensure our documentation was professional and scalable, I carefully evaluated and selected a suite of tools tailored to specific documentation needs."
    )
    
    headers = ["Tool Used", "Primary Purpose", "Reason for Selection"]
    rows_data = [
        ["Lucidchart / Draw.io", "Creating UML, DFD, and ER diagrams.", "Cloud-based collaboration and easy export to vector graphic formats."],
        ["Notion", "Internal team wiki and meeting minutes.", "Allowed asynchronous communication and centralized task tracking."],
        ["Markdown (GitHub)", "Technical README and API documentation.", "Standard format for developers; rendered perfectly natively in the repository."],
        ["Microsoft Word / PowerPoint", "Final report generation and Viva slides.", "Academic standard formats required for submission."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 2.1: Documentation Tools and Technologies Used")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('2.2 Standardizing Formats and Templates', level=2)
    add_styled_paragraph(doc,
        "Consistency is critical in technical writing. I created templates for all project documents. For example, every API endpoint documentation required "
        "a standardized structure: Endpoint URL, HTTP Method, Required Headers, JSON Body Payload, and Expected JSON Response (both success and error codes). "
        "This standardization prevented the developer from writing ad-hoc, messy notes."
    )
    expand_spacing(doc)

    doc.add_heading('2.3 Managing Version Control for Documents', level=2)
    add_styled_paragraph(doc,
        "Documentation must evolve alongside code. I implemented a strict versioning protocol. The SRS document went through three major revisions (v1.0, v1.1, v2.0) "
        "as the scope of the gamified Cyber Lab changed during development. I ensured that outdated documentation was explicitly archived to prevent confusion."
    )

    add_page_break(doc)


def add_chapter3_deliverables(doc):
    doc.add_heading('CHAPTER 3: PROJECT DOCUMENTATION DELIVERABLES', level=1)
    
    doc.add_heading('3.1 Software Requirements Specification (SRS)', level=2)
    add_styled_paragraph(doc,
        "The SRS was the foundational document of our project. It outlined exactly what Protctus would do. I structured the SRS according to IEEE standards."
    )

    headers = ["SRS Module", "Content Description"]
    rows_data = [
        ["Introduction", "Defined the purpose, scope, and target audience of Protctus."],
        ["Functional Requirements", "Detailed the exact logic for the App Scanner and URL Checker."],
        ["Non-Functional Requirements", "Specified performance metrics (e.g., scan must complete in < 5 seconds offline)."],
        ["System Interfaces", "Documented the integration with Firebase Auth and the Google Safe Browsing API."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 3.1: SRS Deliverable Modules")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('3.2 Architectural and Data Flow Diagrams', level=2)
    add_styled_paragraph(doc,
        "Visualizing system architecture is often more effective than explaining it in text. I created multiple diagrams to map the logic of the application."
    )

    headers = ["Diagram Type", "Focus Area Documented"]
    rows_data = [
        ["Level 0 DFD (Context)", "High-level data interaction between the User, Protctus App, and Firebase Cloud."],
        ["Level 1 DFD", "Detailed data flow for the App Scanner parsing the local Room SQLite database."],
        ["Entity-Relationship (ER)", "Mapped the 'Users', 'Quests', and 'XP Logs' collections in Firebase NoSQL."],
        ["Use Case UML", "Defined actor interactions (User vs Admin) within the gamified Cyber Lab system."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 3.2: System Diagrams Created")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('3.3 The User Manual', level=2)
    add_styled_paragraph(doc,
        "I authored a comprehensive 15-page User Manual designed for individuals with zero cybersecurity knowledge. It included step-by-step instructions, "
        "glossaries for terms like 'Phishing' and 'Malware', and troubleshooting steps for common installation errors."
    )

    add_page_break(doc)


def add_chapter4_presentation(doc):
    doc.add_heading('CHAPTER 4: PRESENTATION STRATEGY & DESIGN', level=1)
    
    doc.add_heading('4.1 Designing for the Academic Viva', level=2)
    add_styled_paragraph(doc,
        "A major component of my role was preparing the team for the final academic defense. I designed a 25-slide PowerPoint deck that "
        "synthesized months of development into a crisp, 15-minute presentation. I ensured the slides adhered to the '10-20-30 Rule': clean typography, "
        "minimal text, and high-contrast visuals."
    )
    expand_spacing(doc)

    doc.add_heading('4.2 Data Visualization Techniques', level=2)
    add_styled_paragraph(doc,
        "Instead of bullet points listing our testing results, I utilized data visualization. I created bar charts comparing Protctus's battery consumption "
        "against competitor apps (demonstrating our 40% efficiency gain). I also designed workflow infographics to explain the MVVM architecture simply."
    )
    expand_spacing(doc)

    doc.add_heading('4.3 Structuring the Demo Flow', level=2)
    add_styled_paragraph(doc,
        "A live software demo is prone to failure. I structured a strict 'Golden Path' demo script for the team to follow during the presentation."
    )

    headers = ["Demo Step", "Action", "Narrative Focus"]
    rows_data = [
        ["Step 1: Onboarding", "Launch app, show Firebase login.", "Highlight secure authentication and clean UI."],
        ["Step 2: URL Checker", "Paste a known phishing link.", "Demonstrate real-time API integration and threat prevention."],
        ["Step 3: Offline Scanner", "Turn off Wi-Fi, run App Scanner.", "Prove the core value proposition: offline functionality."],
        ["Step 4: Cyber Lab", "Answer a quiz question correctly.", "Show the gamification logic and Firestore database update."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 4.1: Presentation Slide and Demo Structure")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter5_communication(doc):
    doc.add_heading('CHAPTER 5: TEAM COMMUNICATION & ALIGNMENT', level=1)
    
    doc.add_heading('5.1 Facilitating Technical Syncs', level=2)
    add_styled_paragraph(doc,
        "To ensure my documentation remained accurate, I facilitated weekly technical sync meetings. I interviewed Jatin (Lead Developer) about the backend logic "
        "he wrote that week, and queried Vinayak (QA) about the latest bugs found. I took extensive meeting minutes and distributed action items via Notion."
    )
    expand_spacing(doc)

    doc.add_heading('5.2 Synchronizing Code with Documentation', level=2)
    add_styled_paragraph(doc,
        "A common failure in software projects is 'documentation drift'—where the code changes but the docs do not. I instituted a rule: no major feature branch "
        "could be merged into the main repository until the corresponding technical documentation (README or API spec) was updated to reflect the new code."
    )

    headers = ["Meeting / Sync Type", "Frequency", "Documentation Outcome"]
    rows_data = [
        ["Sprint Planning", "Weekly", "Updated the project roadmap and assigned Jira/Trello tickets."],
        ["Architecture Review", "Bi-Weekly", "Updated the ER/DFD diagrams if database schemas changed."],
        ["Pre-Release Sync", "Before Beta", "Finalized the User Manual and Release Notes."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.1: Internal Communication Matrices")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('5.3 Final Review Protocols', level=2)
    add_styled_paragraph(doc,
        "Before finalizing the project report, I conducted a comprehensive peer-review process, ensuring all technical jargon was used correctly, formatting was impeccable, "
        "and the document met all institutional academic standards."
    )

    add_page_break(doc)


def add_chapter6_conclusion(doc):
    doc.add_heading('CHAPTER 6: CONCLUSION & LEARNING OUTCOMES', level=1)
    
    doc.add_heading('6.1 Conclusion', level=2)
    add_styled_paragraph(doc,
        "My role as the Technical Documentation and Presentation Lead was pivotal in transforming a complex codebase into a professional, comprehensible software product. "
        "By enforcing documentation standards, creating detailed system diagrams, and designing a compelling academic presentation, I ensured that the hard work of the "
        "development and testing teams was communicated effectively to all stakeholders."
    )
    expand_spacing(doc)
    
    doc.add_heading('6.2 Personal Learnings in Technical Communication', level=2)
    add_styled_paragraph(doc,
        "This project provided profound insights into the discipline of technical writing. I learned that:"
    )

    learnings = [
        "Technical writing is not just about recording facts; it is about structuring information so that it is easily digestible by distinct audiences (users vs. developers).",
        "Visual diagrams (UML/DFD) resolve architectural misunderstandings much faster than text descriptions.",
        "A successful presentation relies heavily on narrative flow and data visualization rather than overwhelming the audience with bullet points.",
        "Documentation drift is a real threat, requiring strict version control and team discipline to mitigate."
    ]
    add_bullet_list(doc, learnings)
    expand_spacing(doc)

    headers = ["Skill Area", "Competency Gained"]
    rows_data = [
        ["Technical Writing", "Mastered IEEE SRS formatting, Markdown drafting, and API documentation."],
        ["System Design", "Proficient in mapping data flows and NoSQL database structures visually."],
        ["Public Speaking / Pitching", "Developed strong presentation design skills and defense strategies."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 6.1: Technical Writing Skill Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    add_styled_paragraph(doc,
        "This project fulfills my diploma requirements and serves as a comprehensive portfolio demonstrating my abilities in technical communication, system analysis, and project presentation."
    )
    add_page_break(doc)


def add_references_appendix(doc):
    doc.add_heading('REFERENCES', level=1)
    
    refs = [
        "IEEE Computer Society. 'IEEE Recommended Practice for Software Requirements Specifications (IEEE Std 830-1998).'",
        "Garr Reynolds. 'Presentation Zen: Simple Ideas on Presentation Design and Delivery.'",
        "Fowler, Martin. 'UML Distilled: A Brief Guide to the Standard Object Modeling Language.'",
        "Google Developers Style Guide for Technical Writing. Available at: https://developers.google.com/style"
    ]
    for idx, ref in enumerate(refs, 1):
        p = doc.add_paragraph()
        run = p.add_run(f"[{idx}] {ref}")
        run.font.size = Pt(11)
    
    add_page_break(doc)
    doc.add_heading('APPENDIX A: SAMPLE DATA DICTIONARY', level=1)
    add_styled_paragraph(doc, "The following is an excerpt from the Data Dictionary I authored for the Firebase User Collection.")
    expand_spacing(doc)
    
    dict_text = """
Table: users_collection
Description: Stores primary user profile and gamification state.

Field Name    | Data Type | Description                       | Required
--------------|-----------|-----------------------------------|---------
user_id       | String    | Unique Firebase Auth UID          | YES
email         | String    | User's registered email address   | YES
current_xp    | Integer   | Total experience points earned    | YES (Def: 0)
current_rank  | String    | Rank title (e.g., 'Novice')       | YES
last_login    | Timestamp | Unix timestamp of last session    | NO
    """
    add_styled_paragraph(doc, dict_text, font_name="Consolas", size=9)
