# -*- coding: utf-8 -*-
"""
Builds two professional .docx reports for the Protctus major project using only
the Python standard library (zipfile + xml). Output is valid WordprocessingML
that opens in Microsoft Word and is fully editable.

Evidence basis: all figures, metrics, file paths, commands and statuses are
drawn from direct inspection of the Protctus repository (source code, assets,
ML result files, evaluation docs, bug register). Nothing is fabricated.
"""
import os
import zipfile
import datetime
import io
from xml.sax.saxutils import escape as _escape
from xml.etree import ElementTree as ET

W = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
R = 'http://schemas.openxmlformats.org/officeDocument/2006/relationships'
C = 'http://schemas.openxmlformats.org/package/2006/content-types'
A = 'http://schemas.openxmlformats.org/drawingml/2006/main'
WP = 'http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'
PIC = 'http://schemas.openxmlformats.org/drawingml/2006/picture'

NSMAP = {'w': W, 'r': R, 'c': C, 'a': A, 'wp': WP, 'pic': PIC}


def q(tag, ns=W):
    return '{%s}%s' % (ns, tag)


def esc(s):
    if s is None:
        return ''
    return _escape(str(s), {'"': '&quot;', "'": '&apos;'})


# ---------------------------------------------------------------------------
# Low-level element builders returning XML strings
# ---------------------------------------------------------------------------
def run(text, bold=False, italic=False, size=11, font='Calibri', color=None,
        underline=False, name=None, highlight=None):
    r = '<w:r>'
    r += '<w:rPr>'
    if bold:
        r += '<w:b/>'
    if italic:
        r += '<w:i/>'
    if underline:
        r += '<w:u w:val="single"/>'
    r += '<w:sz w:val="%d"/>' % (size * 2)
    r += '<w:szCs w:val="%d"/>' % (size * 2)
    r += '<w:rFonts w:ascii="%s" w:hAnsi="%s" w:cs="%s"/>' % (font, font, font)
    if color:
        r += '<w:color w:val="%s"/>' % color
    if highlight:
        r += '<w:highlight w:val="%s"/>' % highlight
    r += '</w:rPr>'
    r += '<w:t xml:space="preserve">%s</w:t>' % esc(text)
    r += '</w:r>'
    return r


def rich_runs(parts, size=11, font='Calibri'):
    """parts: list of (text, dict-of-run-props). Each part gets its own <w:r>."""
    out = ''
    for text, props in parts:
        b = props.get('bold', False)
        i = props.get('italic', False)
        u = props.get('underline', False)
        sz = props.get('size', size)
        fn = props.get('font', font)
        col = props.get('color')
        hl = props.get('highlight')
        out += '<w:r>'
        out += '<w:rPr>'
        if b:
            out += '<w:b/>'
        if i:
            out += '<w:i/>'
        if u:
            out += '<w:u w:val="single"/>'
        out += '<w:sz w:val="%d"/>' % (sz * 2)
        out += '<w:szCs w:val="%d"/>' % (sz * 2)
        out += '<w:rFonts w:ascii="%s" w:hAnsi="%s" w:cs="%s"/>' % (fn, fn, fn)
        if col:
            out += '<w:color w:val="%s"/>' % col
        if hl:
            out += '<w:highlight w:val="%s"/>' % hl
        out += '</w:rPr>'
        out += '<w:t xml:space="preserve">%s</w:t>' % esc(text)
        out += '</w:r>'
    return out


def paragraph(runs_xml, style=None, align=None, indent=None, space_before=None,
              space_after=None, line=None, keep_next=False, p_style=None):
    p = '<w:p>'
    pPr = '<w:pPr>'
    if style:
        pPr += '<w:pStyle w:val="%s"/>' % esc(style)
    if align:
        pPr += '<w:jc w:val="%s"/>' % esc(align)
    if indent is not None:
        pPr += '<w:ind w:left="%d"/>' % indent
    if space_before is not None:
        pPr += '<w:spacing w:before="%d"/>' % space_before
    if space_after is not None:
        pPr += '<w:spacing w:after="%d"/>' % space_after
    if line is not None:
        if 'w:spacing' not in pPr:
            pPr += '<w:spacing/>'
        # insert line into spacing
        pPr = pPr.replace('<w:spacing/>', '<w:spacing w:line="%d" w:lineRule="auto"/>' % line)
    if keep_next:
        pPr += '<w:keepNext/>'
    pPr += '</w:pPr>'
    p = p.replace('<w:p>', '<w:p>' + pPr, 1)
    p += runs_xml
    p += '</w:p>'
    return p


