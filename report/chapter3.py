"""
Chapter 3: Architecture & Backend Design
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_code_block, add_bullet_list
from .chapters_1_2 import add_web_image

def add_chapter3_system_design(doc):
    doc.add_heading('CHAPTER 3: ARCHITECTURE & BACKEND DESIGN', level=1)

    doc.add_heading('3.1 Choosing the Tech Stack', level=2)
    add_styled_paragraph(doc,
        "As a solo developer, choosing the right technologies was crucial. I needed a stack that "
        "was modern, allowed for fast UI development, and had reliable backend services that I didn't "
        "have to manage manually on a VPS."
    )

    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    hdr_cells = table.rows[0].cells
    hdr_cells[0].text = 'Component'
    hdr_cells[1].text = 'My Choice & Reason'

    rows_data = [
        ("Programming Language", "Kotlin: It is the recommended language by Google, concise, and null-safe."),
        ("Frontend UI", "Jetpack Compose: Replaced XML. Much faster to write declarative UI."),
        ("Local Database", "Room (SQLite): I needed a fast way to store malware signatures offline."),
        ("Cloud Backend", "Firebase: Handled authentication and user data storage perfectly without me writing custom backend APIs."),
        ("Architecture Pattern", "MVVM (Model-View-ViewModel): Helped me separate my UI code from my database code.")
    ]
    for comp, reason in rows_data:
        row_cells = table.add_row().cells
        row_cells[0].text = comp
        row_cells[1].text = reason

    p = doc.add_paragraph("Table 3.1: Technologies I Selected")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    doc.add_heading('3.2 MVVM Architecture Planning', level=2)
    add_styled_paragraph(doc,
        "I strictly followed the Model-View-ViewModel (MVVM) architecture. This was one of the hardest parts "
        "for me to learn, but it made my code much cleaner."
    )
    
    add_web_image(doc, "https://images.unsplash.com/photo-1555066931-4365d14bab8c?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 3.1: MVVM Architecture Diagram I Used")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    mvvm_points = [
        "Model: I set up Repository classes to fetch data either from Room (offline) or Firebase (online).",
        "ViewModel: I wrote ViewModels to hold the state. For example, my ScannerViewModel tracks the progress of an app scan.",
        "View: My Jetpack Compose functions observe the ViewModel. If the scan finishes, the UI updates automatically."
    ]
    add_bullet_list(doc, mvvm_points)

    doc.add_heading('3.3 Designing the Firebase Backend', level=2)
    add_styled_paragraph(doc,
        "For the gamification system to work, I needed to store user profiles, XP, and ranks in the cloud. "
        "I designed a NoSQL database structure using Firebase Firestore."
    )

    add_web_image(doc, "https://images.unsplash.com/photo-1544197150-b99a580bb7a8?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)

    p = doc.add_paragraph("Figure 3.2: My Firebase Database Structure")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_styled_paragraph(doc,
        "My database collections were structured as follows:"
    )
    
    db_points = [
        "Users Collection: Stores user_id, email, current_xp, and current_rank.",
        "Quests Collection: Stores daily cybersecurity questions that users answer to earn XP.",
        "Scan Logs Collection: Stores a history of malicious URLs the user tried to open, for their personal record."
    ]
    add_bullet_list(doc, db_points)

    add_styled_paragraph(doc,
        "This architecture ensured that even if the user changes devices, their progress in the Cyber Lab is safely synced from the cloud."
    )

    add_page_break(doc)
