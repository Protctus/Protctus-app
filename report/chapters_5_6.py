"""
Chapters 5 & 6: Testing, Conclusion & Learning Outcomes
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_code_block, add_bullet_list
from .chapters_1_2 import add_web_image

def add_chapter5_testing(doc):
    doc.add_heading('CHAPTER 5: TESTING, CHALLENGES & CONCLUSION', level=1)

    doc.add_heading('5.1 My Testing Methodology', level=2)
    add_styled_paragraph(doc,
        "Since I was building this on my own, I did not have a dedicated QA team. I performed manual testing "
        "on my personal Android device. I tested the app under various conditions, such as running it without "
        "Wi-Fi to ensure the offline scanner worked correctly."
    )

    add_web_image(doc, "https://images.unsplash.com/photo-1551288049-bebda4e38f71?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 5.1: Testing the Gamification Features")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    doc.add_heading('5.2 Challenges Faced & Solutions', level=2)
    add_styled_paragraph(doc,
        "Building a full-stack Android application as a solo student developer presented several challenges:"
    )

    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    hdr_cells = table.rows[0].cells
    hdr_cells[0].text = 'Challenge I Faced'
    hdr_cells[1].text = 'How I Solved It'

    rows_data = [
        ("Learning Jetpack Compose", "The new UI toolkit was confusing at first. I spent a week watching tutorials and reading the official Android documentation to understand 'State'."),
        ("App Crashing on Large Scans", "When the scanner checked over 100 apps, the UI would freeze. I learned to use Kotlin Coroutines (Dispatchers.IO) to move the heavy work to a background thread."),
        ("Offline Database Setup", "Setting up Room SQLite was difficult. I resolved it by creating DAO interfaces and using Dependency Injection to provide the database safely."),
        ("UI Scaling Issues", "The app looked bad on smaller screens. I fixed this by using relative padding (e.g., passing Modifiers) instead of hardcoding pixel heights.")
    ]
    for challenge, solution in rows_data:
        row_cells = table.add_row().cells
        row_cells[0].text = challenge
        row_cells[1].text = solution

    p = doc.add_paragraph("Table 5.1: Bugs I Encountered and Fixed")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_web_image(doc, "https://images.unsplash.com/photo-1555949963-aa79dcee981c?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 5.2: Resolving UI Bugs on Different Screen Sizes")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter6_conclusion(doc):
    doc.add_heading('5.3 Conclusion & Personal Learnings', level=2)
    add_styled_paragraph(doc,
        "Completing Protctus has been an incredibly rewarding experience. When I started, I only knew basic programming "
        "concepts. By the end of this project, I successfully deployed a modern Android application with a working backend."
    )
    
    add_styled_paragraph(doc,
        "I learned that software development is not just about writing code; it is about solving problems, researching "
        "best practices, and thinking about how real users will interact with the application."
    )

    learnings = [
        "Mastered building Android UIs with Jetpack Compose.",
        "Gained hands-on experience with Firebase Authentication and Firestore.",
        "Learned how to use Kotlin Coroutines for asynchronous programming.",
        "Understood the basics of mobile cybersecurity and how malicious apps hide on devices.",
        "Proved my ability to manage a full software project from idea to completion."
    ]
    add_bullet_list(doc, learnings)

    add_styled_paragraph(doc,
        "This project successfully fulfills the requirements for my diploma and serves as a strong portfolio piece "
        "demonstrating my capabilities as a Junior Android Developer."
    )
    add_page_break(doc)


def add_references(doc):
    doc.add_heading('REFERENCES', level=1)
    
    refs = [
        "Android Developers Documentation. 'Jetpack Compose Tutorial.' Available at: https://developer.android.com/jetpack/compose/tutorial",
        "Kotlin Programming Language Official Documentation. 'Coroutines Basics.' Available at: https://kotlinlang.org/docs/coroutines-basics.html",
        "Firebase Official Documentation. 'Cloud Firestore Data Model.' Available at: https://firebase.google.com/docs/firestore/data-model",
        "Stack Overflow Community. Various troubleshooting threads for Android lifecycle and Room database errors.",
        "Google Safe Browsing API Documentation. Available at: https://developers.google.com/safe-browsing"
    ]
    for idx, ref in enumerate(refs, 1):
        p = doc.add_paragraph()
        run = p.add_run(f"[{idx}] {ref}")
        run.font.size = Pt(11)


def add_appendix(doc):
    # Removed generic appendix code for this personal contribution report
    pass
