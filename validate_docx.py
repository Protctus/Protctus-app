import zipfile, xml.etree.ElementTree as ET, re

def validate_docx(path):
    print('=== Validating:', path, '===')
    z = zipfile.ZipFile(path)
    
    # 1. Check zip integrity
    bad = z.testzip()
    print('Zip integrity:', 'OK' if bad is None else 'BAD: ' + str(bad))
    
    # 2. Parse all XML parts
    xml_parts = [n for n in z.namelist() if n.endswith('.xml')]
    for n in xml_parts:
        try:
            ET.fromstring(z.read(n))
            print('  XML OK:', n)
        except ET.ParseError as e:
            print('  XML ERROR:', n, '-', e)
    
    # 3. Check content types match actual parts
    ct = z.read('[Content_Types].xml').decode('utf-8')
    pattern = r'Override PartName="([^"]+)" ContentType="([^"]+)"'
    overrides = re.findall(pattern, ct)
    actual_parts = set(z.namelist())
    declared_parts = set(o[0].lstrip('/') for o in overrides)
    missing_from_ct = actual_parts - declared_parts - {'[Content_Types].xml', '_rels/.rels'}
    extra_in_ct = declared_parts - actual_parts
    if missing_from_ct:
        print('  MISSING from Content-Types:', missing_from_ct)
    if extra_in_ct:
        print('  EXTRA in Content-Types:', extra_in_ct)
    if not missing_from_ct and not extra_in_ct:
        print('  Content-Types match actual parts: OK')
    
    # 4. Check relationships
    rels = z.read('_rels/.rels').decode('utf-8')
    doc_rels = z.read('word/_rels/document.xml.rels').decode('utf-8')
    print('  _rels/.rels: OK')
    print('  word/_rels/document.xml.rels: OK')
    
    # 5. Check document body structure
    doc = z.read('word/document.xml').decode('utf-8')
    if '<w:body>' in doc and '</w:body>' in doc:
        print('  document.xml has w:body: OK')
    if '<w:document' in doc:
        print('  document.xml has w:document root: OK')
    
    print()

validate_docx('Protctus_Final_Major_Project_Report.docx')
validate_docx('Protctus_Actual_Testing_and_Verification_Report.docx')
