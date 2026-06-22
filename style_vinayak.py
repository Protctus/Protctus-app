import os
import docx
from docx.shared import Pt, Inches, RGBColor
from docx.oxml.ns import nsdecls, qn
from docx.oxml import parse_xml
from docx.enum.text import WD_ALIGN_PARAGRAPH

INPUT_FILE = "Protctus_Vinayak_Contribution_Report.docx"
OUTPUT_FILE = "Protctus_Vinayak_Contribution_Report_Updated.docx"
LOGO_PATH   = r"C:\Users\jatin\Downloads\RGPVLOGO.png"

# ── Colors ─────────────────────────────────────────────
CRIMSON     = "A30000"
LIGHT_RED   = "FDEDEC"
MED_RED     = "F5B7B1"
CHARCOAL    = "333333"
SLATE       = "555555"
WHITE       = "FFFFFF"

# ── Helpers ────────────────────────────────────────────

def safe_alignment(p):
    try:
        a = p.alignment
        return a if a is not None else None
    except:
        return None

def color_runs(para, hex_color, bold=None):
    """Change ONLY color (and optionally bold) of runs. Nothing else."""
    r, g, b = int(hex_color[0:2],16), int(hex_color[2:4],16), int(hex_color[4:6],16)
    for run in para.runs:
        run.font.color.rgb = RGBColor(r, g, b)
        if bold is not None:
            run.font.bold = bold

def shade_cell(cell, hex_color):
    tc = cell._tc.get_or_add_tcPr()
    for old in tc.findall(qn('w:shd')):
        tc.remove(old)
    tc.append(parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>'))

def para_shading(para, hex_color):
    """Add background shading to a paragraph."""
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn('w:shd')):
        pPr.remove(old)
    pPr.append(parse_xml(f'<w:shd {nsdecls("w")} w:val="clear" w:color="auto" w:fill="{hex_color}"/>'))

def para_border_bottom(para, color=CRIMSON, sz=8):
    """Red underline divider below paragraph."""
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn('w:pBdr')):
        pPr.remove(old)
    pPr.append(parse_xml(f'''
      <w:pBdr {nsdecls("w")}>
        <w:bottom w:val="single" w:sz="{sz}" w:space="3" w:color="{color}"/>
      </w:pBdr>'''))

def para_border_left_accent(para, color=CRIMSON, sz=18):
    """Thick red left-side accent bar on paragraph."""
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn('w:pBdr')):
        pPr.remove(old)
    pPr.append(parse_xml(f'''
      <w:pBdr {nsdecls("w")}>
        <w:left w:val="single" w:sz="{sz}" w:space="6" w:color="{color}"/>
      </w:pBdr>'''))

def para_border_box(para, color=CRIMSON, sz=6):
    """Full box border around a paragraph."""
    pPr = para._p.get_or_add_pPr()
    for old in pPr.findall(qn('w:pBdr')):
        pPr.remove(old)
    pPr.append(parse_xml(f'''
      <w:pBdr {nsdecls("w")}>
        <w:top    w:val="single" w:sz="{sz}" w:space="4" w:color="{color}"/>
        <w:left   w:val="single" w:sz="{sz}" w:space="6" w:color="{color}"/>
        <w:bottom w:val="single" w:sz="{sz}" w:space="4" w:color="{color}"/>
        <w:right  w:val="single" w:sz="{sz}" w:space="6" w:color="{color}"/>
      </w:pBdr>'''))

def page_border(section, color=CRIMSON):
    sp = section._sectPr
    for old in sp.findall(qn('w:pgBorders')):
        sp.remove(old)
    sp.append(parse_xml(f'''
      <w:pgBorders {nsdecls("w")} w:offset-from="page">
        <w:top    w:val="single" w:sz="8" w:space="24" w:color="{color}"/>
        <w:left   w:val="single" w:sz="8" w:space="24" w:color="{color}"/>
        <w:bottom w:val="single" w:sz="8" w:space="24" w:color="{color}"/>
        <w:right  w:val="single" w:sz="8" w:space="24" w:color="{color}"/>
      </w:pgBorders>'''))

