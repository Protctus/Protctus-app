"""
Chapter 4: Frontend & Core Implementation
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_code_block, add_bullet_list
from .chapters_1_2 import add_web_image

def add_chapter4_implementation(doc):
    doc.add_heading('CHAPTER 4: FRONTEND & CORE IMPLEMENTATION', level=1)

    doc.add_heading('4.1 Frontend UI with Jetpack Compose', level=2)
    add_styled_paragraph(doc,
        "I spent a significant portion of my time writing the frontend. Because Jetpack Compose uses Kotlin "
        "to define the UI instead of XML, I had to learn how to manage 'State'. I created reusable components "
        "like custom buttons, cards, and animated progress bars to keep my code clean."
    )

    add_web_image(doc, "https://images.unsplash.com/photo-1618761714954-0b8cd0026356?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 4.1: Frontend Implementation of the Dashboard")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_styled_paragraph(doc,
        "For example, to make the dashboard look like a high-tech security app, I used Surface layouts "
        "with rounded corners, dark gray backgrounds, and neon green text for 'Safe' status. I also wrote "
        "animations so the scan button pulses when the user clicks it."
    )

    doc.add_heading('4.2 Coding the Core Features', level=2)
    add_styled_paragraph(doc,
        "The core functionality of Protctus relies on the App Scanner and the URL Checker. I coded both of these from scratch."
    )

    add_styled_paragraph(doc, "The App Scanner", bold=True)
    add_styled_paragraph(doc,
        "I wrote a Kotlin function that uses the Android PackageManager to get a list of all installed applications. "
        "Then, I extracted the signature of each app and compared it against a local SQLite (Room) database of known "
        "malware signatures. By doing this locally, I achieved my goal of making the scanner work entirely offline."
    )

    add_web_image(doc, "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 4.2: My Code Implementation for the Scanner")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_styled_paragraph(doc, "The URL Phishing Checker", bold=True)
    add_styled_paragraph(doc,
        "I wrote a feature where a user can paste a link, and my code sends an HTTP request to the Google Safe Browsing API. "
        "I used the Retrofit library and Kotlin Coroutines to handle this network request smoothly so the app UI wouldn't freeze while waiting for a response."
    )

    doc.add_heading('4.3 Backend Integration', level=2)
    add_styled_paragraph(doc,
        "Integrating Firebase was one of my major milestones. I implemented Firebase Authentication so users "
        "could log in with their Google accounts. Once logged in, their profile is created in Firestore."
    )

    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    hdr_cells = table.rows[0].cells
    hdr_cells[0].text = 'Class Name'
    hdr_cells[1].text = 'My Code Responsibility'

    rows_data = [
        ("AuthRepository.kt", "Handled Firebase login, logout, and token management."),
        ("AppScanner.kt", "Contained the logic to fetch installed apps and verify their signatures."),
        ("CyberLabViewModel.kt", "Managed the state for the gamification feature, fetching questions and adding XP."),
        ("DatabaseHelper.kt", "Managed the Room database setup for offline data storage.")
    ]
    for cls, resp in rows_data:
        row_cells = table.add_row().cells
        row_cells[0].text = cls
        row_cells[1].text = resp

    p = doc.add_paragraph("Table 4.1: Key Classes I Developed")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)