def heading(text, level=1):
    # Word built-in heading styles: Heading 1..9
    style = 'Heading%d' % level
    # map level to a manual run if style missing
    sizes = {1: 16, 2: 13.5, 3: 12, 4: 11.5}
    size = sizes.get(level, 11)
    colors = {1: '1F3864', 2: '2E5395', 3: '404040', 4: '595959'}
    color = colors.get(level, '404040')
    runs = run(text, bold=True, size=size, color=color, font='Calibri')
    # use built-in heading style so TOC/navigation works
    return paragraph(runs, style=style, space_before=14 if level == 1 else 10,
                     space_after=6 if level == 1 else 4, keep_next=True)


def subheading(text):
    return heading(text, level=2)


def body_para(text, size=11, align='both', space_after=6, bold=False, italic=False,
              color=None, indent=None, space_before=0):
    return paragraph(run(text, bold=bold, italic=italic, size=size, color=color),
                     align=align, space_after=space_after, space_before=space_before,
                     indent=indent)


def rich_para(parts, align='both', space_after=6, indent=None, size=11):
    return paragraph(rich_runs(parts, size=size), align=align, space_after=space_after, indent=indent)


def bullet(text, level=0, bold_lead=None):
    style = 'ListBullet' if level == 0 else 'ListBullet2'
    if bold_lead:
        runs = rich_runs([(bold_lead, {'bold': True}), (text, {})], size=11)
    else:
        runs = run(text, size=11)
    return paragraph(runs, style=style, space_after=3, indent=level * 360)


def numbered(text, bold_lead=None):
    if bold_lead:
        runs = rich_runs([(bold_lead, {'bold': True}), (text, {})], size=11)
    else:
        runs = run(text, size=11)
    return paragraph(runs, style='ListNumber', space_after=3)


def page_break():
    return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>'


def spacer(pts=6):
    return '<w:p><w:pPr><w:spacing w:after="%d"/></w:pPr><w:r><w:br/></w:r></w:p>' % pts


def _cell(content, size=10, shade=None, align='center', valign='center', mono=False):
    """content: str or list of str paragraphs."""
    if isinstance(content, str):
        content = [content]
    inner = ''
    for i, line in enumerate(content):
        if i > 0:
            inner += '<w:p><w:pPr><w:spacing w:before="10" w:after="10"/></w:pPr>'
        else:
            inner += '<w:p><w:pPr><w:spacing w:before="30" w:after="30"/></w:pPr>'
        font = 'Consolas' if mono else 'Calibri'
        inner += '<w:r><w:rPr><w:sz w:val="%d"/><w:rFonts w:ascii="%s" w:hAnsi="%s"/></w:rPr>' % (size * 2, font, font)
        inner += '<w:t xml:space="preserve">%s</w:t></w:r></w:p>' % esc(line)
    tcPr = '<w:tcPr>'
    if shade:
        tcPr += '<w:shd w:val="clear" w:color="auto" w:fill="%s"/>' % esc(shade)
    tcPr += '<w:vAlign w:val="%s"/>' % esc(valign)
    tcPr += '</w:tcPr>'
    return '<w:tc>' + tcPr + inner + '</w:tc>'


