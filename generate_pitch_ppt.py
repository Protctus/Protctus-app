"""
Protctus — Complete Hackathon Pitch Deck (3-5 min, 10 Slides)
Premium dark design using EXACT Protctus brand colors.
Every slide is content-dense with full technical detail.
"""

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE

# ─── EXACT Protctus Brand Colors (from Color.kt & HomeScreen.kt) ───
BG_DARK     = RGBColor(0x1C, 0x19, 0x19)
BG_DEEPER   = RGBColor(0x14, 0x12, 0x12)
SURFACE     = RGBColor(0x2A, 0x2B, 0x31)
CARD_ALT    = RGBColor(0x22, 0x22, 0x26)
CARD_GRAD   = RGBColor(0x2D, 0x2E, 0x35)
FINDING_BG  = RGBColor(0x1A, 0x1B, 0x1F)
PRIMARY_TXT = RGBColor(0xE3, 0xE3, 0xE3)
ACCENT      = RGBColor(0x7F, 0x90, 0x9F)
SECONDARY   = RGBColor(0x4A, 0x56, 0x68)
MUTED       = RGBColor(0x8E, 0x8F, 0x93)
ERROR       = RGBColor(0xEF, 0x44, 0x44)
SUCCESS     = RGBColor(0x10, 0xB9, 0x81)
WARN_RED    = RGBColor(0xFF, 0x4C, 0x4C)
WARN_ORG    = RGBColor(0xFF, 0x8C, 0x42)
WARN_YEL    = RGBColor(0xFF, 0xC1, 0x07)
WARN_TEAL   = RGBColor(0x4D, 0xB6, 0xAC)
ISSUES_RED  = RGBColor(0xFF, 0x6B, 0x6B)
WHITE       = RGBColor(0xFF, 0xFF, 0xFF)
CYAN_GLOW   = RGBColor(0x56, 0xCC, 0xF2)

prs = Presentation()
prs.slide_width  = Inches(13.333)
prs.slide_height = Inches(7.5)
W, H = prs.slide_width, prs.slide_height
TOTAL = 10

# ─── Helper Functions ───

def mk():
    s = prs.slides.add_slide(prs.slide_layouts[6])
    s.background.fill.solid()
    s.background.fill.fore_color.rgb = BG_DARK
    return s

def rect(s, l, t, w, h, c, bc=None, bw=Pt(0)):
    sh = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, l, t, w, h)
    sh.fill.solid(); sh.fill.fore_color.rgb = c
    if bc: sh.line.color.rgb = bc; sh.line.width = bw
    else: sh.line.fill.background()
    sh.adjustments[0] = 0.04
    return sh

def flat_rect(s, l, t, w, h, c):
    sh = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, l, t, w, h)
    sh.fill.solid(); sh.fill.fore_color.rgb = c; sh.line.fill.background()
    return sh

def accent_bar(s, l, t, w, c=ACCENT):
    flat_rect(s, l, t, w, Pt(3), c)

def tb(s, l, t, w, h):
    tx = s.shapes.add_textbox(l, t, w, h); tx.text_frame.word_wrap = True
    return tx.text_frame

def txt(tf, text, sz=18, b=False, c=PRIMARY_TXT, a=PP_ALIGN.LEFT, sa=Pt(6), fn="Segoe UI"):
    if tf.paragraphs[0].text:
        pr = tf.add_paragraph()
    else:
        pr = tf.paragraphs[0]
    pr.text = text; pr.font.size = Pt(sz); pr.font.bold = b; pr.font.color.rgb = c
    pr.font.name = fn; pr.alignment = a; pr.space_after = sa
    return pr

def add(tf, text, sz=18, b=False, c=PRIMARY_TXT, a=PP_ALIGN.LEFT, sa=Pt(6), fn="Segoe UI"):
    pr = tf.add_paragraph()
    pr.text = text; pr.font.size = Pt(sz); pr.font.bold = b; pr.font.color.rgb = c
    pr.font.name = fn; pr.alignment = a; pr.space_after = sa
    return pr

def sn(s, n):
    tf = tb(s, W - Inches(1.5), H - Inches(0.55), Inches(1.2), Inches(0.4))
    txt(tf, f"{n} / {TOTAL}", sz=10, c=SECONDARY, a=PP_ALIGN.RIGHT)

def header(s, title, sub, n):
    accent_bar(s, Inches(0.9), Inches(0.65), Inches(1.8), ACCENT)
    tf = tb(s, Inches(0.9), Inches(0.85), Inches(11), Inches(0.9))
    txt(tf, title, sz=30, b=True, c=WHITE, fn="Segoe UI Semibold")
    if sub:
        add(tf, sub, sz=15, c=ACCENT)
    sn(s, n)

def dot(s, x, y, c, size=Inches(0.12)):
    sh = s.shapes.add_shape(MSO_SHAPE.OVAL, x, y, size, size)
    sh.fill.solid(); sh.fill.fore_color.rgb = c; sh.line.fill.background()

def stat_card(s, x, y, w, h, value, label, color):
    """Small metric card with big number + label."""
    card = rect(s, x, y, w, h, CARD_ALT, color, Pt(1))
    flat_rect(s, x, y, w, Pt(3), color)
    tf = tb(s, x + Inches(0.15), y + Inches(0.15), w - Inches(0.3), Inches(0.5))
    txt(tf, value, sz=24, b=True, c=color, a=PP_ALIGN.CENTER, fn="Segoe UI Black")
    tf2 = tb(s, x + Inches(0.15), y + Inches(0.55), w - Inches(0.3), Inches(0.35))
    txt(tf2, label, sz=10, c=MUTED, a=PP_ALIGN.CENTER)

def bullet_block(tf, items, sz=12, c=PRIMARY_TXT, bullet="►", sa=Pt(5)):
    """Add multiple bullet points to a text frame."""
    for item in items:
        add(tf, f"  {bullet}  {item}", sz=sz, c=c, sa=sa)

def section_label(s, x, y, text, color):
    """Small colored label tag."""
    badge = rect(s, x, y, Inches(1.6), Inches(0.32), color)
    tf = tb(s, x, y + Inches(0.02), Inches(1.6), Inches(0.28))
    txt(tf, text, sz=10, b=True, c=BG_DARK, a=PP_ALIGN.CENTER, fn="Segoe UI Semibold")


