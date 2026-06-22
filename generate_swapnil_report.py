"""
--------------------------------------------------------------------------
   PROTCTUS - Swapnil's Contribution Report Generator
   Generates a professional DOCX report
   Usage: python generate_swapnil_report.py
--------------------------------------------------------------------------
"""

import os
import sys
from docx import Document
from docx.shared import Pt, Cm

# Add report package to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from report.styles import setup_styles
from report.swapnil_front_matter import (
    add_title_page, add_certificate, add_declaration,
    add_acknowledgement, add_abstract, add_table_of_contents,
    add_list_of_tables
)
from report.swapnil_chapters import (
    add_chapter1_introduction, add_chapter2_coding,
    add_chapter3_testing, add_chapter4_documentation,
    add_chapter5_marketing, add_chapter6_conclusion,
    add_references_appendix
)


def configure_page_layout(doc):
    """Configure A4 page with academic margins."""
    for section in doc.sections:
        section.page_width = Cm(21)    # A4 width
        section.page_height = Cm(29.7) # A4 height
        section.top_margin = Cm(2.54)  # 1 inch
        section.bottom_margin = Cm(2.54)
        section.left_margin = Cm(3.18) # 1.25 inch (binding margin)
        section.right_margin = Cm(2.54)


def add_headers_footers(doc):
    """Add page headers and footers."""
    for section in doc.sections:
        header = section.header
        header.is_linked_to_previous = False
        hp = header.paragraphs[0]
        hp.text = "Protctus - Documentation & QA Report"
        hp.style = doc.styles['Normal']
        run = hp.runs[0] if hp.runs else hp.add_run("")
        run.font.size = Pt(8)
        run.font.italic = True

        footer = section.footer
        footer.is_linked_to_previous = False
        fp = footer.paragraphs[0]
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = fp.add_run("Page ")
        run.font.size = Pt(8)
        from docx.oxml.ns import qn
        from lxml import etree
        fldChar1 = etree.SubElement(run._element, qn('w:fldChar'))
        fldChar1.set(qn('w:fldCharType'), 'begin')
        run2 = fp.add_run()
        instrText = etree.SubElement(run2._element, qn('w:instrText'))
        instrText.text = ' PAGE '
        instrText.set(qn('xml:space'), 'preserve')
        run3 = fp.add_run()
        fldChar2 = etree.SubElement(run3._element, qn('w:fldChar'))
        fldChar2.set(qn('w:fldCharType'), 'end')
        run4 = fp.add_run(" | Protctus 2026")
        run4.font.size = Pt(8)


def generate_report():
    print("-" * 60)
    print("  PROTCTUS - Swapnil's Contribution Report Generator")
    print("-" * 60)

    doc = Document()
    configure_page_layout(doc)
    setup_styles(doc)

    print("Generating front matter...")
    add_title_page(doc)
    add_certificate(doc)
    add_declaration(doc)
    add_acknowledgement(doc)
    add_abstract(doc)
    add_table_of_contents(doc)
    add_list_of_tables(doc)

    print("Writing chapters...")
    add_chapter1_introduction(doc)
    add_chapter2_coding(doc)
    add_chapter3_testing(doc)
    add_chapter4_documentation(doc)
    add_chapter5_marketing(doc)
    add_chapter6_conclusion(doc)
    add_references_appendix(doc)

    print("Adding headers/footers and saving...")
    add_headers_footers(doc)

    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "Protctus_Swapnil_Contribution_Report.docx")
    doc.save(output_path)

    print()
    print("-" * 60)
    print(f"  Report generated successfully!")
    print(f"  Output: {output_path}")
    print("-" * 60)
    return output_path


if __name__ == "__main__":
    generate_report()
