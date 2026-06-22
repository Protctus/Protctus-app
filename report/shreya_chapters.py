"""
Chapters 1 to 6 for Shreya Bhadouriya's Contribution Report
(Quality Control: Documentation, Presentation & UI Review - Expanded)
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_bullet_list, add_styled_table

def expand_spacing(doc, count=2):
    for _ in range(count):
        doc.add_paragraph("")

def add_chapter1_introduction(doc):
    doc.add_heading('CHAPTER 1: INTRODUCTION & MY REVIEW ROLE', level=1)
    
    doc.add_heading('1.1 Project Motivation', level=2)
    add_styled_paragraph(doc,
        "A successful software project requires more than just functional code; it demands polish, usability, and rigorous peer review. "
        "As part of my diploma project, my motivation was to step into the role of a Quality Control (QC) Lead. I wanted to ensure that Protctus "
        "not only worked flawlessly but also looked professional, was documented impeccably, and could be presented to an academic panel with confidence."
    )
    expand_spacing(doc)

    doc.add_heading('1.2 The Importance of Quality Control', level=2)
    add_styled_paragraph(doc,
        "In a fast-paced development environment, developers often develop 'tunnel vision'—they focus so heavily on making a feature work "
        "that they overlook how a user might interact with it or how it should be documented. My role was to provide an objective, fresh perspective. "
        "I audited the team's outputs across three major domains: UI/UX Design, Technical Documentation, and Presentation Strategy."
    )
    expand_spacing(doc)

    doc.add_heading('1.3 Overview of My Responsibilities', level=2)
    add_styled_paragraph(doc,
        "Below is a detailed breakdown of my specific responsibilities as the Quality Control Reviewer."
    )

    headers = ["Review Domain", "Specific Auditing Tasks Executed", "Impact on Final Product"]
    rows_data = [
        ["UI Aesthetics Review", "Conducted heuristic evaluations of the Jetpack Compose layouts; checked color contrast ratios.", "Ensured the application met accessibility standards and maintained a consistent visual identity."],
        ["Documentation Audit", "Proofread the SRS, User Manual, and architectural diagrams against IEEE standards.", "Eliminated grammatical errors and structural inconsistencies, ensuring professional academic submissions."],
        ["Presentation Review", "Critiqued the Viva slide deck for clarity; monitored demo rehearsals for timing.", "Improved the team's oral delivery and visual communication for the final defense."],
        ["Process Gatekeeping", "Established a mandatory peer-review protocol before any document could be finalized.", "Prevented substandard deliverables from reaching the final repository."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 1.1: Detailed Breakdown of Quality Control Responsibilities")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter2_ui_review(doc):
    doc.add_heading('CHAPTER 2: UI AESTHETICS & UX REVIEW', level=1)
    
    doc.add_heading('2.1 Heuristic Evaluation Methodology', level=2)
    add_styled_paragraph(doc,
        "To systematically review the User Interface (UI), I employed Nielsen's Heuristic Evaluation principles. Rather than just saying "
        "'this looks bad,' I provided the developers with actionable, standardized feedback based on established UX laws."
    )
    expand_spacing(doc)

    headers = ["Heuristic Principle", "Observation During Audit", "Resolution Implemented"]
    rows_data = [
        ["Visibility of System Status", "The URL Scanner did not show a loading indicator while waiting for the API.", "Requested the addition of a CircularProgressIndicator during network calls."],
        ["Match Between System and Real World", "The gamification terminology was too technical (e.g., 'SQLi Vulnerability').", "Suggested simplifying terms to 'Database Hack' for non-technical users."],
        ["Consistency and Standards", "Buttons on different screens had varying corner radiuses.", "Enforced a global 'Shapes.kt' file to standardize Jetpack Compose button styles."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 2.1: UI/UX Heuristic Evaluation Results")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('2.2 Auditing the Jetpack Compose Design', level=2)
    add_styled_paragraph(doc,
        "The development team utilized Jetpack Compose for the frontend. My review focused on ensuring the declarative UI code translated "
        "into a visually appealing product. I reviewed the dark theme implementation, specifically looking for areas where 'neon green' accents "
        "clashed with 'dark gray' surfaces."
    )
    expand_spacing(doc)

    doc.add_heading('2.3 Accessibility and Color Contrast Standards', level=2)
    add_styled_paragraph(doc,
        "Accessibility is a legal and ethical requirement. I ran contrast checkers on our primary color palette to ensure it met WCAG AA standards."
    )

    headers = ["UI Component", "Initial Contrast Issue", "Adjustment Made"]
    rows_data = [
        ["Secondary Text", "Dark gray (#333333) on black background was unreadable.", "Lightened the text to #A0A0A0 for better legibility."],
        ["'Danger' Alerts", "Red text (#FF0000) vibrated against the dark mode background.", "Muted the red to a softer crimson (#EF4444) to reduce eye strain."],
        ["Touch Targets", "Some navigation icons were 24x24dp, making them hard to tap.", "Requested an increase to the Android standard minimum of 48x48dp."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 2.2: Accessibility and Contrast Adjustments")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter3_doc_audit(doc):
    doc.add_heading('CHAPTER 3: TECHNICAL DOCUMENTATION AUDIT', level=1)
    
    doc.add_heading('3.1 Establishing Review Protocols', level=2)
    add_styled_paragraph(doc,
        "As the lead documentation reviewer, I established a strict protocol: no document authored by Kunal or Swapnil could be submitted "
        "until it passed my audit. I checked for grammatical precision, technical accuracy, and adherence to our institutional formatting guidelines."
    )
    expand_spacing(doc)

    doc.add_heading('3.2 Auditing the SRS and System Diagrams', level=2)
    add_styled_paragraph(doc,
        "The Software Requirements Specification (SRS) is a legal-style document. I meticulously reviewed it to ensure that every 'shall' statement "
        "was testable. Furthermore, I audited the Data Flow Diagrams (DFDs) to ensure the arrows and entity relationships logically matched the actual Kotlin code."
    )

    headers = ["Document Section", "Common Errors Found", "Corrective Action"]
    rows_data = [
        ["SRS: Functional Req.", "Vague language (e.g., 'The app should be fast').", "Rewrote to be testable: 'The offline scan shall complete in < 5 seconds.'"],
        ["ER Diagrams", "Missing foreign keys between Firebase collections.", "Sent back to the author to include the 'user_id' mapping in the 'Quests' table."],
        ["API Documentation", "Missing HTTP error codes.", "Ensured the 404 and 500 error responses were documented alongside the 200 OK responses."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 3.1: Documentation Audit Checklist")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('3.3 Standardizing the User Manual', level=2)
    add_styled_paragraph(doc,
        "For the User Manual, I took on the persona of a non-technical end-user. I reviewed the instructions step-by-step alongside the actual app "
        "to ensure no steps were skipped. I also standardized the screenshot formatting, ensuring all images had consistent borders and captions."
    )

    add_page_break(doc)


def add_chapter4_presentation_review(doc):
    doc.add_heading('CHAPTER 4: PRESENTATION & DEMO REHEARSAL REVIEW', level=1)
    
    doc.add_heading('4.1 Reviewing the Viva Slide Deck', level=2)
    add_styled_paragraph(doc,
        "A technical project can easily fail an evaluation if the presentation is confusing. I reviewed the team's PowerPoint slides with a critical eye, "
        "enforcing the 'less is more' philosophy."
    )
    expand_spacing(doc)

    headers = ["Slide Element", "My Critique", "Optimization Made"]
    rows_data = [
        ["Text Density", "Slides contained full paragraphs of text.", "Forced the team to condense text into maximum 4 bullet points per slide."],
        ["Visual Hierarchy", "Titles were the same size as body text.", "Standardized header fonts to 36pt and body to 24pt."],
        ["Diagram Clarity", "UML diagrams were too small to read on a projector.", "Split complex diagrams across multiple slides and highlighted key areas."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 4.1: Presentation Optimization Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('4.2 Rehearsal and Timing Coordination', level=2)
    add_styled_paragraph(doc,
        "I organized and moderated three full dress rehearsals for our academic Viva. I utilized a stopwatch to time each speaker, ensuring no one "
        "exceeded their allotted 3 minutes. I provided feedback on their pacing, tone, and ability to handle simulated Q&A questions from the 'examiner'."
    )
    expand_spacing(doc)

    doc.add_heading('4.3 Live Demo Optimization', level=2)
    add_styled_paragraph(doc,
        "Live demos are risky. I reviewed the demo script and insisted that we pre-load a known phishing URL into the clipboard before the presentation started, "
        "avoiding awkward typing delays and minimizing the risk of a live typo causing an API crash."
    )

    add_page_break(doc)


def add_chapter5_feedback(doc):
    doc.add_heading('CHAPTER 5: FEEDBACK LOOPS & TEAM ALIGNMENT', level=1)
    
    doc.add_heading('5.1 Delivering Constructive Criticism', level=2)
    add_styled_paragraph(doc,
        "A significant part of my role involved soft skills: delivering critiques without causing team friction. I utilized the 'compliment sandwich' method "
        "and always tied my feedback back to the core project requirements, ensuring all criticism was objective and data-driven."
    )
    expand_spacing(doc)

    doc.add_heading('5.2 Tracking Feedback Implementation', level=2)
    add_styled_paragraph(doc,
        "I maintained a 'Review Action Log' in Notion to ensure my feedback wasn't ignored."
    )

    headers = ["Review Date", "Defect Noticed", "Assigned To", "Resolution Status"]
    rows_data = [
        ["Oct 12", "Grammar errors in Abstract draft", "Kunal", "Resolved (Oct 13)"],
        ["Oct 18", "Button contrast fails WCAG tests", "Jatin", "Resolved (Oct 20)"],
        ["Nov 05", "Demo script is 2 mins over time limit", "Swapnil", "Resolved (Nov 06)"]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.1: Feedback Resolution Log")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('5.3 Final Approval Gatekeeping', level=2)
    add_styled_paragraph(doc,
        "I served as the final gatekeeper for the project. I physically signed off on the final APK build and the printed project reports, verifying that "
        "every piece of feedback had been addressed and that the project was ready for external evaluation."
    )

    add_page_break(doc)


def add_chapter6_conclusion(doc):
    doc.add_heading('CHAPTER 6: CONCLUSION & LEARNING OUTCOMES', level=1)
    
    doc.add_heading('6.1 Conclusion', level=2)
    add_styled_paragraph(doc,
        "My role in Quality Control—spanning UI aesthetics, documentation auditing, and presentation review—was essential in elevating Protctus from a functional "
        "codebase to a polished, professional software product. By enforcing high standards across all deliverables, I ensured that our team's hard work was "
        "presented in the best possible light to the academic evaluation panel."
    )
    expand_spacing(doc)
    
    doc.add_heading('6.2 Personal Learnings in Quality Assurance', level=2)
    add_styled_paragraph(doc,
        "Operating as the primary reviewer provided me with unique insights into the software development lifecycle:"
    )

    learnings = [
        "Objective rubrics (like WCAG guidelines or IEEE standards) are vital for removing personal bias from design and writing reviews.",
        "A developer's familiarity with their own work makes them the worst person to proofread it; peer review is mandatory.",
        "Presentation and documentation are just as critical as the backend architecture when seeking stakeholder approval.",
        "Delivering constructive feedback requires empathy, clear communication, and actionable solutions."
    ]
    add_bullet_list(doc, learnings)
    expand_spacing(doc)

    headers = ["Skill Area", "Competency Gained"]
    rows_data = [
        ["UI/UX Auditing", "Proficiency in Heuristic Evaluations and WCAG Accessibility Standards."],
        ["Technical Editing", "Mastery of copyediting software documentation for clarity and academic formatting."],
        ["Project Management", "Managing review cycles, tracking action items, and gatekeeping final deliverables."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 6.1: Quality Control Skill Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    add_styled_paragraph(doc,
        "This project fulfills my diploma requirements and demonstrates my capability to act as a rigorous, detail-oriented Quality Control lead in a technical environment."
    )
    add_page_break(doc)


def add_references_appendix(doc):
    doc.add_heading('REFERENCES', level=1)
    
    refs = [
        "Nielsen, J. (1994). '10 Usability Heuristics for User Interface Design.' Nielsen Norman Group.",
        "W3C Web Accessibility Initiative (WAI). 'Web Content Accessibility Guidelines (WCAG) 2.1.'",
        "The Chicago Manual of Style. (Referenced for documentation grammar and formatting).",
        "Reynolds, G. 'Presentation Zen: Simple Ideas on Presentation Design and Delivery.'"
    ]
    for idx, ref in enumerate(refs, 1):
        p = doc.add_paragraph()
        run = p.add_run(f"[{idx}] {ref}")
        run.font.size = Pt(11)
    
    add_page_break(doc)
    doc.add_heading('APPENDIX A: SAMPLE REVIEW CHECKLIST', level=1)
    add_styled_paragraph(doc, "The following is an excerpt from the standardized UI Review Checklist I utilized during my audits.")
    expand_spacing(doc)
    
    dict_text = """
UI COMPONENT: CYBER LAB DASHBOARD
REVIEWER: SHREYA BHADOURIYA
DATE: NOV 10, 2025

[x] Contrast Check: Do primary buttons meet 4.5:1 contrast ratio?
    - Passed. Green on Dark Gray reads at 5.2:1.
[ ] Touch Targets: Are all clickable areas at least 48x48dp?
    - Failed. The 'Back' arrow in the top app bar is only 24dp.
[x] Error States: Do error messages clearly explain the problem?
    - Passed. Snackbar clearly states "Network Disconnected".
[x] Loading States: Is there a visual indicator for asynchronous tasks?
    - Passed. Circular loader spins while fetching trivia questions.
    """
    add_styled_paragraph(doc, dict_text, font_name="Consolas", size=9)
