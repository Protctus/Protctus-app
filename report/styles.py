"""
Protctus Report — Shared Styles & Utilities
"""
from docx.shared import Inches, Pt, Cm, RGBColor, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn, nsdecls
from docx.oxml import parse_xml
import os

# ── Brand Colors ──
DARK_BG = RGBColor(0x1C, 0x19, 0x19)
PRIMARY_BLUE = RGBColor(0x1E, 0x40, 0xAF)
ACCENT_CYAN = RGBColor(0x06, 0xB6, 0xD4)
TEXT_PRIMARY = RGBColor(0x1F, 0x29, 0x37)
TEXT_SECONDARY = RGBColor(0x64, 0x74, 0x8B)
RED_CRITICAL = RGBColor(0xEF, 0x44, 0x44)
GREEN_SAFE = RGBColor(0x10, 0xB9, 0x81)
AMBER_WARN = RGBColor(0xF5, 0x9E, 0x0B)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
BLACK = RGBColor(0x00, 0x00, 0x00)
LIGHT_GRAY = RGBColor(0xF1, 0xF5, 0xF9)
TABLE_HEADER_BG = "1E3A5F"
TABLE_ALT_ROW = "F8FAFC"

FONT_BODY = 'Calibri'
FONT_HEADING = 'Calibri'
FONT_CODE = 'Consolas'


def setup_styles(doc):
    """Configure document-wide styles."""
    style = doc.styles['Normal']
    style.font.name = FONT_BODY
    style.font.size = Pt(11)
    style.font.color.rgb = TEXT_PRIMARY
    style.paragraph_format.space_after = Pt(6)
    style.paragraph_format.line_spacing = 1.15

    for level in range(1, 4):
        hstyle = doc.styles[f'Heading {level}']
        hstyle.font.name = FONT_HEADING
        hstyle.font.color.rgb = PRIMARY_BLUE
        hstyle.font.bold = True
        if level == 1:
            hstyle.font.size = Pt(22)
            hstyle.paragraph_format.space_before = Pt(24)
            hstyle.paragraph_format.space_after = Pt(12)
        elif level == 2:
            hstyle.font.size = Pt(16)
            hstyle.paragraph_format.space_before = Pt(18)
            hstyle.paragraph_format.space_after = Pt(8)
        else:
            hstyle.font.size = Pt(13)
            hstyle.paragraph_format.space_before = Pt(12)
            hstyle.paragraph_format.space_after = Pt(6)


def add_styled_paragraph(doc, text, bold=False, italic=False, size=11, color=None, align=None, space_after=6, font_name=None):
    p = doc.add_paragraph()
    run = p.add_run(text)
    run.bold = bold
    run.italic = italic
    run.font.size = Pt(size)
    run.font.name = font_name or FONT_BODY
    if color:
        run.font.color.rgb = color
    if align:
        p.alignment = align
    p.paragraph_format.space_after = Pt(space_after)
    return p


def add_code_block(doc, code, language="kotlin"):
    """Add a formatted code block with monospace font and background shading."""
    p = doc.add_paragraph()
    # Add light gray shading
    pPr = p._element.get_or_add_pPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="F1F5F9" w:val="clear"/>')
    pPr.append(shd)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(4)

    run = p.add_run(code)
    run.font.name = FONT_CODE
    run.font.size = Pt(8.5)
    run.font.color.rgb = TEXT_PRIMARY
    return p


def add_bullet(doc, text, level=0, bold_prefix=None):
    p = doc.add_paragraph(style='List Bullet')
    if bold_prefix:
        r = p.add_run(bold_prefix)
        r.bold = True
        r.font.size = Pt(11)
        r.font.name = FONT_BODY
        r2 = p.add_run(text)
        r2.font.size = Pt(11)
        r2.font.name = FONT_BODY
    else:
        run = p.add_run(text)
        run.font.size = Pt(11)
        run.font.name = FONT_BODY
    if level > 0:
        p.paragraph_format.left_indent = Inches(0.5 * level)
    return p


def add_bullet_list(doc, items, level=0):
    for item in items:
        add_bullet(doc, item, level)


def set_cell_shading(cell, color_hex):
    shading_elm = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}" w:val="clear"/>')
    cell._element.get_or_add_tcPr().append(shading_elm)


def add_styled_table(doc, headers, rows, col_widths=None):
    """Create a professional table with header styling and alternating rows."""
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = 'Table Grid'
    table.alignment = WD_TABLE_ALIGNMENT.CENTER

    # Header row
    for i, header in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        run = p.add_run(header)
        run.bold = True
        run.font.size = Pt(9)
        run.font.color.rgb = WHITE
        run.font.name = FONT_BODY
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        set_cell_shading(cell, TABLE_HEADER_BG)

    # Data rows
    for row_idx, row_data in enumerate(rows):
        row = table.add_row()
        for col_idx, cell_text in enumerate(row_data):
            cell = row.cells[col_idx]
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(str(cell_text))
            run.font.size = Pt(9)
            run.font.name = FONT_BODY
            if row_idx % 2 == 1:
                set_cell_shading(cell, TABLE_ALT_ROW)

    if col_widths:
        for i, width in enumerate(col_widths):
            for row in table.rows:
                row.cells[i].width = Inches(width)
    return table


def add_page_break(doc):
    doc.add_page_break()


def add_figure_caption(doc, caption, number):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(f"Figure {number}: {caption}")
    run.bold = True
    run.italic = True
    run.font.size = Pt(9)
    run.font.color.rgb = TEXT_SECONDARY
    p.paragraph_format.space_after = Pt(12)


def add_table_caption(doc, caption, number):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(f"Table {number}: {caption}")
    run.bold = True
    run.italic = True
    run.font.size = Pt(9)
    run.font.color.rgb = TEXT_SECONDARY
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(12)