# ═════════════════════════════════════════════════════════════════════
# SLIDE 1: TITLE — Maximum Impact
# ═════════════════════════════════════════════════════════════════════
s = mk()

# Top accent line
flat_rect(s, Inches(0), Inches(0), W, Pt(4), ACCENT)

# Brand name — large and centered
tf = tb(s, Inches(0.5), Inches(1.0), Inches(12.3), Inches(2.0))
txt(tf, "PROTCTUS", sz=84, b=True, c=WHITE, a=PP_ALIGN.CENTER, fn="Segoe UI Black")
add(tf, "India's First Zero-Trust, Privacy-First", sz=24, c=PRIMARY_TXT, a=PP_ALIGN.CENTER, fn="Segoe UI Light")
add(tf, "Mobile Security Ecosystem", sz=24, c=PRIMARY_TXT, a=PP_ALIGN.CENTER, fn="Segoe UI Light")

# Divider
accent_bar(s, Inches(5.5), Inches(3.8), Inches(2.3), ACCENT)

# Core pillars
tf2 = tb(s, Inches(1.0), Inches(4.2), Inches(11.3), Inches(0.6))
txt(tf2, "Offline-First   ·   AI-Powered   ·   Hardware-Aware   ·   Gamified   ·   40+ Screens", sz=17, c=ACCENT, a=PP_ALIGN.CENTER)

# Quick stats bar
stats = [
    ("5+", "Security\nEngines", WARN_RED),
    ("O(1)", "Threat\nLookup", SUCCESS),
    ("~4ms", "DNS\nLatency", CYAN_GLOW),
    ("64KB", "Signature\nDB Size", WARN_YEL),
    ("100%", "Offline\nCapable", ACCENT),
    ("40+", "App\nScreens", WARN_ORG),
]
for i, (val, label, color) in enumerate(stats):
    x = Inches(1.0) + Inches(i * 1.95)
    stat_card(s, x, Inches(5.1), Inches(1.7), Inches(0.9), val, label, color)

# Tech stack footer
tf3 = tb(s, Inches(1.0), Inches(6.35), Inches(11.3), Inches(0.5))
txt(tf3, "Kotlin  ·  Jetpack Compose  ·  TensorFlow Lite  ·  Room DB  ·  Firebase", sz=12, c=SECONDARY, a=PP_ALIGN.CENTER)

# Bottom accent
flat_rect(s, Inches(0), H - Pt(4), W, Pt(4), ACCENT)
sn(s, 1)


# ═════════════════════════════════════════════════════════════════════
# SLIDE 2: THE PROBLEM — Threat Landscape
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "The Problem", "Smartphones carry banking, identity, private data — yet mobile security is broken", 2)

problems = [
    ("Juice Jacking", "Public Charging",
     "Hostile USB connections silently exfiltrate data or inject payloads via public kiosks",
     "Nobody detects this", WARN_RED),
    ("Stalkerware", "Commercial Spyware",
     "Commercially available spying apps track SMS, GPS, camera — often completely invisible",
     "Basic app list scans miss them", WARN_ORG),
    ("Ghost Apps", "Invisible Malware",
     "Malware hides without a launcher icon, runs headlessly with SMS/Accessibility permissions",
     "Completely undetectable to users", WARN_RED),
    ("Zero-Day Malware", "Polymorphic Payloads",
     "New malware variants bypass all known cloud signature databases instantly",
     "Legacy AVs require cloud connectivity", WARN_YEL),
    ("Phishing", "Credential Harvesting",
     "Spoofed login pages steal banking credentials in seconds via convincing fake UIs",
     "Browser-level checks are easily bypassed", WARN_ORG),
]

for i, (title, category, desc, gap, sev_color) in enumerate(problems):
    y = Inches(2.0) + Inches(i * 1.0)
    card = rect(s, Inches(0.9), y, Inches(11.5), Inches(0.88), CARD_ALT, SECONDARY, Pt(0.5))
    
    # Severity indicator bar on left edge
    flat_rect(s, Inches(0.9), y, Pt(4), Inches(0.88), sev_color)
    
    # Severity dot
    dot(s, Inches(1.2), y + Inches(0.35), sev_color)
    
    # Title + category
    tf = tb(s, Inches(1.5), y + Inches(0.08), Inches(2.5), Inches(0.35))
    txt(tf, title, sz=16, b=True, c=WHITE)
    
    tf_cat = tb(s, Inches(1.5), y + Inches(0.48), Inches(2.5), Inches(0.3))
    txt(tf_cat, category, sz=10, c=sev_color)
    
    # Description
    tf2 = tb(s, Inches(4.2), y + Inches(0.1), Inches(4.8), Inches(0.35))
    txt(tf2, desc, sz=12, c=MUTED)
    
    # Current gap
    tf3 = tb(s, Inches(9.2), y + Inches(0.1), Inches(3.0), Inches(0.7))
    txt(tf3, f"Gap: {gap}", sz=11, b=True, c=WARN_RED)

# Bottom verdict
card_v = rect(s, Inches(0.9), Inches(7.0), Inches(11.5), Inches(0.38), FINDING_BG, WARN_RED, Pt(1))
tf_v = tb(s, Inches(0.9), Inches(7.02), Inches(11.5), Inches(0.35))
txt(tf_v, "Traditional mobile security is reactive, cloud-dependent, resource-heavy, and trivially bypassable.", sz=13, b=True, c=WARN_RED, a=PP_ALIGN.CENTER)


# ═════════════════════════════════════════════════════════════════════
# SLIDE 3: THE SOLUTION — Protctus Overview
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "The Solution: Protctus", "Privacy-first Android security ecosystem — Kotlin + Jetpack Compose, Offline-first", 3)

# Core philosophy boxes
pillars = [
    ("OFFLINE-FIRST", "Every core engine works\nwithout internet. No user\ndata leaves the device.", SUCCESS),
    ("ZERO-TRUST", "Multi-layer threat\ndetection across hardware,\nnetwork & application.", CYAN_GLOW),
    ("AI-POWERED", "TensorFlow Lite on-device\nfor rapid classification.", ACCENT),
    ("PRIVACY-FIRST", "Zero telemetry. No cloud\nuploads for scanning.\n100% local processing.", WARN_YEL),
]

