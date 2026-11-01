import zipfile, re, html

def plain_text(path):
    z = zipfile.ZipFile(path)
    xml = z.read('word/document.xml').decode('utf-8')
    # Convert to readable text
    x = re.sub(r'</w:p>', '\n', xml)
    x = re.sub(r'<w:br[^>]*/>', '\n', x)
    x = re.sub(r'<[^>]+>', '', x)
    x = html.unescape(x)
    return x

print('=== DOCUMENT 1 CONTENT CHECKS ===')
p1 = plain_text('Protctus_Final_Major_Project_Report.docx')
checks1 = [
    'Certificate', 'Student Declaration', 'Acknowledgement', 'Abstract',
    'Table of Contents', 'List of Figures and Tables', 'Abbreviations, Acronyms',
    'Glossary of Technical Terms', 'Chapter 1 - Introduction',
    'Chapter 2 - Existing Systems and Proposed Approach',
    'Chapter 3 - Requirements and System Architecture',
    'Chapter 4 - Technologies and Development Methodology',
    'Chapter 5 - System Implementation', 'Chapter 6 - Machine Learning',
    'Chapter 7 - Results and Discussion', 'Chapter 8 - Security, Privacy',
    'Chapter 9 - Conclusion and Future Work', 'References', 'Appendices',
    'Subject Index', 'PLACEHOLDER', '0.9274', '0.8935', '0.9517', '0.9217',
    '0.0415', 'OpenPhish', 'TN=9,786', 'FP=424', 'FN=995', 'TP=8,352',
]
for c in checks1:
    print(('OK  ' if c in p1 else 'MISS'), c)

print()
print('=== DOCUMENT 2 CONTENT CHECKS ===')
p2 = plain_text('Protctus_Actual_Testing_and_Verification_Report.docx')
checks2 = [
    'Verification Scope', 'Implementation Change Register', 'Build and Execution',
    'Test Execution Matrix', 'NeuroShield Evaluation', 'Malware Engine Verification',
    'VPN/DNS', 'Android Integration', 'Defect Register', 'Final Verification Matrix',
    'Abbreviations, Acronyms', '0.9274', '0.8935', 'BLOCKED', 'PASS', 'NOT VERIFIED',
    'TN=843', 'FP=51', 'FN=34', 'TP=264',
]
for c in checks2:
    print(('OK  ' if c in p2 else 'MISS'), c)

print()
print('=== DISCREPANCY CHECKS ===')
# Verify both documents agree on key metrics
for metric in ['0.9274', '0.8935', '0.9517', 'TN=9,786', 'FP=424', 'FN=995', 'TP=8,352', 'TN=843', 'FP=51', 'FN=34', 'TP=264']:
    in1 = metric in p1
    in2 = metric in p2
    if in1 != in2:
        print(f'MISMATCH: {metric} in doc1={in1}, doc2={in2}')
    else:
        print(f'AGREE: {metric} in both={in1}')

print()
print('=== FILE SIZES ===')
import os
for f in ['Protctus_Final_Major_Project_Report.docx', 'Protctus_Actual_Testing_and_Verification_Report.docx']:
    print(f'{f}: {os.path.getsize(f)} bytes')
