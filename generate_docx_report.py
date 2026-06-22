"""
--------------------------------------------------------------------------
   PROTCTUS - Personal Project Report Generator
   Generates a 50+ page professional DOCX report
   Author: Generated from real project source code analysis
   Usage: python generate_docx_report.py
--------------------------------------------------------------------------
"""

import os
import sys
from docx import Document
from docx.shared import Inches, Pt, Cm
from docx.enum.section import WD_ORIENT

# Add report package to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from report.styles import setup_styles, add_page_break
from report.front_matter import (
    add_title_page, add_certificate, add_declaration,
    add_acknowledgement, add_abstract, add_table_of_contents,
    add_list_of_figures, add_list_of_tables
)
from report.chapters_1_2 import add_chapter1_introduction, add_chapter2_literature_review
from report.chapter3 import add_chapter3_system_design
from report.chapter4 import add_chapter4_implementation
from report.chapters_5_6 import (
    add_chapter5_testing, add_chapter6_conclusion,
    add_references, add_appendix
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
        # Header
        header = section.header
        header.is_linked_to_previous = False
        hp = header.paragraphs[0]
        hp.text = "Protctus - Personal Project Report"
        hp.style = doc.styles['Normal']
        run = hp.runs[0] if hp.runs else hp.add_run("")
        run.font.size = Pt(8)
        run.font.italic = True

        # Footer with page number
        footer = section.footer
        footer.is_linked_to_previous = False
        fp = footer.paragraphs[0]
        from docx.enum.text import WD_ALIGN_PARAGRAPH
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = fp.add_run("Page ")
        run.font.size = Pt(8)
        # Add auto page number field
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
        run4 = fp.add_run(" | Protctus © 2026")
        run4.font.size = Pt(8)


def generate_report():
    """Main report generation entry point."""
    print("-" * 60)
    print("  PROTCTUS - Personal Project Report Generator")
    print("-" * 60)

    doc = Document()

    # Phase 1: Configure layout
    print("[1/10] Configuring page layout...")
    configure_page_layout(doc)
    setup_styles(doc)

    # Phase 2: Front Matter
    print("[2/10] Generating front matter...")
    add_title_page(doc)
    add_certificate(doc)
    add_declaration(doc)
    add_acknowledgement(doc)
    add_abstract(doc)
    add_table_of_contents(doc)
    add_list_of_figures(doc)
    add_list_of_tables(doc)

    # Phase 3: Chapter 1 — Introduction
    print("[3/10] Writing Chapter 1: Introduction...")
    add_chapter1_introduction(doc)

    # Phase 4: Chapter 2 — Literature Review
    print("[4/10] Writing Chapter 2: Literature Review...")
    add_chapter2_literature_review(doc)

    # Phase 5: Chapter 3 — System Analysis & Design
    print("[5/10] Writing Chapter 3: System Analysis & Design...")
    add_chapter3_system_design(doc)

    # Phase 6: Chapter 4 — Implementation
    print("[6/10] Writing Chapter 4: Implementation...")
    add_chapter4_implementation(doc)

    # Phase 7: Chapter 5 — Testing & Results
    print("[7/10] Writing Chapter 5: Testing & Results...")
    add_chapter5_testing(doc)

    # Phase 8: Chapter 6 — Conclusion & Future Scope
    print("[8/10] Writing Chapter 6: Conclusion & Future Scope...")
    add_chapter6_conclusion(doc)

    # Phase 9: References
    print("[9/10] Writing References & Appendices...")
    add_references(doc)
    add_appendix(doc)

    # Phase 10: Add headers/footers and save
    print("[10/10] Adding headers/footers and saving...")
    add_headers_footers(doc)

    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "Protctus_Personal_Project_Report_Updated.docx")
    doc.save(output_path)

    print()
    print("-" * 60)
    print(f"  ✅ Report generated successfully!")
    print(f"  📄 Output: {output_path}")
    print(f"  📊 Estimated pages: 26+")
    print("-" * 60)
    return output_path


if __name__ == "__main__":
    generate_report()
