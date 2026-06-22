"""
Chapters 1 to 6 for Swapnil Chaurasiya's Contribution Report
(General Contributor: Coding, Testing, Documentation, Marketing - Expanded)
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_bullet_list, add_styled_table

def expand_spacing(doc, count=2):
    for _ in range(count):
        doc.add_paragraph("")

def add_chapter1_introduction(doc):
    doc.add_heading('CHAPTER 1: INTRODUCTION & MY MULTI-DISCIPLINARY ROLE', level=1)
    
    doc.add_heading('1.1 Project Motivation', level=2)
    add_styled_paragraph(doc,
        "As part of my diploma studies in Computer Science & Engineering, I sought to understand the complete software "
        "development lifecycle from a holistic perspective. While specialized roles like Lead Developer or QA Tester are essential, "
        "modern Agile environments heavily rely on cross-functional contributors who can bridge the gaps between code, testing, "
        "product documentation, and market strategy."
    )
    add_styled_paragraph(doc,
        "I partnered with my teammates, Jatin Sharma and Vinayak Chourasiya, to build Protctus. My personal motivation was to ensure that "
        "the project remained cohesive. By participating in every phase of development—from writing boilerplate code to executing "
        "usability tests and drafting the user manual—I ensured that our technical implementation aligned perfectly with our end-user requirements."
    )
    expand_spacing(doc)

    doc.add_heading('1.2 The Need for a Cross-Functional Contributor', level=2)
    add_styled_paragraph(doc,
        "In a fast-paced student project, bottlenecks frequently occur. When the lead developer was overwhelmed with backend architecture, "
        "I stepped in to handle frontend UI styling. When the QA lead needed to execute simultaneous device tests, I assisted in running "
        "edge-case scenarios. My objective was to act as the glue holding the technical and non-technical aspects of the project together."
    )
    expand_spacing(doc)

    doc.add_heading('1.3 Overview of My Responsibilities', level=2)
    add_styled_paragraph(doc,
        "Below is a detailed breakdown of my specific cross-functional responsibilities during the project."
    )

    headers = ["Domain", "Specific Tasks", "Impact on Project"]
    rows_data = [
        ["Coding & Development", "Assisted with Jetpack Compose UI state management; debugged Firebase authentication flows.", "Accelerated the development sprint by removing frontend bottlenecks."],
        ["Quality Assurance", "Conducted usability testing; identified logical flaws in the Cyber Lab scoring system.", "Ensured the gamification logic was fair and intuitive for non-technical users."],
        ["Documentation", "Authored the codebase README, API specifications, and the end-user manual.", "Provided a maintainable knowledge base for future updates and user onboarding."],
        ["Marketing & Product", "Coordinated closed beta testing, gathered user feedback, and drafted promotional copy.", "Aligned the final product features with actual market demands."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 1.1: Cross-Functional Responsibilities Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter2_coding(doc):
    doc.add_heading('CHAPTER 2: CODING & DEVELOPMENT CONTRIBUTIONS', level=1)
    
    doc.add_heading('2.1 Pair Programming and Code Review', level=2)
    add_styled_paragraph(doc,
        "While Jatin served as the lead architect, I actively participated in pair programming sessions. We utilized this approach "
        "to tackle the most complex parts of the application, specifically the offline SQLite (Room) database implementation for the App Scanner."
    )
    add_styled_paragraph(doc,
        "My role during these sessions was to act as the 'navigator'. I reviewed the code in real-time, checked for Kotlin best practices, "
        "and ensured that our Coroutine scopes were properly managing background threads to prevent memory leaks."
    )
    expand_spacing(doc)

    doc.add_heading('2.2 Assisting with Jetpack Compose UI', level=2)
    add_styled_paragraph(doc,
        "Transitioning from XML to Jetpack Compose was a learning curve for the entire team. I took ownership of styling several specific UI components."
    )
    
    ui_tasks = [
        "Cyber Lab Cards: Designed the expandable card UI used to display daily cybersecurity questions.",
        "Color Palette Implementation: Defined the global Color.kt file, ensuring the dark mode aesthetic and neon green accents were consistent across all screens.",
        "Responsive Padding: Adjusted Modifiers to ensure the UI did not break on smaller screens (e.g., 4.5-inch displays)."
    ]
    add_bullet_list(doc, ui_tasks)
    expand_spacing(doc)

    doc.add_heading('2.3 Codebase Contributions Log', level=2)
    add_styled_paragraph(doc,
        "To maintain transparency, I logged my specific codebase contributions and refactoring efforts."
    )

    headers = ["Component / File", "My Contribution", "Outcome"]
    rows_data = [
        ["Color.kt / Theme.kt", "Standardized the Material 3 dark theme typography and color hex codes.", "Achieved a uniform 'cyber' aesthetic across the app."],
        ["ScannerViewModel.kt", "Assisted in refactoring the LiveData to StateFlow for better Compose compatibility.", "Reduced UI freezing during large state updates."],
        ["AuthScreen.kt", "Added loading spinners and error snackbars for failed login attempts.", "Improved user feedback during network timeouts."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 2.1: Codebase Contributions and Refactoring Log")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter3_testing(doc):
    doc.add_heading('CHAPTER 3: QUALITY ASSURANCE & TESTING ASSISTANCE', level=1)
    
    doc.add_heading('3.1 Usability and Accessibility Testing', level=2)
    add_styled_paragraph(doc,
        "While Vinayak focused on deep functional and stress testing, I focused on Usability and Accessibility. "
        "A security app is useless if the average user cannot understand how to navigate it. I conducted 'hallway usability testing'—giving the app "
        "to peers outside of the CS department and observing where they got stuck."
    )
    expand_spacing(doc)

    doc.add_heading('3.2 Usability Test Cases Executed', level=2)
    add_styled_paragraph(doc,
        "Based on user observation, I executed and documented several usability-specific test cases."
    )

    headers = ["Test ID", "Usability Scenario", "Observation", "Fix Suggested"]
    rows_data = [
        ["UT-01", "User attempts to interpret a 'Dangerous' URL result.", "Users did not understand the raw JSON API response.", "Suggested converting API response to simple 'Safe' / 'Unsafe' icons."],
        ["UT-02", "User tries to gain XP in the Cyber Lab.", "Users found the questions too technical.", "Assisted in rewriting the questions to be more beginner-friendly."],
        ["UT-03", "User navigates the app in bright sunlight.", "Contrast ratio on gray text was too low.", "Updated Theme.kt to use pure white text for primary headers."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 3.1: Usability Test Cases Executed")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('3.3 Collaborative Bug Resolution', level=2)
    add_styled_paragraph(doc,
        "Because I understood both the codebase and the testing requirements, I acted as a liaison. When Vinayak logged a complex bug regarding "
        "Firebase synchronization, I worked directly with Jatin to trace the stack trace, identify the Coroutine dispatcher issue, and deploy the fix."
    )

    add_page_break(doc)


def add_chapter4_documentation(doc):
    doc.add_heading('CHAPTER 4: TECHNICAL DOCUMENTATION & PROJECT MANAGEMENT', level=1)
    
    doc.add_heading('4.1 Establishing Documentation Standards', level=2)
    add_styled_paragraph(doc,
        "Good code is self-documenting, but a good project requires formal documentation. I took absolute ownership of the project's written materials. "
        "I enforced a standard where every major Kotlin class required a KDoc header, and the GitHub repository required a comprehensive README.md."
    )
    expand_spacing(doc)

    doc.add_heading('4.2 Project Documentation Deliverables', level=2)
    add_styled_paragraph(doc,
        "Throughout the lifecycle, I authored and maintained several living documents."
    )

    headers = ["Document Type", "Target Audience", "Purpose and Content"]
    rows_data = [
        ["Technical Architecture Doc", "Developers / Evaluators", "Detailed the MVVM pattern, Room database schema, and Firebase collections."],
        ["User Manual", "End Users", "A step-by-step guide with screenshots on how to scan apps and use the URL checker."],
        ["API Integration Guide", "Developers", "Documented the endpoints and JSON structures used for the Google Safe Browsing API."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 4.1: Project Documentation Deliverables")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('4.3 Sprint Planning and Task Tracking', level=2)
    add_styled_paragraph(doc,
        "To keep the team on schedule for our diploma submission deadline, I acted in a Scrum Master capacity. "
        "I maintained a Trello board, breaking down the project into weekly sprints, assigning tasks (e.g., 'Fix URL Regex', 'Write Test Cases'), "
        "and tracking our velocity to ensure we did not fall behind."
    )

    add_page_break(doc)


def add_chapter5_marketing(doc):
    doc.add_heading('CHAPTER 5: MARKETING STRATEGY & MARKET ANALYSIS', level=1)
    
    doc.add_heading('5.1 Beta Testing Coordination', level=2)
    add_styled_paragraph(doc,
        "A critical part of our go-to-market strategy was the closed beta phase. I coordinated the distribution of the Protctus APK to a test group "
        "of 20 students. I designed Google Forms to systematically collect their feedback on the app's performance and design."
    )
    expand_spacing(doc)

    doc.add_heading('5.2 Gathering User Feedback', level=2)
    add_styled_paragraph(doc,
        "The data gathered from the beta testers was instrumental in shaping the final version of the app."
    )

    headers = ["Feedback Category", "User Complaint / Suggestion", "Action Taken by Team"]
    rows_data = [
        ["Onboarding", "Users didn't know what the Cyber Lab was.", "Added a 3-screen introductory tutorial on first app launch."],
        ["Performance", "Scan took too long on older phones.", "Added a 'Fast Scan' mode that only checks recently installed apps."],
        ["Design", "Green accent color was too harsh at night.", "Toned down the hex code from #00FF00 to a softer #10B981 (Emerald Green)."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.1: Beta Testing Feedback and Resolutions")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('5.3 Formulating Promotional Messaging', level=2)
    add_styled_paragraph(doc,
        "Working with Vinayak, I helped draft the copy for our promotional materials. We focused on the educational aspect of the app."
    )

    headers = ["Platform", "Content Type", "Messaging Focus"]
    rows_data = [
        ["WhatsApp Groups", "Short Text + APK Link", "'Stop guessing if a link is safe. Scan it instantly with Protctus.'"],
        ["College Forums", "Long-form Post", "Deep dive into how our offline scanner saves battery compared to cloud antiviruses."],
        ["In-App Copy", "Push Notifications", "'Daily Cyber Quest available! Earn 50 XP today.'"]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.2: Marketing Content Strategy Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter6_conclusion(doc):
    doc.add_heading('CHAPTER 6: CONCLUSION & LEARNING OUTCOMES', level=1)
    
    doc.add_heading('6.1 Conclusion', level=2)
    add_styled_paragraph(doc,
        "My multi-disciplinary role in Protctus allowed me to touch every single aspect of the software engineering lifecycle. "
        "By assisting in coding, executing usability tests, managing the documentation, and analyzing beta feedback, I ensured "
        "that the technical implementation perfectly served the end-users' needs. The application stands as a cohesive, polished product "
        "because of our team's cross-functional collaboration."
    )
    expand_spacing(doc)
    
    doc.add_heading('6.2 Personal Learnings in Full-Stack Dynamics', level=2)
    add_styled_paragraph(doc,
        "This project provided an incredible learning curve. Operating as a generalist taught me that:"
    )

    learnings = [
        "Documentation is not an afterthought; it is a critical project management tool that prevents technical debt.",
        "A developer's logic and a user's intuition are rarely the same. Usability testing is mandatory.",
        "Pair programming accelerates bug fixing significantly compared to working in isolated silos.",
        "Marketing an app requires understanding the exact pain points (e.g., battery drain) that competitors are ignoring."
    ]
    add_bullet_list(doc, learnings)
    expand_spacing(doc)

    headers = ["Skill Area", "Competency Gained"]
    rows_data = [
        ["Technical", "Jetpack Compose UI styling, Git version control, basic Kotlin Coroutine debugging."],
        ["Testing", "Usability testing methodologies, structured user observation."],
        ["Management", "Agile sprint tracking (Trello), technical writing, user feedback synthesis."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 6.1: Key Skill Acquisitions")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    add_styled_paragraph(doc,
        "This project successfully fulfills my diploma requirements and demonstrates my capability to operate "
        "effectively in a modern, agile software development team."
    )
    add_page_break(doc)


def add_references_appendix(doc):
    doc.add_heading('REFERENCES', level=1)
    
    refs = [
        "Android Developers Documentation. 'Jetpack Compose Basics.' Available at: https://developer.android.com/jetpack/compose",
        "Atlassian Agile Coach. 'Scrum Master Roles and Responsibilities.'",
        "Nielsen Norman Group. 'Usability Testing 101.' Available at: https://www.nngroup.com/",
        "Google Safe Browsing API Technical Specifications.",
        "IEEE Standard for Software Test Documentation (IEEE Std 829)."
    ]
    for idx, ref in enumerate(refs, 1):
        p = doc.add_paragraph()
        run = p.add_run(f"[{idx}] {ref}")
        run.font.size = Pt(11)
    
    add_page_break(doc)
    doc.add_heading('APPENDIX A: SAMPLE DOCUMENTATION', level=1)
    add_styled_paragraph(doc, "The following is an excerpt from the GitHub README.md documentation that I authored for the project repository.")
    expand_spacing(doc)
    
    readme_text = """
# Protctus - Android Cyber-Security App
## Overview
Protctus is an offline-first malware scanner and gamified cybersecurity learning platform.

## Architecture
- **Frontend**: Jetpack Compose (Material 3)
- **Local DB**: Room SQLite
- **Cloud/Auth**: Firebase Firestore & Authentication
- **Network**: Retrofit & OkHttp

## Build Instructions
1. Clone the repository.
2. Add your `google-services.json` to the `app/` directory.
3. Sync Gradle and build using Android Studio Hedgehog or newer.
    """
    add_styled_paragraph(doc, readme_text, font_name="Consolas", size=9)