def table(header, rows, widths=None, header_shade='1F3864', header_color='FFFFFF',
          body_size=9, header_size=9, align='center', repeat_header=True,
          col_aligns=None):
    """Build a w:tbl. header: list of str. rows: list of list of str (or list of list of runs-xml)."""
    tbl = '<w:tbl><w:tblPr>'
    tbl += '<w:tblW w:w="%d" w:type="dxa"/>' % (9000 if widths is None else sum(widths or []))
    tbl += '<w:tblBorders>'
    for edge in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        tbl += '<w:%s w:val="single" w:sz="4" w:space="0" w:color="9CA3AF"/>' % edge
    tbl += '</w:tblBorders>'
    tbl += '<w:tblLook w:val="04A0" w:firstRow="1" w:lastRow="0" w:firstColumn="0" w:lastColumn="0" w:noHorizontalBand="0" w:noVerticalBand="0"/>'
    tbl += '</w:tblPr><w:tblGrid>'
    if widths is None:
        widths = [9000 // max(1, len(header))] * len(header)
    for wdt in widths:
        tbl += '<w:gridCol w:w="%d"/>' % wdt
    tbl += '</w:tblGrid>'

    # header row
    tr = '<w:tr>'
    trPr = '<w:trPr>'
    if repeat_header:
        trPr += '<w:tblHeader/>'
    trPr += '</w:trPr>'
    tr += trPr
    for i, h in enumerate(header):
        wdt = widths[i] if widths and i < len(widths) else None
        tr += '<w:tc><w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="%s"/><w:vAlign w:val="center"/></w:tcPr>' % esc(header_shade)
        tr += '<w:p><w:pPr><w:spacing w:before="40" w:after="40"/><w:jc w:val="%s"/></w:pPr>' % esc(align)
        tr += '<w:r><w:rPr><w:b/><w:sz w:val="%d"/><w:color w:val="%s"/><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr><w:t xml:space="preserve">%s</w:t></w:r>' % (header_size * 2, esc(header_color), esc(h))
        tr += '</w:p></w:tc>'
    tr += '</w:tr>'
    tbl += tr

    # body rows
    band = False
    for row in rows:
        band = not band
        tr = '<w:tr><w:trPr></w:trPr>'
        for i, cell in enumerate(row):
            shade = 'F2F5FB' if band else None
            cell_align = (col_aligns[i] if col_aligns and i < len(col_aligns) else align)
            tr += _cell(cell, size=body_size, shade=shade, align=cell_align)
        tr += '</w:tr>'
        tbl += tr
    tbl += '</w:tbl>'
    return tbl


def caption(text, label='Table'):
    return paragraph(run('%s ' % label + text, bold=True, italic=True, size=9, color='595959'),
                     align='center', space_before=4, space_after=10)


def figure_caption(text):
    return paragraph(run(text, bold=True, italic=True, size=9, color='595959'),
                     align='center', space_before=4, space_after=10)


def code_block(lines, size=8.5):
    """lines: list of strings. Renders as a single shaded cell for monospace look."""
    inner = ''
    for ln in lines:
        inner += '<w:p><w:pPr><w:spacing w:before="0" w:after="0"/><w:rPr><w:sz w:val="%d"/></w:rPr></w:pPr>' % (size * 2)
        inner += '<w:r><w:rPr><w:sz w:val="%d"/><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/></w:rPr><w:t xml:space="preserve">%s</w:t></w:r>' % (size * 2, esc(ln))
        inner += '</w:p>'
    tcPr = '<w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="F5F5F5"/><w:vAlign w:val="top"/></w:tcPr>'
    tbl = '<w:tbl><w:tblPr><w:tblW w:w="9240" w:type="dxa"/><w:tblBorders>'
    tbl += '<w:top w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/><w:left w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/><w:right w:val="single" w:sz="4" w:space="0" w:color="BFBFBF"/><w:insideH w:val="none" w:sz="0" w:space="0" w:color="auto"/><w:insideV w:val="none" w:sz="0" w:space="0" w:color="auto"/></w:tblBorders></w:tblPr><w:tblGrid><w:gridCol w:w="9240"/></w:tblGrid>'
    tbl += '<w:tr><w:tc>' + tcPr + inner + '</w:tc></w:tr></w:tbl>'
    return tbl


def image(rel_id, cx_inches, cy_inches, name='image'):
    """Inline image drawing. cx_inches/cy_inches in inches -> EMU (inches*914400)."""
    cx = int(cx_inches * 914400)
    cy = int(cy_inches * 914400)
    return ('<w:p><w:pPr><w:jc w:val="center"/></w:pPr>'
            '<w:r><w:rPr/><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">'
            '<wp:extent cx="%d" cy="%d"/><wp:effectExtent l="0" t="0" r="0" b="0"/>'
            '<wp:docPr id="1" name="%s"/><wp:cNvGraphicFramePr/>'
            '<a:graphic xmlns:a="%s"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">'
            '<pic:pic xmlns:pic="%s"><pic:nvPicPr><pic:cNvPr id="0" name="%s"/><pic:cNvPicPr/></pic:nvPicPr>'
            '<pic:blipFill><a:blip r:embed="%s"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>'
            '<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="%d" cy="%d"/></a:xfrm>'
            '<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>'
            '</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>'
            ) % (cx, cy, esc(name), A, PIC, esc(name), rid, cx, cy)


# ---------------------------------------------------------------------------
# Document assembler
# ---------------------------------------------------------------------------
class Docx:
    def __init__(self, title):
        self.title = title
        self.body_parts = []
        self.media = {}  # rel_id -> (filename, bytes, content_type)
        self.next_media_id = 1
        self.section_id = 1

    def add(self, xml):
        self.body_parts.append(xml)

    def add_media(self, filename, data, content_type):
        rid = 'rIdM%d' % self.next_media_id
        self.next_media_id += 1
        self.media[rid] = (filename, data, content_type)
        return rid

    def add_image(self, filename, data, content_type, cx_inches, cy_inches):
        rid = self.add_media(filename, data, content_type)
        self.add(image(None, rid, cx_inches, cy_inches))
        return rid

    def _body_xml(self):
        return ''.join(self.body_parts)

    def _content_types(self):
        parts = []
        parts.append('<Types xmlns="%s">' % C)
        parts.append('<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>')
        parts.append('<Default Extension="xml" ContentType="application/xml"/>')
        parts.append('<Default Extension="png" ContentType="image/png"/>')
        parts.append('<Default Extension="svg" ContentType="image/svg+xml"/>')
        for rid, (fn, data, ct) in self.media.items():
            ext = fn.rsplit('.', 1)[-1] if '.' in fn else 'bin'
            parts.append('<Override PartName="/word/media/%s" ContentType="%s"/>' % (esc(fn), ct))
        parts.append('<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>')
        parts.append('<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>')
        parts.append('<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>')
        parts.append('<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>')
        parts.append('<Override PartName="/word/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/>')
        parts.append('<Override PartName="/word/_rels/document.xml.rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>')
        parts.append('<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>')
        parts.append('<Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>')
        parts.append('<Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>')
        parts.append('</Types>')
        return ''.join(parts)

    def _rels(self):
        rels = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
                '<Relationships xmlns="%s">' % R]
        rels.append('<Relationship Id="rId1" Type="%s/document" Target="word/document.xml"/>' % R)
        rels.append('<Relationship Id="rId2" Type="%s/styles" Target="word/styles.xml"/>' % R)
        rels.append('<Relationship Id="rId3" Type="%s/theme" Target="word/theme/theme1.xml"/>' % R)
        rels.append('<Relationship Id="rIdSettings" Type="%s/settings" Target="word/settings.xml"/>' % R)
        rels.append('<Relationship Id="rIdFontTable" Type="%s/fontTable" Target="word/fontTable.xml"/>' % R)
        rels.append('<Relationship Id="rIdCoreProps" Type="%s/core-properties" Target="docProps/core.xml"/>' % R)
        i = 4
        for rid in self.media:
            rels.append('<Relationship Id="%s" Type="%s/image" Target="media/%s"/>' % (rid, R, self.media[rid][0]))
            i += 1
        rels.append('</Relationships>')
        return ''.join(rels)

    def _doc_rels(self):
        rels = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>',
                '<Relationships xmlns="%s">' % R]
        rels.append('<Relationship Id="rId1" Type="%s/relationships/header" Target="header1.xml"/>' % R)
        rels.append('<Relationship Id="rId2" Type="%s/relationships/footer" Target="footer1.xml"/>' % R)
        j = 3
        for rid in self.media:
            rels.append('<Relationship Id="%s" Type="%s/relationships/image" Target="../media/%s" TargetMode="Internal"/>' % (rid, R, self.media[rid][0]))
        rels.append('</Relationships>')
        return ''.join(rels)

    def _styles(self):
        return (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<w:styles xmlns:w="%s">' % W +
            '<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="22"/><w:szCs w:val="22"/>'
            '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/></w:rPr></w:rPrDefault></w:docDefaults>'
            '<w:style w:type="paragraph" w:styleId="Normal"><w:name w:val="normal"/>'
            '<w:rPr><w:sz w:val="22"/><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:rPr><w:b/><w:sz w:val="32"/><w:color w:val="1F3864"/><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:rPr><w:b/><w:sz w:val="27"/><w:color w:val="2E5395"/><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="404040"/><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/></w:rPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="ListBullet"><w:name w:val="List Bullet"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:pPr><w:ind w:left="360" w:hanging="180"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="ListBullet2"><w:name w:val="List Bullet 2"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:pPr><w:ind w:left="720" w:hanging="180"/><w:numPr><w:ilvl w:val="1"/><w:numId w:val="1"/></w:numPr></w:pPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="ListNumber"><w:name w:val="List Number"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:pPr><w:ind w:left="360" w:hanging="180"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="2"/></w:numPr></w:pPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="Header"><w:name w:val="header"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:rPr><w:sz w:val="18"/><w:color w:val="595959"/></w:rPr></w:style>'
            '<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/>'
            '<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>'
            '<w:rPr><w:sz w:val="18"/><w:color w:val="595959"/></w:rPr></w:style>'
            '</w:styles>')

    def _theme(self):
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<a:theme xmlns:a="%s" name="Office Theme">' % A +
                '<a:fontScheme name="minor"><a:majorFont><a:latin typeface="Calibri"/></a:majorFont>'
                '<a:minorFont><a:latin typeface="Calibri"/></a:minorFont></a:fontScheme></a:theme>')

    def _settings(self):
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<w:settings xmlns:w="%s"><w:updateFields w:val="true"/><w:zoom w:percent="100"/></w:settings>' % W)

    def _font_table(self):
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<w:fontTable xmlns:w="%s"></w:fontTable>' % W)

    def _core(self):
        now = datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ')
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
                'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">'
                '<dc:title>%s</dc:title><dcterms:created>%s</dcterms:created>'
                '<dcterms:modified>%s</dcterms:modified><dc:creator>Protctus Project</dc:creator>'
                '</cp:coreProperties>' % (esc(self.title), now, now))

    def _header(self):
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<w:hdr xmlns:w="%s"><w:p><w:pPr><w:pStyle w:val="Header"/></w:pPr>'
                '<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="595959"/></w:rPr>'
                '<w:t xml:space="preserve">Protctus - Final Major Project Report</w:t></w:r></w:p></w:hdr>' % W)

    def _footer(self):
        # footer with project title left and page number right (using a tab + PAGE field)
        return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
                '<w:ftr xmlns:w="%s"><w:p><w:pPr><w:pStyle w:val="Footer"/><w:tabs><w:tab w:val="right" w:leader="spaces" w:pos="11340"/></w:tabs></w:pPr>'
                '<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="595959"/></w:rPr>'
                '<w:t xml:space="preserve">Protctus Final Major Project Report\t</w:t></w:r>'
                '<w:r><w:fldChar w:fldCharType="begin"/><w:instrText xml:space="preserve"> PAGE </w:instrText><w:fldChar w:fldCharType="end"/></w:r>'
                '<w:r><w:rPr><w:sz w:val="18"/><w:color w:val="595959"/></w:rPr>'
                '<w:t xml:space="preserve"> of </w:t></w:r>'
                '<w:r><w:fldChar w:fldCharType="begin"/><w:instrText xml:space="preserve"> NUMPAGES </w:instrText><w:fldChar w:fldCharType="end"/></w:r>'
                '</w:p></w:ftr>' % W)

    def _document_xml(self):
        sectPr = ('<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>'
                  '<w:footerReference w:type="default" r:id="rId2"/>'
                  '<w:headerReference w:type="default" r:id="rId1"/>'
                  '<w:type w:val="nextPage"/><w:pgNumType w:fmt="decimal" w:start="1"/></w:sectPr>')
        body = '<w:body>' + self._body_xml() + sectPr + '</w:body>'
        return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="%s" xmlns:r="%s">%s</w:document>' % (W, R, body)

    def save(self, path):
        # media files
        media_files = {}
        for rid, (fn, data, ct) in self.media.items():
            media_files['word/media/%s' % fn] = (data, ct)
        files = {
            '[Content_Types].xml': (self._content_types(), 'application/xml'),
            '_rels/.rels': (self._rels(), 'application/xml'),
            'word/_rels/document.xml.rels': (self._doc_rels(), 'application/xml'),
            'word/document.xml': (self._document_xml(), 'application/xml'),
            'word/styles.xml': (self._styles(), 'application/xml'),
            'word/theme/theme1.xml': (self._theme(), 'application/xml'),
            'word/settings.xml': (self._settings(), 'application/xml'),
            'word/fontTable.xml': (self._font_table(), 'application/xml'),
            'docProps/core.xml': (self._core(), 'application/xml'),
            'word/header1.xml': (self._header(), 'application/xml'),
            'word/footer1.xml': (self._footer(), 'application/xml'),
        }
        for fn, (data, ct) in media_files.items():
            files[fn] = (data, ct)
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
            for fn, (data, ct) in files.items():
                if isinstance(data, str):
                    z.writestr(fn, data.encode('utf-8'))
                else:
                    z.writestr(fn, data)


def esc_attr(s):
    return esc(s)