for i, (title, desc, color) in enumerate(pillars):
    x = Inches(0.9) + Inches(i * 3.1)
    card = rect(s, x, Inches(1.9), Inches(2.8), Inches(1.8), CARD_ALT, color, Pt(1))
    flat_rect(s, x, Inches(1.9), Inches(2.8), Pt(3), color)
    
    tf = tb(s, x + Inches(0.2), Inches(2.1), Inches(2.4), Inches(0.35))
    txt(tf, title, sz=13, b=True, c=color, a=PP_ALIGN.CENTER, fn="Segoe UI Semibold")
    
    tf2 = tb(s, x + Inches(0.2), Inches(2.55), Inches(2.4), Inches(1.0))
    txt(tf2, desc, sz=11, c=MUTED, a=PP_ALIGN.CENTER)

# Full feature list in two columns
left_features = [
    ("Anti-Juice Jacking Engine", "Real-time USB monitoring, multi-factor threat matrix", WARN_RED),
    ("Stalkerware Detection", "4-rule heuristic matrix, score-based flagging", WARN_ORG),
    ("Ghost App Detection", "Hunts headless apps with no launcher icon", ACCENT),
    ("Proc Malware Engine", "Behavioral analysis catches zero-days", WHITE),
    ("MurmurHash3 + Bloom Filter", "O(1) signature lookups, 64KB filter, ~36K sigs", SUCCESS),
    ("Real-Time Sentinel Service", "Always-on install interception, START_STICKY", CYAN_GLOW),
]

right_features = [
    ("Local VPN DNS Sinkhole", "3-tier phishing shield at the network layer", SUCCESS),
    ("Advanced Typosquatting Engine", "Homoglyph & Levenshtein distance on-device", ACCENT),
    ("Deep Scan + Smart Scan", "Full filesystem traversal + quick app scan", WHITE),
    ("Security Posture Audit", "100-point health check, 16+ risk vectors", WARN_YEL),
    ("Gamification Layer", "Quests, Labs, Leaderboard, XP, Rewards", WARN_ORG),
    ("Dark Web Scan", "Active identity breach monitoring & remediation", CYAN_GLOW),
]

# Left column
rect(s, Inches(0.9), Inches(4.0), Inches(5.9), Inches(3.35), FINDING_BG, SECONDARY, Pt(0.5))
flat_rect(s, Inches(0.9), Inches(4.0), Pt(4), Inches(3.35), ACCENT)
for i, (name, desc, color) in enumerate(left_features):
    y = Inches(4.1) + Inches(i * 0.53)
    tf = tb(s, Inches(1.3), y, Inches(2.2), Inches(0.25))
    txt(tf, name, sz=11, b=True, c=color, sa=Pt(0))
    tf2 = tb(s, Inches(3.6), y, Inches(3.0), Inches(0.25))
    txt(tf2, desc, sz=10, c=MUTED, sa=Pt(0))

# Right column
rect(s, Inches(7.1), Inches(4.0), Inches(5.3), Inches(3.35), FINDING_BG, SECONDARY, Pt(0.5))
flat_rect(s, Inches(7.1), Inches(4.0), Pt(4), Inches(3.35), ACCENT)
for i, (name, desc, color) in enumerate(right_features):
    y = Inches(4.1) + Inches(i * 0.53)
    tf = tb(s, Inches(7.4), y, Inches(2.0), Inches(0.25))
    txt(tf, name, sz=11, b=True, c=color, sa=Pt(0))
    tf2 = tb(s, Inches(9.5), y, Inches(2.7), Inches(0.25))
    txt(tf2, desc, sz=10, c=MUTED, sa=Pt(0))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 4: CORE DETECTION ENGINES (The Money Slide)
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "Core Detection Engines", "5 engines working together — all offline, all on-device", 4)

# ── Engine 1: Anti-Juice Jacking ──
rect(s, Inches(0.9), Inches(1.85), Inches(5.8), Inches(2.55), CARD_ALT, WARN_RED, Pt(1))
flat_rect(s, Inches(0.9), Inches(1.85), Inches(5.8), Pt(3), WARN_RED)

tf = tb(s, Inches(1.15), Inches(2.05), Inches(5.4), Inches(2.3))
txt(tf, "1. Anti-Juice Jacking Engine", sz=16, b=True, c=WARN_RED, fn="Segoe UI Semibold")
add(tf, "Real-time USB monitoring via system broadcast receivers", sz=11, c=MUTED, sa=Pt(3))
add(tf, "", sz=4, sa=Pt(0))
bullet_block(tf, [
    "Detects data transfer capability (not just charging)",
    "Host computer detection (ADB exploitation vector)",
    "ADB debugging state + device lock status check",
    "Cumulative Risk Score — CRITICAL (≥80) triggers:",
    "   SOS vibration + priority-max notification + Threat Feed log",
], sz=11, c=PRIMARY_TXT, sa=Pt(2))

# ── Engine 2: Stalkerware Detection ──
rect(s, Inches(7.0), Inches(1.85), Inches(5.4), Inches(2.55), CARD_ALT, WARN_ORG, Pt(1))
flat_rect(s, Inches(7.0), Inches(1.85), Inches(5.4), Pt(3), WARN_ORG)

tf2 = tb(s, Inches(7.25), Inches(2.05), Inches(5.0), Inches(2.3))
txt(tf2, "2. Stalkerware Detection", sz=16, b=True, c=WARN_ORG, fn="Segoe UI Semibold")
add(tf2, "4-rule heuristic scoring matrix per app", sz=11, c=MUTED, sa=Pt(3))
add(tf2, "", sz=4, sa=Pt(0))
bullet_block(tf2, [
    "Sideloaded Install (not from Play Store)     +40",
    "Accessibility / Device Admin requests           +30",
    "Hidden Launcher Icon (invisible to user)       +40",
    "Toxic Permission Cocktail (Cam+Mic+SMS+Loc) +10/ea",
    "Score > 70 = flagged · User whitelist · Deduplication",
], sz=11, c=PRIMARY_TXT, sa=Pt(2))