# ── MAIN ─────────────────────────────────────────────────
def style_document():
    if not os.path.exists(INPUT_FILE):
        print(f"Error: Input file not found: {INPUT_FILE}")
        return

    print(f"Loading: {os.path.basename(INPUT_FILE)}")
    doc = docx.Document(INPUT_FILE)

    # 0. Remove empty pages
    print("  -> Removing empty pages (stripping stray page breaks)...")
    removed = 0
    for p in doc.paragraphs:
        txt = p.text.strip()
        breaks = p._p.findall('.//' + qn('w:br'))
        page_breaks = [br for br in breaks if br.get(qn('w:type')) == 'page']
        if page_breaks and not txt:
            for br in page_breaks:
                br.getparent().remove(br)
                removed += 1
    for p in doc.paragraphs:
        txt = p.text.strip()
        if not txt:
            lrpb = p._p.findall('.//' + qn('w:lastRenderedPageBreak'))
            for el in lrpb:
                el.getparent().remove(el)
    print(f"    Removed {removed} stray page break(s)")

    # 1. Page Border on every page
    print("  -> Adding crimson page border frame...")
    for sec in doc.sections:
        page_border(sec, CRIMSON)

    # 2. Paragraph styling — EXACTLY like v6
    print("  -> Applying graphical design overlay...")

    FRONT_TITLES = {
        "CERTIFICATE", "DECLARATION", "ACKNOWLEDGEMENT", "ABSTRACT",
        "TABLE OF CONTENTS", "LIST OF TABLES", "LIST OF FIGURES",
        "REFERENCES", "NOTES", "APPENDIX"
    }
    NAMES = {"JATIN SHARMA", "VINAYAK CHOURASIYA", "SWAPNIL CHAURASIYA",
             "KUNAL DHAKAD", "SHREYA BHADORIYA", "MRS. ASHA GRACE KERKETTA, HOD CSE"}

    cover = True
    for idx, p in enumerate(doc.paragraphs):
        txt = p.text.strip()
        if not txt:
            continue

        if "CERTIFICATE" in txt.upper() and len(txt) < 30:
            cover = False

        # ── COVER PAGE ──
        if cover:
            if "PROTCTUS" in txt.upper() and len(txt) < 30:
                color_runs(p, CRIMSON, bold=True)
            elif "MAJOR PROJECT REPORT" in txt.upper() or "A MAJOR PROJECT" in txt.upper() or "A PERSONAL CONTRIBUTION" in txt.upper():
                color_runs(p, CRIMSON, bold=True)
            elif "Submitted By:" in txt or "Under the Guidance" in txt:
                color_runs(p, CRIMSON, bold=True)
            elif "Academic Year" in txt:
                color_runs(p, CRIMSON, bold=True)
                # Box border around academic year for visual accent
                para_border_box(p, CRIMSON, sz=4)
            elif txt.upper() in NAMES:
                color_runs(p, CHARCOAL, bold=True)
            elif txt.startswith("[Department") or "Government Poly" in txt:
                color_runs(p, SLATE)
            else:
                color_runs(p, CHARCOAL)
            continue

        # ── CONTENT PAGES ──

        # ── Front matter section titles → RED BACKGROUND BADGE ──
        if txt.upper() in FRONT_TITLES:
            para_shading(p, CRIMSON)
            color_runs(p, WHITE, bold=True)
            continue

        # ── CHAPTER X → Red background banner ──
        if txt.upper().startswith("CHAPTER") and len(txt) < 15:
            para_shading(p, CRIMSON)
            color_runs(p, WHITE, bold=True)
            try:
                nxt = doc.paragraphs[idx + 1]
                nxt_txt = nxt.text.strip()
                if nxt_txt and not nxt_txt[0].isdigit():
                    para_shading(nxt, LIGHT_RED)
                    color_runs(nxt, CRIMSON, bold=True)
                    para_border_bottom(nxt, CRIMSON, sz=6)
            except IndexError:
                pass
            continue

        # ── Sub-headings (1.1 Title) → Left accent bar + light background ──
        if len(txt) > 3 and txt[0].isdigit() and '.' in txt[:4]:
            parts = txt.split(' ', 1)[0].split('.')
            num_parts = sum(1 for x in parts if x.isdigit())
            if num_parts >= 3:
                # Heading 3 (1.1.1) → Red italic text + left accent bar
                color_runs(p, CRIMSON, bold=True)
                para_border_left_accent(p, CRIMSON, sz=12)
            elif num_parts >= 2:
                # Heading 2 (1.1) → Light red background + left accent bar + red text
                para_shading(p, LIGHT_RED)
                color_runs(p, CRIMSON, bold=True)
                para_border_left_accent(p, CRIMSON, sz=18)
            continue

        # ── "Keywords:" label ──
        if txt.lower().startswith("keywords"):
            color_runs(p, SLATE, bold=True)
            continue

        # ── Standard body text → charcoal ──
        color_runs(p, CHARCOAL)

    # 3. RGPV Logo on cover - STRICTLY like v6
    print("  -> Inserting RGPV logo on cover page...")
    if os.path.exists(LOGO_PATH):
        # We explicitly OVERWRITE the very first paragraph with the logo
        if len(doc.paragraphs) > 0:
            p0 = doc.paragraphs[0]
            p0.text = ""
            p0.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p0.add_run().add_picture(LOGO_PATH, width=Inches(1.6))
    else:
        print(f"    Warning: Logo not found at {LOGO_PATH}!")

    # 4. Table styling - STRICTLY like v6
    print("  -> Styling tables with red theme...")
    for table in doc.tables:
        pr = table._tbl.tblPr
        for old in pr.findall(qn('w:tblBorders')): pr.remove(old)
        pr.append(parse_xml(f'''<w:tblBorders {nsdecls("w")}>
            <w:top    w:val="single" w:sz="6" w:space="0" w:color="{CRIMSON}"/>
            <w:left   w:val="single" w:sz="6" w:space="0" w:color="{CRIMSON}"/>
            <w:bottom w:val="single" w:sz="6" w:space="0" w:color="{CRIMSON}"/>
            <w:right  w:val="single" w:sz="6" w:space="0" w:color="{CRIMSON}"/>
            <w:insideH w:val="single" w:sz="4" w:space="0" w:color="{CRIMSON}"/>
            <w:insideV w:val="single" w:sz="4" w:space="0" w:color="{CRIMSON}"/>
        </w:tblBorders>'''))

        for ri, row in enumerate(table.rows):
            for cell in row.cells:
                if ri == 0:
                    shade_cell(cell, CRIMSON)
                    for cp in cell.paragraphs:
                        for r in cp.runs:
                            r.font.color.rgb = RGBColor(255, 255, 255)
                            r.font.bold = True
                else:
                    bg = LIGHT_RED if ri % 2 == 1 else WHITE
                    shade_cell(cell, bg)
                    for cp in cell.paragraphs:
                        for r in cp.runs:
                            r.font.color.rgb = RGBColor(0x33, 0x33, 0x33)

    # 5. Save
    print(f"  -> Saving: {os.path.basename(OUTPUT_FILE)}")
    out_dir = os.path.dirname(OUTPUT_FILE)
    if out_dir and not os.path.exists(out_dir):
        os.makedirs(out_dir)
    doc.save(OUTPUT_FILE)
    print("  Done! Strict v6 Professional graphical design applied.")

if __name__ == "__main__":
    style_document()
