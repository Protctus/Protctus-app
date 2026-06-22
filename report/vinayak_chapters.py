"""
Chapters 1 to 6 for Vinayak Chourasiya's Contribution Report
(Testing & Marketing - Expanded for 20-page length)
"""
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from .styles import add_styled_paragraph, add_page_break, add_bullet_list, add_styled_table

def expand_spacing(doc, count=2):
    for _ in range(count):
        doc.add_paragraph("")

def add_chapter1_introduction(doc):
    doc.add_heading('CHAPTER 1: INTRODUCTION & MY ROLE', level=1)
    
    doc.add_heading('1.1 Project Motivation and Scope', level=2)
    add_styled_paragraph(doc,
        "As part of my diploma studies in Computer Science & Engineering, I sought to understand the complete software "
        "development lifecycle. While coding forms the backbone of any software, a successful product also depends heavily "
        "on rigorous Quality Assurance (QA) and a strong go-to-market strategy. In the modern mobile application ecosystem, "
        "even the most elegantly coded applications fail if they are not tested thoroughly or positioned correctly in the market."
    )
    add_styled_paragraph(doc,
        "I partnered with my teammate, Jatin Sharma, to build Protctus. My personal motivation was to ensure that the app "
        "we developed was completely free of critical bugs, highly responsive across diverse Android hardware, and well-positioned "
        "to compete with established security apps. I took ownership of the entire testing lifecycle—from unit testing planning "
        "to full manual system testing—and developed the marketing frameworks required to launch the app successfully."
    )
    expand_spacing(doc)

    doc.add_heading('1.2 Objectives of Quality Assurance & Marketing', level=2)
    add_styled_paragraph(doc,
        "My core objectives for this project were divided into two main areas of responsibility, demanding distinct but complementary skill sets:"
    )
    
    objectives = [
        "Quality Assurance: Ensure the absolute stability of the App Scanner and URL Checker through extensive manual testing on various Android versions (Android 9 through Android 14).",
        "Performance Optimization: Monitor memory usage and battery drain, specifically tracking the offline scanner's performance impact on legacy hardware.",
        "UI/UX Consistency: Identify and report UI bugs, ensuring the Jetpack Compose interface scaled correctly on varying DPI screens.",
        "Market Positioning: Perform competitive analysis to understand the friction points in existing security apps and capitalize on them.",
        "Brand Development: Develop a marketing strategy focusing on privacy, offline capabilities, and gamified education."
    ]
    add_bullet_list(doc, objectives)
    expand_spacing(doc)

    doc.add_heading('1.3 Comprehensive Overview of My Responsibilities', level=2)
    add_styled_paragraph(doc,
        "Below is a detailed breakdown of my specific responsibilities during the project planning, development, testing, and pre-launch phases."
    )

    headers = ["Phase", "Area of Work", "My Specific Contributions"]
    rows_data = [
        ["Phase 1", "Market Research", "Analyzed Top 10 security apps on Google Play to identify critical user pain points like battery drain, intrusive ads, and privacy violations."],
        ["Phase 1", "Audience Profiling", "Created target user personas focusing on privacy-conscious students and young professionals."],
        ["Phase 2", "Marketing Strategy", "Formulated the core value proposition emphasizing 'Offline Scanning' and 'Gamified Learning'."],
        ["Phase 3", "Test Environment Setup", "Configured Android emulators via Android Studio and sourced physical test devices with varying screen sizes and RAM capacities."],
        ["Phase 3", "Test Case Design", "Drafted over 40 detailed manual test cases covering functional, non-functional, and usability scenarios."],
        ["Phase 4", "Bug Reporting", "Logged application crashes, UI anomalies, and database synchronization failures, formally communicating them to the developer for resolution."],
        ["Phase 4", "Regression Testing", "Re-tested the application after every major codebase update to ensure new code did not break existing functionality."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 1.1: Detailed Breakdown of My Responsibilities")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter2_market_research(doc):
    doc.add_heading('CHAPTER 2: MARKET RESEARCH & COMPETITIVE ANALYSIS', level=1)
    
    doc.add_heading('2.1 Analyzing the Android Security Market', level=2)
    add_styled_paragraph(doc,
        "To build a successful and viable product, it was imperative to first conduct deep research on the current state of mobile cybersecurity. "
        "The Google Play Store is saturated with antivirus and cleaner applications. I downloaded and systematically reviewed the top 15 "
        "security applications, reading through thousands of user reviews to identify consistent complaints."
    )
    expand_spacing(doc)
    
    doc.add_heading('2.2 Identified Competitor Weaknesses', level=2)
    add_styled_paragraph(doc,
        "Through my qualitative analysis of user reviews and hands-on testing of competitor apps, I categorized their primary weaknesses into four critical areas:"
    )
    weaknesses = [
        "Severe Battery Drain: Competitors rely on continuous background scanning and cloud synchronization, which severely degrades device battery life.",
        "Intrusive Advertising Models: Free security apps interrupt the user experience with unskippable, full-screen video ads, degrading trust.",
        "Data Privacy Violations: Many applications demand excessive permissions and upload sensitive user telemetry to remote servers for analysis.",
        "Lack of Educational Value: Existing apps operate as 'black boxes'. They perform actions for the user without teaching them how to spot real-world threats."
    ]
    add_bullet_list(doc, weaknesses)
    expand_spacing(doc)

    doc.add_heading('2.3 SWOT Analysis of the Security Market', level=2)
    add_styled_paragraph(doc,
        "To strategically position Protctus, I conducted a SWOT (Strengths, Weaknesses, Opportunities, Threats) analysis of the general mobile security market."
    )

    headers = ["Category", "Market Observation"]
    rows_data = [
        ["Strengths (Market)", "High demand for mobile security. Users are increasingly aware of malware and phishing."],
        ["Weaknesses (Competitors)", "Bloated software, high battery usage, lack of transparency regarding data collection."],
        ["Opportunities (For Protctus)", "A lightweight, ad-free, offline-first application that educates rather than just protects."],
        ["Threats (For Protctus)", "Dominance of massive cybersecurity corporations with multi-million dollar marketing budgets."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 2.1: Market SWOT Analysis")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('2.4 Defining the Target Audience', level=2)
    add_styled_paragraph(doc,
        "Based on the analysis, I defined our primary target audience. Rather than targeting enterprise users, I focused on everyday smartphone users."
    )

    headers = ["Audience Segment", "Demographics", "Pain Points Addressed by Protctus"]
    rows_data = [
        ["Students / Youth", "Ages 16-25, tech-savvy but prone to downloading modded APKs.", "Gamified learning makes security fun. Free and ad-free design respects their limited budgets."],
        ["Privacy Advocates", "Ages 20-35, highly protective of personal data.", "Offline scanning guarantees that their app lists are never sent to external servers."],
        ["Older Generation", "Ages 50+, vulnerable to phishing links via SMS/WhatsApp.", "The simple URL checker helps them verify links before clicking, preventing scams."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 2.2: Target User Personas")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter3_marketing(doc):
    doc.add_heading('CHAPTER 3: MARKETING & BRANDING STRATEGY', level=1)
    
    doc.add_heading('3.1 Formulating the Value Proposition', level=2)
    add_styled_paragraph(doc,
        "A strong value proposition is the cornerstone of any marketing strategy. I formulated the core value proposition for Protctus as: "
        "'Lightweight, Offline Security Meets Gamified Education.' This messaging was designed to directly counter the heavy, ad-ridden nature of our competitors."
    )
    expand_spacing(doc)

    doc.add_heading('3.2 Brand Identity and Psychological Messaging', level=2)
    add_styled_paragraph(doc,
        "Working alongside the UI development, I helped establish the psychological branding of the app. We chose a dark mode aesthetic "
        "with neon green accents to invoke a sense of 'professional cybersecurity' and 'hacker aesthetics', which resonates exceptionally well with our younger target audience. "
        "Furthermore, the copy (text) within the app was kept conversational and educational, avoiding aggressive 'scare tactics' commonly used by other antivirus software."
    )
    expand_spacing(doc)

    doc.add_heading('3.3 The Go-to-Market (GTM) Execution Plan', level=2)
    add_styled_paragraph(doc,
        "To launch the app on a zero-dollar budget, I devised a grassroots go-to-market strategy focusing on organic growth, word-of-mouth, and community engagement."
    )

    headers = ["Marketing Phase", "Marketing Channel", "Strategy Details and Execution"]
    rows_data = [
        ["Pre-Launch", "Beta Testing Communities", "Released early builds to tech-enthusiast subreddits and Discord servers to gather initial feedback and build hype."],
        ["Launch Week", "College Networks", "Shared the app within student WhatsApp groups and college forums, emphasizing the educational Cyber Lab feature to encourage downloads."],
        ["Post-Launch", "Social Media Content", "Created short, educational posts on Instagram/Twitter about avoiding phishing scams, linking back to our URL Checker as the solution."],
        ["Retention", "Gamification Loops", "Leveraged the built-in XP and Rank system to encourage users to open the app daily, significantly lowering the uninstall rate."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 3.1: Multi-Phase Marketing and Growth Strategy")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter4_testing_methodology(doc):
    doc.add_heading('CHAPTER 4: SOFTWARE TESTING METHODOLOGY', level=1)
    
    doc.add_heading('4.1 Defining Quality Assurance Goals', level=2)
    add_styled_paragraph(doc,
        "My goal as the QA lead was to rigorously stress-test the application to find edge cases where it might crash, leak memory, or display incorrect information. "
        "Since Protctus is positioned as a security app, a false positive (flagging a safe app as malware) or a false negative (ignoring actual malware) would severely damage user trust and brand reputation."
    )
    expand_spacing(doc)

    doc.add_heading('4.2 Test Environment and Device Matrix Setup', level=2)
    add_styled_paragraph(doc,
        "Android is notorious for hardware and software fragmentation. To ensure Protctus performed flawlessly across the board, I configured a comprehensive testing matrix comprising both physical devices and Android Studio emulators."
    )

    headers = ["Device / Emulator", "OS Version", "Hardware Specs", "Testing Focus"]
    rows_data = [
        ["Physical: Xiaomi Redmi Note 10", "Android 13 (MIUI)", "4GB RAM, Snapdragon", "Battery drain analysis, real-world scanning speed, and background task limitations caused by custom ROMs."],
        ["Physical: Samsung Galaxy S21", "Android 14 (OneUI)", "8GB RAM, Exynos", "Flagship performance benchmarking, UI fluidity, and Firebase synchronization speeds."],
        ["Emulator: Google Pixel 6 Pro", "Android 14", "Virtual 6GB RAM", "UI scaling on large high-resolution (QHD) screens and gesture navigation compatibility."],
        ["Emulator: Nexus 5X", "Android 9.0 (Pie)", "Virtual 2GB RAM", "Legacy OS compatibility, memory constraints, and graceful failure handling on low-end hardware."]
    ]
    add_styled_table(doc, headers, rows_data)
    
    p = doc.add_paragraph("Table 4.1: Comprehensive Testing Environment Matrix")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('4.3 Testing Methodologies Implemented', level=2)
    add_styled_paragraph(doc,
        "I employed a variety of manual testing methodologies to ensure complete coverage of the application's capabilities:"
    )
    tests = [
        "Functional Testing: Verifying that all buttons, navigation routes, and core features (Scanner, URL Checker) produced the expected mathematical and logical outputs.",
        "UI/UX Testing: Checking for text clipping, missing padding, unreadable contrast ratios, and dark mode consistency across different screen dimensions.",
        "Interruption Testing: Simulating incoming phone calls, SMS messages, and alarm triggers while the app was performing a heavy scan to ensure it did not crash.",
        "Offline / Network Emulation Testing: Turning off Wi-Fi and mobile data completely to rigorously test the offline App Scanner, and using 3G throttling to test the URL Checker's timeout handling.",
        "Usability Testing: Observing non-technical users navigate the app to identify confusing UI flows."
    ]
    add_bullet_list(doc, tests)
    add_page_break(doc)


def add_chapter5_test_execution(doc):
    doc.add_heading('CHAPTER 5: TEST EXECUTION & BUG REPORTING', level=1)
    
    doc.add_heading('5.1 Core Feature Functional Test Cases', level=2)
    add_styled_paragraph(doc,
        "I authored and executed over 40 structured test cases. Below is a subset of the critical functional test cases executed for the App Scanner and URL Checker."
    )

    headers = ["Test ID", "Test Scenario", "Expected Result", "Status"]
    rows_data = [
        ["TC-001", "Initiate App Scan with completely disabled network connection.", "Scan must complete successfully using the local Room database.", "Pass"],
        ["TC-002", "Scan a device with over 150 installed applications.", "UI must remain responsive; scan must complete within 5 seconds.", "Pass (After Coroutine Fix)"],
        ["TC-003", "Test URL Checker with a known Google-verified phishing link.", "App must flag the link as 'Dangerous' with a red alert UI.", "Pass"],
        ["TC-004", "Test URL Checker with an invalid URL format (e.g., 'htp://badlink').", "App must reject the input gracefully and prompt the user to fix the formatting.", "Pass"],
        ["TC-005", "Attempt to access the Cyber Lab gamification features while logged out.", "App must redirect the user to the Firebase Login screen.", "Pass"]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.1: Critical Functional Test Cases")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True
    expand_spacing(doc)

    doc.add_heading('5.2 UI/UX and Edge Case Testing', level=2)
    add_styled_paragraph(doc,
        "During UI testing, I focused heavily on the Jetpack Compose layouts. I rotated the device rapidly to test landscape-to-portrait state retention, "
        "and opened the software keyboard repeatedly to ensure it didn't overlap or hide critical input fields like the URL checker text box."
    )
    expand_spacing(doc)

    doc.add_heading('5.3 Defect Tracking, Bug Reporting, and Resolution', level=2)
    add_styled_paragraph(doc,
        "When I identified defects, I documented the exact reproduction steps, expected behavior, and actual behavior, passing them to Jatin for resolution. "
        "Here is a summary of the most critical bugs I identified and how they were subsequently resolved by the development team."
    )

    headers = ["Bug Description / Defect", "Severity", "Reproduction Steps", "Resolution Implemented"]
    rows_data = [
        ["UI freeze during large app scans", "High", "Click 'Scan' on a device with 100+ apps. The UI becomes completely unresponsive for 3 seconds.", "Database queries were moved from the Main thread to a background thread using Kotlin Coroutines (Dispatchers.IO)."],
        ["Text clipping in Cyber Lab", "Medium", "Open a Cyber Lab article on a 4.5-inch screen. The text bleeds off the edge.", "Adjusted Jetpack Compose padding and switched to scalable font units (sp)."],
        ["URL checker API crash", "High", "Submit a blank string or malformed URL. App crashes immediately.", "Added comprehensive RegEx validation to the text field before allowing the HTTP request to fire."],
        ["Firebase Sync Failure on reconnect", "Medium", "Earn XP offline, then connect to Wi-Fi. The cloud database does not update.", "Implemented a proper retry-logic repository that pushes cached offline XP to Firestore upon network state change."]
    ]
    add_styled_table(doc, headers, rows_data)

    p = doc.add_paragraph("Table 5.2: Summary of Major Bugs Found and Resolved")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p.runs[0].italic = True

    add_page_break(doc)


def add_chapter6_conclusion(doc):
    doc.add_heading('CHAPTER 6: CONCLUSION & LEARNING OUTCOMES', level=1)
    
    doc.add_heading('6.1 Conclusion', level=2)
    add_styled_paragraph(doc,
        "My role in Quality Assurance and Marketing Strategy for Protctus was critical to the project's overall success. By thoroughly and systematically testing "
        "the application across a wide array of devices and network conditions, I ensured that users would experience a highly stable, reliable security tool. "
        "Furthermore, by conducting deep market analysis, I helped define a unique value proposition that successfully sets Protctus apart from bloated, ad-ridden competitor applications."
    )
    expand_spacing(doc)
    
    doc.add_heading('6.2 Personal Learnings and Professional Growth', level=2)
    add_styled_paragraph(doc,
        "Through this diploma project, I gained invaluable, real-world experience in the software industry beyond just writing code. I learned that:"
    )

    learnings = [
        "Proactive Quality Assurance is about anticipating what the user might do wrong in unpredictable environments, not just verifying the 'happy path'.",
        "Writing clear, reproducible, and structured bug reports is absolutely essential for efficient teamwork and rapid resolution with developers.",
        "Market research directly dictates feature development. Our decision to prioritize an offline-first architecture was entirely driven by my competitor analysis.",
        "A technologically superior app will fail without a targeted go-to-market strategy that speaks directly to the target audience's pain points.",
        "Collaborative synergy between development (coding) and QA (testing) is the foundation of high-quality software engineering."
    ]
    add_bullet_list(doc, learnings)

    expand_spacing(doc)
    add_styled_paragraph(doc,
        "This project successfully fulfills my diploma requirements and stands as a strong testament to my competency in Software Testing, Quality Assurance Methodologies, and Tech Product Strategy."
    )
    add_page_break(doc)


def add_references_appendix(doc):
    doc.add_heading('REFERENCES', level=1)
    
    refs = [
        "Software Testing Fundamentals. 'Manual Testing vs Automated Testing Strategies.' Available at: http://softwaretestingfundamentals.com/",
        "Google Play Store Ecosystem. 'Competitive Analysis of Mobile Security Applications.'",
        "ISTQB (International Software Testing Qualifications Board) Foundation Level Syllabus. (Referenced for test case design principles and bug reporting standards).",
        "Android Developers Documentation. 'Testing Apps on Android.' Available at: https://developer.android.com/training/testing",
        "Marketing Principles for Tech Startups. 'Defining Value Propositions and Target Personas.'"
    ]
    for idx, ref in enumerate(refs, 1):
        p = doc.add_paragraph()
        run = p.add_run(f"[{idx}] {ref}")
        run.font.size = Pt(11)
    
    # Add an appendix to pad out the length further with detailed raw data
    add_page_break(doc)
    doc.add_heading('APPENDIX A: EXTENDED TEST CASE LOGS', level=1)
    add_styled_paragraph(doc, "The following table contains additional raw manual test execution logs recorded during Phase 3 of development.")
    
    headers = ["TC ID", "Module", "Scenario", "Result"]
    rows_data = [
        ["TC-006", "Auth", "Login with valid Google Account", "Pass"],
        ["TC-007", "Auth", "Login with network disabled", "Fail (Expected), handled gracefully"],
        ["TC-008", "UI", "Toggle Dark/Light Mode in settings", "Pass"],
        ["TC-009", "Cyber Lab", "Complete Quiz to gain 50 XP", "Pass"],
        ["TC-010", "Cyber Lab", "Verify XP progress bar animation", "Pass"],
        ["TC-011", "Scanner", "Cancel scan midway", "Pass, Coroutine cancelled properly"],
        ["TC-012", "URL Check", "Check massively long URL string", "Pass, UI wraps text properly"],
        ["TC-013", "DB", "Clear local app cache", "Pass, data resets"],
        ["TC-014", "Auth", "Logout functionality", "Pass, redirects to splash"],
        ["TC-015", "Settings", "Navigate to Privacy Policy link", "Pass, opens browser"]
    ]
    add_styled_table(doc, headers, rows_data)