# ── Engine 3: Ghost App Detection ──
rect(s, Inches(0.9), Inches(4.6), Inches(3.7), Inches(2.7), CARD_ALT, ACCENT, Pt(1))
flat_rect(s, Inches(0.9), Inches(4.6), Inches(3.7), Pt(3), ACCENT)

tf3 = tb(s, Inches(1.15), Inches(4.8), Inches(3.3), Inches(2.4))
txt(tf3, "3. Ghost App Detection", sz=14, b=True, c=ACCENT, fn="Segoe UI Semibold")
add(tf3, "", sz=3, sa=Pt(0))
bullet_block(tf3, [
    "Headless Payload Dropper",
    "Headless Surveillance Engine",
    "Headless Remote Receiver Hook",
    "Ghost Executable (no code flag)",
    "No launcher icon + dangerous perms",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

# ── Engine 4: Proc Malware Engine ──
rect(s, Inches(4.8), Inches(4.6), Inches(4.2), Inches(2.7), CARD_ALT, WHITE, Pt(1))
flat_rect(s, Inches(4.8), Inches(4.6), Inches(4.2), Pt(3), WHITE)

tf4 = tb(s, Inches(5.05), Inches(4.8), Inches(3.8), Inches(2.4))
txt(tf4, "4. Proc Malware Engine", sz=14, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf4, "App-Level:", sz=10, b=True, c=ACCENT, sa=Pt(1))
bullet_block(tf4, [
    "Triad Signature (Overlay+Access+Boot)",
    "Crypto anomaly (debug keys on sideloads)",
    "IPC backdoor (exported services no guard)",
], sz=10, c=PRIMARY_TXT, sa=Pt(1))
add(tf4, "File-Level:", sz=10, b=True, c=ACCENT, sa=Pt(1))
bullet_block(tf4, [
    "Disguised payloads (dex in .pdf/.jpg)",
    "Double-extension (invoice.pdf.apk)",
    "85% threshold to minimize false positives",
], sz=10, c=PRIMARY_TXT, sa=Pt(1))

# ── Engine 5: MurmurHash3 + Bloom Filter ──
rect(s, Inches(9.2), Inches(4.6), Inches(3.2), Inches(2.7), CARD_ALT, SUCCESS, Pt(1))
flat_rect(s, Inches(9.2), Inches(4.6), Inches(3.2), Pt(3), SUCCESS)

tf5 = tb(s, Inches(9.45), Inches(4.8), Inches(2.8), Inches(2.4))
txt(tf5, "5. Bloom Filter", sz=14, b=True, c=SUCCESS, fn="Segoe UI Semibold")
add(tf5, "MurmurHash3 Signatures", sz=10, c=MUTED, sa=Pt(3))
add(tf5, "", sz=3, sa=Pt(0))
bullet_block(tf5, [
    "64KB filter size",
    "~36,000 signatures",
    "<1% false positive rate",
    "Sub-millisecond lookup",
    "3 hashes × 3 seeds",
    "O(1) — zero network",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 5: NETWORK DEFENSE — VPN + AI URL Scanner
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "Network Defense Layer", "Local VPN DNS Sinkhole + Cloudflare RBI AI URL Scanner", 5)

# ── LEFT: VPN DNS Sinkhole 3-Tier Architecture ──
rect(s, Inches(0.9), Inches(1.85), Inches(7.0), Inches(5.3), CARD_ALT, SUCCESS, Pt(1))
flat_rect(s, Inches(0.9), Inches(1.85), Inches(7.0), Pt(3), SUCCESS)

tf = tb(s, Inches(1.2), Inches(2.05), Inches(6.5), Inches(0.5))
txt(tf, "Local VPN Phishing Shield (DNS Sinkhole)", sz=18, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf, "All DNS requests route through 3-tier defense via Android VpnService", sz=11, c=MUTED)

# Tier 1
tier_y = Inches(2.85)
rect(s, Inches(1.15), tier_y, Inches(6.5), Inches(1.2), FINDING_BG, SUCCESS, Pt(0.5))
section_label(s, Inches(1.3), tier_y + Inches(0.08), "TIER 1 — ALLOW", SUCCESS)
tf1 = tb(s, Inches(1.3), tier_y + Inches(0.45), Inches(6.2), Inches(0.7))
txt(tf1, "VVIP Allowlist — Tranco Top 380K legitimate domains in Room DB with B-tree indexing + LRU cache (500 entries)", sz=11, c=PRIMARY_TXT, sa=Pt(1))
add(tf1, "Includes Indian banking & government: SBI, PhonePe, DigiLocker, NPCI, IRCTC — safe domains pass instantly", sz=10, c=MUTED, sa=Pt(0))

# Tier 2
tier_y = Inches(4.2)
rect(s, Inches(1.15), tier_y, Inches(6.5), Inches(1.0), FINDING_BG, WARN_RED, Pt(0.5))
section_label(s, Inches(1.3), tier_y + Inches(0.08), "TIER 2 — BLOCK", WARN_RED)
tf2 = tb(s, Inches(1.3), tier_y + Inches(0.45), Inches(6.2), Inches(0.5))
txt(tf2, "Known phishing domains checked via MurmurHash3 Bloom Filter in O(1) — matches sinkholed to 0.0.0.0 instantly", sz=11, c=PRIMARY_TXT, sa=Pt(0))

# Tier 3
tier_y = Inches(5.35)
rect(s, Inches(1.15), tier_y, Inches(6.5), Inches(1.1), FINDING_BG, ACCENT, Pt(0.5))
section_label(s, Inches(1.3), tier_y + Inches(0.08), "TIER 3 — ANALYZE", ACCENT)
tf3 = tb(s, Inches(1.3), tier_y + Inches(0.45), Inches(6.2), Inches(0.6))
txt(tf3, "Ambiguous domains → TensorFlow Lite URL character-sequence analysis entirely on-device", sz=11, c=PRIMARY_TXT, sa=Pt(1))
add(tf3, "No Accessibility Services. No battery drain. No screen reading required.", sz=10, c=SUCCESS, sa=Pt(0))

# Latency badge
rect(s, Inches(1.8), Inches(6.6), Inches(3.0), Inches(0.4), SUCCESS)
tf_lat = tb(s, Inches(1.8), Inches(6.62), Inches(3.0), Inches(0.35))
txt(tf_lat, "Added Latency: ~4ms", sz=13, b=True, c=BG_DARK, a=PP_ALIGN.CENTER, fn="Segoe UI Semibold")

# ── RIGHT: AI URL Scanner ──
rect(s, Inches(8.2), Inches(1.85), Inches(4.2), Inches(5.3), CARD_ALT, CYAN_GLOW, Pt(1))
flat_rect(s, Inches(8.2), Inches(1.85), Inches(4.2), Pt(3), CYAN_GLOW)

tf_ai = tb(s, Inches(8.45), Inches(2.05), Inches(3.7), Inches(5.0))
txt(tf_ai, "Typosquatting Engine", sz=18, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf_ai, "100% On-Device OpenSquat", sz=11, c=CYAN_GLOW, sa=Pt(8))

add(tf_ai, "Homoglyph Normalization:", sz=11, b=True, c=ACCENT, sa=Pt(3))
bullet_block(tf_ai, [
    "Detects Cyrillic/Greek character spoofing",
    "Normalizes visually similar domains",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

add(tf_ai, "Levenshtein Distance:", sz=11, b=True, c=ACCENT, sa=Pt(3))
bullet_block(tf_ai, [
    "Compares against Top 500 targeted brands",
    "Catches spelling mistakes (e.g. faceb00k)",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

add(tf_ai, "Local VpnService Implementation:", sz=11, b=True, c=ACCENT, sa=Pt(3))
bullet_block(tf_ai, [
    "Intercepts DNS traffic via TUN interface",
    "Zero reliance on cloud processing",
    "Minimal battery drain",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 6: REAL-TIME + SCANNING + POSTURE
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "Protection & Intelligence", "Always-on guardian + deep scanning + security health audit", 6)

# ── LEFT: Sentinel Service ──
rect(s, Inches(0.9), Inches(1.85), Inches(3.8), Inches(3.8), CARD_ALT, ACCENT, Pt(1))
flat_rect(s, Inches(0.9), Inches(1.85), Pt(4), Inches(3.8), ACCENT)

tf = tb(s, Inches(1.2), Inches(2.0), Inches(3.4), Inches(3.5))
txt(tf, "Sentinel Service", sz=17, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf, "Always-On Real-Time Guardian", sz=10, c=ACCENT, sa=Pt(6))
bullet_block(tf, [
    "Listens ACTION_PACKAGE_ADDED",
    "Every new install → instant scan",
    "Ghost App + Heuristic engine",
    "Full-screen threat alert if flagged",
    "START_STICKY — auto-restarts",
], sz=11, c=PRIMARY_TXT, sa=Pt(3))

# ── CENTER: Scan Engines ──
rect(s, Inches(4.9), Inches(1.85), Inches(3.8), Inches(3.8), CARD_ALT, WARN_YEL, Pt(1))
flat_rect(s, Inches(4.9), Inches(1.85), Pt(4), Inches(3.8), WARN_YEL)

tf2 = tb(s, Inches(5.2), Inches(2.0), Inches(3.4), Inches(3.5))
txt(tf2, "Scan Engines", sz=17, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf2, "Two Depths + Dark Web", sz=10, c=WARN_YEL, sa=Pt(6))

add(tf2, "Deep Scan:", sz=10, b=True, c=WARN_ORG, sa=Pt(1))
bullet_block(tf2, [
    "Absolute filesystem traversal from /",
    "WakeLock + pause/resume support",
    "Phase 1: Apps → Phase 2: Files",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

add(tf2, "Smart Scan:", sz=10, b=True, c=SUCCESS, sa=Pt(1))
bullet_block(tf2, [
    "Non-system apps + Downloads dir",
    "Completes Daily Quest for XP",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

add(tf2, "Dark Web:", sz=10, b=True, c=WARN_RED, sa=Pt(1))
bullet_block(tf2, [
    "Email breach check + remediation",
], sz=10, c=PRIMARY_TXT, sa=Pt(2))

# ── RIGHT: Security Posture Audit ──
rect(s, Inches(8.9), Inches(1.85), Inches(3.5), Inches(3.8), CARD_ALT, SUCCESS, Pt(1))
flat_rect(s, Inches(8.9), Inches(1.85), Pt(4), Inches(3.8), SUCCESS)

tf3 = tb(s, Inches(9.2), Inches(2.0), Inches(3.1), Inches(3.5))
txt(tf3, "Posture Audit", sz=17, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf3, "100-Point Security Health Check", sz=10, c=SUCCESS, sa=Pt(6))

add(tf3, "Device:", sz=10, b=True, c=ACCENT, sa=Pt(1))
bullet_block(tf3, [
    "ADB, Unknown Sources, Root",
    "Emulator (15 build props)",
    "Screen Lock, Accessibility",
], sz=10, c=PRIMARY_TXT, sa=Pt(1))

add(tf3, "Network:", sz=10, b=True, c=ACCENT, sa=Pt(1))
bullet_block(tf3, [
    "HTTP Proxy, CA Certs (MITM)",
    "VPN, WiFi encryption, DNS",
], sz=10, c=PRIMARY_TXT, sa=Pt(1))

add(tf3, "Auto: 24h via WorkManager", sz=9, c=WARN_YEL, sa=Pt(0))

# ── BOTTOM ROW: Threat Feed + PDF Report ──
rect(s, Inches(0.9), Inches(5.85), Inches(5.8), Inches(1.4), CARD_ALT, WARN_ORG, Pt(1))
flat_rect(s, Inches(0.9), Inches(5.85), Pt(4), Inches(1.4), WARN_ORG)

tf4 = tb(s, Inches(1.2), Inches(5.95), Inches(5.4), Inches(1.2))
txt(tf4, "Threat Feed & Resolution", sz=15, b=True, c=WHITE)
add(tf4, "Unified timeline of every security event across all engines — Juice Jacking, Stalkerware, scans, posture.", sz=11, c=MUTED, sa=Pt(2))
add(tf4, "Color-coded priority · Timestamp · One-tap resolution · Direct app uninstall for flagged packages", sz=11, c=PRIMARY_TXT, sa=Pt(0))

rect(s, Inches(6.9), Inches(5.85), Inches(5.5), Inches(1.4), CARD_ALT, ACCENT, Pt(1))
flat_rect(s, Inches(6.9), Inches(5.85), Pt(4), Inches(1.4), ACCENT)

tf5 = tb(s, Inches(7.2), Inches(5.95), Inches(5.1), Inches(1.2))
txt(tf5, "PDF Security Report", sz=15, b=True, c=WHITE)
add(tf5, "Branded, exportable PDF with complete scan results, threat findings, and posture score.", sz=11, c=MUTED, sa=Pt(2))
add(tf5, "Suitable for enterprise compliance audits or personal security records.", sz=11, c=PRIMARY_TXT, sa=Pt(0))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 7: ENGAGEMENT & EDUCATION
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "Engagement & Education", "Security that keeps users engaged, learning, and coming back", 7)

# Large feature cards - top row
engage_top = [
    ("Daily Quests", "Dynamic Quest Generation",
     ["Run Smart Scan, check URL, complete quiz",
      "XP rewards for security-positive actions",
      "Progress tracking with completion badges"],
     WARN_YEL),
    ("Protctus Lab", "Full Education Pipeline",
     ["Lab Chapters → Topics → Microlearning",
      "Interactive Quizzes with scoring",
      "Phishing, social engineering, passwords",
      "Bite-sized content for mobile learning"],
     ACCENT),
    ("Global Leaderboard", "Competitive Security",
     ["Firebase Firestore Real-time Sync",
      "Global ranking by security XP",
      "Weekly and all-time leaderboards",
      "Social proof drives engagement"],
     SUCCESS),
]

for i, (title, sub, points, color) in enumerate(engage_top):
    x = Inches(0.9) + Inches(i * 4.1)
    card = rect(s, x, Inches(1.85), Inches(3.8), Inches(2.8), CARD_ALT, color, Pt(1))
    flat_rect(s, x, Inches(1.85), Inches(3.8), Pt(3), color)
    
    tf = tb(s, x + Inches(0.25), Inches(2.05), Inches(3.3), Inches(2.5))
    txt(tf, title, sz=17, b=True, c=color, fn="Segoe UI Semibold")
    add(tf, sub, sz=10, c=MUTED, sa=Pt(5))
    bullet_block(tf, points, sz=11, c=PRIMARY_TXT, sa=Pt(2))

# Bottom row - 3 smaller cards
engage_bot = [
    ("Rewards & Referral", "Milestone-based rewards + referral tracking program for organic growth", WARN_ORG),
    ("Premium Tier", "Advanced features gated behind subscription for sustainable monetization", SECONDARY),
    ("6 Indian Languages", "Hindi, Kannada, Bengali, Tamil, Telugu, Odia — dynamic restart-free locale switching", ACCENT),
]

for i, (title, desc, color) in enumerate(engage_bot):
    x = Inches(0.9) + Inches(i * 4.1)
    card = rect(s, x, Inches(4.9), Inches(3.8), Inches(0.85), CARD_ALT, color, Pt(0.5))
    flat_rect(s, x, Inches(4.9), Pt(4), Inches(0.85), color)
    
    tf = tb(s, x + Inches(0.25), Inches(4.98), Inches(3.5), Inches(0.7))
    txt(tf, title, sz=13, b=True, c=color, sa=Pt(1))
    add(tf, desc, sz=10, c=MUTED, sa=Pt(0))

# App navigation map
rect(s, Inches(0.9), Inches(6.0), Inches(11.5), Inches(1.2), FINDING_BG, SECONDARY, Pt(0.5))
flat_rect(s, Inches(0.9), Inches(6.0), Inches(11.5), Pt(3), ACCENT)

tf_nav = tb(s, Inches(1.2), Inches(6.15), Inches(11.0), Inches(1.0))
txt(tf_nav, "40+ Screens — Complete App Navigation", sz=14, b=True, c=WHITE)
add(tf_nav, "Auth Flow (Splash→Walkthrough→Onboarding→SignIn/Up→OTP→Biometric)  ·  Dashboard (Home→Features→Search)  ·  Scanning (Smart→Deep→URL→DarkWeb)", sz=10, c=MUTED, sa=Pt(1))
add(tf_nav, "Intelligence (Threat Feed→Resolution→Notifications)  ·  Education (Lab→Topics→Quiz)  ·  Engagement (Quests→Rewards→Leaderboard)  ·  Settings (Account→Preferences→Help)", sz=10, c=MUTED, sa=Pt(0))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 8: SELF-SECURITY + AUTH
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "App Self-Security & Authentication", "Protctus protects itself — 7-layer Atmanirbhar Chakravyuh + 6-layer OTP", 8)

# ── LEFT: Atmanirbhar Chakravyuh ──
rect(s, Inches(0.9), Inches(1.85), Inches(6.2), Inches(5.3), CARD_ALT, WARN_RED, Pt(1))
flat_rect(s, Inches(0.9), Inches(1.85), Inches(6.2), Pt(4), WARN_RED)

tf = tb(s, Inches(1.2), Inches(2.1), Inches(5.7), Inches(5.0))
txt(tf, "App Self-Security", sz=20, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf, "Runtime Self-Protection — Validated on Every App Launch", sz=11, c=WARN_RED, sa=Pt(8))
add(tf, "Ensures Protctus itself cannot be tampered with, decompiled, or redistributed as a modded APK.", sz=11, c=MUTED, sa=Pt(8))

layers = [
    ("Layer 1", "Signature Integrity", "Validates APK signing certificate matches release build", WARN_RED),
    ("Layer 2", "Root / Su Detection", "Checks for su binaries, root management apps, and build tags", WARN_ORG),
    ("Layer 3", "Emulator Detection", "15 build properties analyzed to detect virtual environments", WARN_YEL),
    ("Layer 4", "Anti-Debugging", "Detects attached debuggers and debug flags at runtime", ACCENT),
    ("Layer 5", "Anti-Hooking", "Scans /proc/self/maps for Frida, Xposed, and injection frameworks", CYAN_GLOW),
    ("Layer 6", "Installer Verification", "Validates the app was installed from a trusted source", SUCCESS),
    ("Layer 7", "DEX Checksum", "Verifies bytecode integrity to detect code modification", WHITE),
]

for layer_id, name, desc, color in layers:
    add(tf, f"  {layer_id}: {name}", sz=11, b=True, c=color, sa=Pt(0))
    add(tf, f"       {desc}", sz=10, c=MUTED, sa=Pt(3))

add(tf, "If any layer fails → non-dismissible overlay locks the entire UI.", sz=11, b=True, c=WARN_RED, sa=Pt(0))

# ── RIGHT: Authentication ──
rect(s, Inches(7.4), Inches(1.85), Inches(5.0), Inches(5.3), CARD_ALT, ACCENT, Pt(1))
flat_rect(s, Inches(7.4), Inches(1.85), Inches(5.0), Pt(4), ACCENT)

tf2 = tb(s, Inches(7.7), Inches(2.1), Inches(4.5), Inches(5.0))
txt(tf2, "Authentication & Account", sz=20, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf2, "Firebase Auth + 6-Layer OTP Protection", sz=11, c=ACCENT, sa=Pt(8))

add(tf2, "Firebase Authentication:", sz=12, b=True, c=WHITE, sa=Pt(3))
bullet_block(tf2, [
    "Email/password with Firestore profiles",
    "Biometric unlock support (fingerprint/face)",
    "TOTP crypto manager for secure tokens",
], sz=11, c=PRIMARY_TXT, sa=Pt(2))

add(tf2, "Secure OTP Protection:", sz=12, b=True, c=WARN_YEL, sa=Pt(5))
bullet_block(tf2, [
    "Progressive cooldown: 1m → 2m → 5m → 10m",
    "Hourly limit: 5 OTPs max",
    "Daily limit: 10 OTPs max",
    "Wrong-OTP lockout: 5 wrong → 30min block",
    "5-minute expiry per OTP",
    "Device-level Firebase rate limiting",
], sz=11, c=PRIMARY_TXT, sa=Pt(2))


# ═════════════════════════════════════════════════════════════════════
# SLIDE 9: TECH STACK + PERFORMANCE
# ═════════════════════════════════════════════════════════════════════
s = mk()
header(s, "Architecture & Performance", "Built for speed, privacy, and scale — Protctus vs the industry", 9)

# ── LEFT: Tech Stack ──
rect(s, Inches(0.9), Inches(1.85), Inches(5.8), Inches(5.3), CARD_ALT, ACCENT, Pt(1))
flat_rect(s, Inches(0.9), Inches(1.85), Pt(4), Inches(5.3), ACCENT)

tf = tb(s, Inches(1.25), Inches(2.0), Inches(5.3), Inches(5.0))
txt(tf, "Technology Stack", sz=20, b=True, c=WHITE, fn="Segoe UI Semibold")
add(tf, "", sz=6, sa=Pt(0))

stack = [
    ("Language",       "Kotlin 1.9+", ACCENT),
    ("UI Framework",   "Jetpack Compose — Material 3 · Unidirectional Data Flow", ACCENT),
    ("Architecture",   "MVVM + Clean Architecture + Hilt Dependency Injection", ACCENT),
    ("Local Storage",  "Room Database + DataStore Preferences", ACCENT),
    ("Cloud",          "Firebase Auth · Firestore · FCM Push Notifications", ACCENT),
    ("Edge Computing", "Firebase Functions", CYAN_GLOW),
    ("AI / ML",        "TFLite on-device URL analysis", CYAN_GLOW),
    ("Networking",     "Retrofit + OkHttp with API key authentication", ACCENT),
    ("Background",     "WorkManager (periodic) · Foreground Services (Sentinel, Deep Scan)", ACCENT),
    ("Localization",   "Dynamic restart-free switching — Hindi, Kannada, Bengali, Tamil, Telugu, Odia", WARN_YEL),
    ("Target",         "Android 8.0 (API 26) → Android 14+ (API 34)", SUCCESS),
]

for label, val, color in stack:
    add(tf, f"  {label}", sz=10, b=True, c=color, sa=Pt(0))
    add(tf, f"  {val}", sz=11, c=PRIMARY_TXT, sa=Pt(5))

# ── RIGHT: Performance Comparison ──
rect(s, Inches(7.0), Inches(1.85), Inches(5.4), Inches(5.3), CARD_ALT, SUCCESS, Pt(1))
flat_rect(s, Inches(7.0), Inches(1.85), Pt(4), Inches(5.3), SUCCESS)

tf2 = tb(s, Inches(7.35), Inches(2.0), Inches(5.0), Inches(0.5))
txt(tf2, "Protctus vs Traditional AV", sz=20, b=True, c=WHITE, fn="Segoe UI Semibold")

# Table header
header_y = Inches(2.7)
rect(s, Inches(7.25), header_y, Inches(1.8), Inches(0.35), SECONDARY)
rect(s, Inches(9.15), header_y, Inches(1.5), Inches(0.35), SUCCESS)
rect(s, Inches(10.75), header_y, Inches(1.5), Inches(0.35), WARN_RED)

tf_h1 = tb(s, Inches(7.25), header_y + Inches(0.02), Inches(1.8), Inches(0.3))
txt(tf_h1, "Metric", sz=10, b=True, c=WHITE, a=PP_ALIGN.CENTER)
tf_h2 = tb(s, Inches(9.15), header_y + Inches(0.02), Inches(1.5), Inches(0.3))
txt(tf_h2, "Protctus", sz=10, b=True, c=BG_DARK, a=PP_ALIGN.CENTER)
tf_h3 = tb(s, Inches(10.75), header_y + Inches(0.02), Inches(1.5), Inches(0.3))
txt(tf_h3, "Traditional", sz=10, b=True, c=WHITE, a=PP_ALIGN.CENTER)

metrics = [
    ("CPU (Idle)",       "<1.2%",          "4-8%"),
    ("VPN Latency",      "~4ms loopback",  "N/A (Accessibility)"),
    ("Signature DB",     "64KB Bloom",     "100MB+ cloud"),
    ("Lookup Speed",     "O(1) Murmur",    "O(n) cloud RTT"),
    ("RAM Usage",        "~65MB avg",      "150MB+"),
    ("Offline",          "Full capability", "Minimal"),
    ("Privacy",          "Zero telemetry",  "Cloud uploads"),
]

for i, (metric, ours, theirs) in enumerate(metrics):
    row_y = Inches(3.15) + Inches(i * 0.5)
    row_bg = FINDING_BG if i % 2 == 0 else CARD_ALT
    
    rect(s, Inches(7.25), row_y, Inches(1.8), Inches(0.42), row_bg)
    rect(s, Inches(9.15), row_y, Inches(1.5), Inches(0.42), row_bg)
    rect(s, Inches(10.75), row_y, Inches(1.5), Inches(0.42), row_bg)
    
    tf_m = tb(s, Inches(7.3), row_y + Inches(0.05), Inches(1.7), Inches(0.3))
    txt(tf_m, metric, sz=10, b=True, c=ACCENT, a=PP_ALIGN.CENTER)
    
    tf_o = tb(s, Inches(9.15), row_y + Inches(0.05), Inches(1.5), Inches(0.3))
    txt(tf_o, ours, sz=10, b=True, c=SUCCESS, a=PP_ALIGN.CENTER)
    
    tf_t = tb(s, Inches(10.75), row_y + Inches(0.05), Inches(1.5), Inches(0.3))
    txt(tf_t, theirs, sz=10, c=WARN_RED, a=PP_ALIGN.CENTER)


# ═════════════════════════════════════════════════════════════════════
# SLIDE 10: THANK YOU — Powerful Summary
# ═════════════════════════════════════════════════════════════════════
s = mk()
flat_rect(s, Inches(0), Inches(0), W, Pt(4), ACCENT)

# Brand
tf = tb(s, Inches(0.5), Inches(0.4), Inches(12.3), Inches(1.2))
txt(tf, "PROTCTUS", sz=60, b=True, c=WHITE, a=PP_ALIGN.CENTER, fn="Segoe UI Black")
add(tf, "Zero-Trust Mobile Security  ·  Offline-First  ·  Privacy-First  ·  AI-Powered", sz=16, c=ACCENT, a=PP_ALIGN.CENTER)

accent_bar(s, Inches(5.5), Inches(2.0), Inches(2.3), ACCENT)

# Two-column summary
summary_left = [
    ("Anti-Juice Jacking", "Real-time USB threat detection — unique in market", WARN_RED),
    ("Stalkerware + Ghost", "Behavioral 4-rule engine hunts invisible spying apps", WARN_ORG),
    ("Proc Malware Engine", "Zero-day malware caught via behavioral analysis", WHITE),
    ("MurmurHash3 Bloom Filter", "O(1) lookups · 64KB · 36K sigs · 100% offline", SUCCESS),
]

summary_right = [
    ("VPN DNS Sinkhole", "3-tier phishing block at network layer · ~4ms", SUCCESS),
    ("Typosquatting Engine", "On-device Homoglyph & Levenshtein analysis", CYAN_GLOW),
    ("Sentinel + Deep Scan", "Always-on + absolute filesystem traversal", ACCENT),
    ("Gamified Security", "Quests · Labs · Leaderboard · 6 Indian languages", WARN_YEL),
]

# Left column
rect(s, Inches(0.9), Inches(2.3), Inches(5.7), Inches(3.0), FINDING_BG, SECONDARY, Pt(0.5))
flat_rect(s, Inches(0.9), Inches(2.3), Pt(4), Inches(3.0), ACCENT)
for i, (name, desc, color) in enumerate(summary_left):
    y = Inches(2.45) + Inches(i * 0.7)
    tf_s = tb(s, Inches(1.3), y, Inches(5.1), Inches(0.65))
    txt(tf_s, f"►  {name}", sz=13, b=True, c=color, sa=Pt(0))
    add(tf_s, f"    {desc}", sz=11, c=MUTED, sa=Pt(0))

# Right column
rect(s, Inches(6.8), Inches(2.3), Inches(5.7), Inches(3.0), FINDING_BG, SECONDARY, Pt(0.5))
flat_rect(s, Inches(6.8), Inches(2.3), Pt(4), Inches(3.0), ACCENT)
for i, (name, desc, color) in enumerate(summary_right):
    y = Inches(2.45) + Inches(i * 0.7)
    tf_s = tb(s, Inches(7.2), y, Inches(5.1), Inches(0.65))
    txt(tf_s, f"►  {name}", sz=13, b=True, c=color, sa=Pt(0))
    add(tf_s, f"    {desc}", sz=11, c=MUTED, sa=Pt(0))

# Self-security badge
rect(s, Inches(0.9), Inches(5.5), Inches(5.7), Inches(0.65), CARD_ALT, WARN_RED, Pt(0.5))
tf_ac = tb(s, Inches(1.1), Inches(5.55), Inches(5.3), Inches(0.55))
txt(tf_ac, "App Self-Protection", sz=12, b=True, c=WARN_RED, sa=Pt(0))
add(tf_ac, "Signature + Root + Emulator + Debug", sz=10, c=MUTED, sa=Pt(0))

# Auth badge
rect(s, Inches(6.8), Inches(5.5), Inches(5.7), Inches(0.65), CARD_ALT, ACCENT, Pt(0.5))
tf_au = tb(s, Inches(7.0), Inches(5.55), Inches(5.3), Inches(0.55))
txt(tf_au, "Secure OTP Protection + Biometric Auth", sz=12, b=True, c=ACCENT, sa=Pt(0))
add(tf_au, "Progressive cooldown · Rate limiting · Device-level Firebase", sz=10, c=MUTED, sa=Pt(0))

# Performance bar
rect(s, Inches(0.9), Inches(6.35), Inches(11.6), Inches(0.55), CARD_GRAD, SUCCESS, Pt(1))
tf_perf = tb(s, Inches(0.9), Inches(6.38), Inches(11.6), Inches(0.5))
txt(tf_perf, "CPU <1.2%   ·   RAM ~65MB   ·   DB 64KB   ·   Latency ~4ms   ·   O(1) Lookup   ·   Zero Telemetry   ·   Full Offline", sz=13, b=True, c=SUCCESS, a=PP_ALIGN.CENTER)

# Bottom accent
flat_rect(s, Inches(0), H - Pt(4), W, Pt(4), ACCENT)
sn(s, 10)


# ─── Save ─────────────────────────────────────────
out = r"a:\Protctus\Protctus-deck.pptx"
prs.save(out)
print(f"Saved: {out} - {len(prs.slides)} slides")
