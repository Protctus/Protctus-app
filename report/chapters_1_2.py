"""
Chapters 1 & 2: Introduction & My Role, Research & UI/UX Strategy
"""
import urllib.request
import io
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_code_block, add_bullet_list

def add_web_image(doc, url, width_inches=5.0):
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as response:
            image_stream = io.BytesIO(response.read())
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            run = p.add_run()
            run.add_picture(image_stream, width=Inches(width_inches))
    except Exception as e:
        print(f"  [Warning] Could not load image from {url}: {e}")
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.add_run(f"[Image Placeholder: {url}]").italic = True


def add_chapter1_introduction(doc):
    doc.add_heading('CHAPTER 1: INTRODUCTION & MY ROLE', level=1)
    
    doc.add_heading('1.1 Project Motivation', level=2)
    add_styled_paragraph(doc,
        "During my diploma studies in Computer Science & Engineering, I developed a strong interest in "
        "Android app development and cybersecurity. I noticed that while most people have smartphones, "
        "very few understand the risks of downloading unverified apps or clicking on random links. "
        "I wanted to build an app that wasn't just a basic college project, but a real-world tool that "
        "combines security features with user education."
    )
    add_styled_paragraph(doc,
        "I decided to build Protctus - a personal project to challenge my skills. My motivation was to prove "
        "that I could independently research, design, and code a full-stack Android application from scratch. "
        "I wanted to build an offline malware scanner that doesn't drain the battery, and a gamified learning "
        "platform to make cybersecurity education engaging."
    )

    add_web_image(doc, "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 1.1: Cybersecurity and Data Protection Concept")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    doc.add_heading('1.2 My Objectives as a Solo Developer', level=2)
    add_styled_paragraph(doc,
        "Since I was building this entirely on my own, I set clear objectives to structure my workflow and ensure the app was completed successfully:"
    )
    
    objectives = [
        "Learn and master Kotlin and Jetpack Compose to build a modern, reactive UI.",
        "Design a seamless and intuitive user experience (UI/UX) without relying on external designers.",
        "Implement real logic for an offline malware scanner and a URL phishing checker.",
        "Integrate Firebase to handle user authentication and cloud data storage.",
        "Test and debug the app on physical Android devices to ensure real-world performance."
    ]
    add_bullet_list(doc, objectives)

    doc.add_heading('1.3 Overview of My Responsibilities', level=2)
    add_styled_paragraph(doc,
        "As the sole creator of Protctus, I handled every phase of the software development lifecycle. "
        "Below is a breakdown of my core responsibilities throughout the project."
    )

    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    hdr_cells = table.rows[0].cells
    hdr_cells[0].text = 'Area of Work'
    hdr_cells[1].text = 'My Specific Contributions'

    rows_data = [
        ("Research & Strategy", "Analyzed existing security apps and planned a gamification strategy to keep users engaged."),
        ("UI / UX Design", "Designed wireframes, selected color palettes (dark mode focus), and created the navigation flow."),
        ("Frontend Coding", "Wrote 100% of the UI code using Jetpack Compose and managed state using Kotlin Coroutines."),
        ("Backend & Database", "Set up Firebase Authentication, structured the Firestore database, and wrote local Room database queries."),
        ("Security Logic", "Developed the offline hash-matching logic for the app scanner and the URL verification logic."),
        ("Testing & QA", "Manually tested the app for crashes, fixed UI bugs on different screen sizes, and optimized performance.")
    ]
    
    for area, contribution in rows_data:
        row_cells = table.add_row().cells
        row_cells[0].text = area
        row_cells[1].text = contribution

    p = doc.add_paragraph("Table 1.1: My Core Responsibilities Breakdown")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter2_literature_review(doc):
    doc.add_heading('CHAPTER 2: RESEARCH & UI/UX STRATEGY', level=1)
    
    doc.add_heading('2.1 Market Research', level=2)
    add_styled_paragraph(doc,
        "Before writing any code, I spent a week analyzing existing Android security applications on the Google Play Store, "
        "such as Avast, Malwarebytes, and various cleaner apps. I noticed several common problems:"
    )
    
    problems = [
        "Heavy Battery Drain: Most apps constantly run in the background, slowing down the phone.",
        "Intrusive Ads: Free security apps are often filled with disruptive advertisements.",
        "Scare Tactics: They often use red warning screens to scare users into buying premium features.",
        "Lack of Education: They protect the device but don't teach the user how to stay safe."
    ]
    add_bullet_list(doc, problems)
    
    add_styled_paragraph(doc,
        "Based on this research, my strategy was to build an app that runs scans only when the user requests them, "
        "contains zero ads, and focuses heavily on user education through a 'Cyber Lab' feature."
    )

    doc.add_heading('2.2 UI/UX Design Strategy', level=2)
    add_styled_paragraph(doc,
        "I wanted the application to look professional and modern, similar to premium tools used by actual cybersecurity professionals. "
        "Since I am a developer, not a graphic designer, I kept the UI minimal but effective."
    )
    
    add_web_image(doc, "https://images.unsplash.com/photo-1607252656733-fd742ca4c659?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80", 4.5)
    
    p = doc.add_paragraph("Figure 2.1: My Initial Wireframing and Design Approach")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    design_decisions = [
        "Color Palette: I chose a deep dark theme (black and dark grays) with bright neon green and blue accents. This gives it a 'hacker/cyber' aesthetic.",
        "Typography: I used clean, sans-serif fonts to make the text highly readable on small mobile screens.",
        "Navigation: I implemented a bottom navigation bar with three simple tabs: Dashboard, Cyber Lab, and Settings. This prevents the user from getting lost.",
        "Feedback: I added loading animations and clear success/error messages so the user always knows what the app is doing."
    ]
    add_bullet_list(doc, design_decisions)

    doc.add_heading('2.3 Planning the User Experience (Gamification)', level=2)
    add_styled_paragraph(doc,
        "To solve the problem of users ignoring security education, I researched gamification techniques. "
        "I decided to implement an 'Experience Points (XP)' system. When a user reads an article in the Cyber Lab "
        "or uses the URL checker, they gain XP. As they gain XP, their rank increases (e.g., from 'Novice' to 'Cyber Knight')."
    )

    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    hdr_cells = table.rows[0].cells
    hdr_cells[0].text = 'Design Decision'
    hdr_cells[1].text = 'My Rationale'

    rows_data2 = [
        ("Dark Mode Default", "Reduces eye strain and fits the cybersecurity theme perfectly."),
        ("Jetpack Compose", "Allowed me to build the UI much faster than traditional XML layouts."),
        ("Gamified Lab", "Encourages users to open the app daily and learn security habits."),
        ("Offline Scanning", "Ensures user privacy because app data is not sent to a cloud server.")
    ]
    
    for dec, rat in rows_data2:
        row_cells = table.add_row().cells
        row_cells[0].text = dec
        row_cells[1].text = rat

    p = doc.add_paragraph("Table 2.1: Design Decisions and Rationale")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)
