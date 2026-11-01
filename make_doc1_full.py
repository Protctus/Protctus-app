# -*- coding: utf-8 -*-
"""Builds Document 1: Protctus_Final_Major_Project_Report.docx (complete, consistent)."""
import sys
sys.path.insert(0, 'A:\\Protctus-app')
from build_reports import (Docx, heading, subheading, body_para, rich_para,
    bullet, numbered, page_break, spacer, table, caption, figure_caption,
    code_block, run, rich_runs, paragraph)

doc = Docx('Protctus Final Major Project Report')

def P(text, **kw):
    kw.setdefault('align', 'both')
    doc.add(body_para(text, **kw))

def B(text, lead=None):
    doc.add(bullet(text, bold_lead=lead))

def S(text, **kw):
    """Sub-paragraph, smaller."""
    kw.setdefault('align', 'both')
    doc.add(body_para(text, **kw))

# ============================ COVER ============================
doc.add(spacer(120))
doc.add(paragraph(run('PROTCTUS', bold=True, size=40, color='1F3864'), align='center', space_after=10))
doc.add(paragraph(run('On-Device, Offline-First Android Cyber-Security Platform', italic=True, size=16, color='404040'), align='center', space_after=8))
doc.add(paragraph(run('Final Major Project Report', bold=True, size=18), align='center', space_after=8))
doc.add(paragraph(run('Cybersecurity - Phishing Detection - Malware Scanning - DNS Shielding', size=12, color='595959'), align='center', space_after=40))
doc.add(table(
    ['Field', 'Details'],
    [
        ['Project title', 'Protctus - On-Device, Offline-First Android Cyber-Security Platform'],
        ['Project type', 'Final Year / Major Academic Project (Android Application)'],
        ['Student name', '[PLACEHOLDER - student name not supplied]'],
        ['Enrollment number', '[PLACEHOLDER - enrollment number not supplied]'],
        ['Department', '[PLACEHOLDER - department not supplied]'],
        ['Institution', '[PLACEHOLDER - institution not supplied]'],
        ['Supervisor / guide', '[PLACEHOLDER - supervisor not supplied]'],
        ['Academic session / year', '[PLACEHOLDER - academic session not supplied]'],
        ['Submission date', '[PLACEHOLDER - submission date not supplied]'],
        ['Package name', 'com.protctus.app'],
        ['Minimum SDK', 'Android API level 27'],
        ['Target SDK', 'Android API level 35'],
    ],
    widths=[2400, 6600], header_shade='1F3864', header_color='FFFFFF', body_size=10, align='left', col_aligns=['left', 'left']))
doc.add(spacer(60))
doc.add(paragraph(run('Submitted in partial fulfillment of the requirements for the award of the degree in Computer Science / Information Technology.', size=11, italic=True, color='595959'), align='center', space_after=0))
doc.add(page_break())

# ============================ CERTIFICATE ============================
doc.add(heading('Certificate', level=1))
P('This is to certify that the project report entitled "Protctus - On-Device, Offline-First Android Cyber-Security Platform" is a bonafide project work carried out by [PLACEHOLDER - student name], Enrollment No. [PLACEHOLDER - enrollment number], under the supervision of [PLACEHOLDER - supervisor name], Department of [PLACEHOLDER - department], [PLACEHOLDER - institution], in partial fulfillment of the requirements for the award of the degree. The work presented in this report is original to the best of my knowledge and has not been submitted elsewhere for any other degree or diploma.')
doc.add(spacer(30))
doc.add(table(
    ['', ''],
    [
        ['', '[PLACEHOLDER - Supervisor / Guide signature]'],
        ['', '[PLACEHOLDER - Head of Department signature]'],
        ['', '[PLACEHOLDER - External Examiner signature]'],
        ['', '[PLACEHOLDER - Date of approval]'],
    ],
    widths=[2200, 6800], header_shade='FFFFFF', body_size=10))
P('Note: All academic details (student name, enrollment number, supervisor, department, institution, session, signatures and dates) are placeholders pending institutional completion. They are left editable.', size=9, italic=True, color='595959', space_before=12)
doc.add(page_break())

# ============================ DECLARATION ============================
doc.add(heading('Student Declaration', level=1))
P('I, [PLACEHOLDER - student name], Enrollment No. [PLACEHOLDER - enrollment number], a student of [PLACEHOLDER - institution], hereby declare that the project report entitled "Protctus - On-Device, Offline-First Android Cyber-Security Platform" submitted by me is my own original work and has been carried out under the guidance and supervision of [PLACEHOLDER - supervisor name]. This report has not been submitted, in whole or in part, to any other university or institution for the award of any degree or diploma.')
doc.add(spacer(30))
doc.add(table(
    ['', ''],
    [
        ['', '[PLACEHOLDER - Student signature]'],
        ['', '[PLACEHOLDER - Date]'],
        ['', '[PLACEHOLDER - Place]'],
    ],
    widths=[2200, 6800], header_shade='FFFFFF', body_size=10))
P('This declaration is provided in the standard institutional format.', size=9, italic=True, color='595959', space_before=12)
doc.add(page_break())

# ============================ ACKNOWLEDGEMENT ============================
doc.add(heading('Acknowledgement', level=1))
P('I express my sincere gratitude to my project supervisor [PLACEHOLDER - supervisor name] for guidance, encouragement and technical insights throughout this project. I thank the Head of Department [PLACEHOLDER - HOD name] and the faculty of [PLACEHOLDER - department] for their support and for providing the academic environment in which this work was carried out.')
P('I also thank my family and friends for their patience and encouragement during the development and evaluation phases of this project.')
P('The development of Protctus relied on publicly available, genuine datasets and open source tooling. Where external data or references were used, they are cited in the References section; no proprietary or confidential data was used.')
doc.add(spacer(20))
doc.add(table(
    ['', ''],
    [
        ['', '[PLACEHOLDER - Student signature]'],
        ['', '[PLACEHOLDER - Date]'],
    ],
    widths=[2200, 6800], header_shade='FFFFFF', body_size=10))
doc.add(page_break())

# ============================ ABSTRACT ============================
doc.add(heading('Abstract', level=1))
P('Protctus is an Android application designed to protect users from phishing and malware threats using entirely on-device, offline-first detection. Motivated by the rising volume of phishing URLs and malicious Android packages, and by privacy concerns around uploading browsing data to cloud AI services, the project implements local detection that does not send URLs or browsing data to any remote service by default.')
P('The principal subsystems are: (1) NeuroShield, an on-device phishing-detection engine that combines a Bloom-filter whitelist, heuristic analysis (BK-Tree brand proximity, Markov-chain DGA detection, Viterbi segmentation and structural anomaly scoring) and a calibrated Logistic-Regression ONNX model using 53 lexical features, with a battery-efficient LightGBM cascade; (2) a Malware Engine comprising Quark behavioral rules, Clam-style hash and byte-pattern matching, YARA, Androguard APK inspection, a Bloom-filter signature database and an additive 68-feature-vector malware model; and (3) a PhishingVpnService providing real-time split-tunnel DNS shielding that intercepts DNS queries locally.')
P('The NeuroShield ONNX model was trained and evaluated offline in Python on genuine held-out data: 380,000 benign (Tranco-style) domains and 386,413 phishing domains (after removing 200 overlaps), split 60/20/20 with domain-grouping to prevent leakage. On a 50,000-per-class held-out sample the production LogisticRegression ONNX model achieved accuracy 0.9274, recall 0.8935, precision 0.9517, F1 0.9217 and FPR 0.0415 (threshold 0.5730 selected on validation only). External validation on an independent OpenPhish feed (298 hostnames, 894 benign) gave accuracy 0.9287 and recall 0.8859 but lower precision (0.8381), reflecting a lexical-only model\u2019s difficulty with newly registered or obscure domains. The 0.95 accuracy and 0.95 recall targets were not met and are disclosed honestly.')
P('Key engineering limitations are documented: the APK could not be built or tested on a device in this environment (no Android SDK; YARA native sources missing, blocking the native CMake step); the TFLite model could not be evaluated (interpreter blocked); Android unit/instrumented tests were not executed; and the Malware Engine was not evaluated on labeled real malware data because no device or emulator was available. Privacy controls (cloud telemetry gated behind explicit user consent, default OFF) were implemented and verified by code inspection.')
P('The project demonstrates that a privacy-first, fully on-device Android security scanner is implementable, but the detection performance remains below the 0.95/0.95 target and Android-side behaviour is unverified pending a device-capable build environment.')
doc.add(page_break())

# ============================ TABLE OF CONTENTS ============================
doc.add(heading('Table of Contents', level=1))
P('The following table of contents lists the chapters and principal sections of this report. Page numbers finalize after typesetting in Microsoft Word.', size=10, italic=True, color='595959')
toc_entries = [
    ('Preliminary Pages', None),
    ('Cover page', 'i'),
    ('Certificate', 'ii'),
    ('Student declaration', 'ii'),
    ('Acknowledgement', 'iii'),
    ('Abstract', 'iv'),
    ('Table of contents', 'v'),
    ('List of figures and tables', 'vi'),
    ('Abbreviations, acronyms and full forms', 'vii'),
    ('Glossary of technical terms', 'x'),
    ('Chapter 1 - Introduction', '1'),
    ('Chapter 2 - Existing Systems and Proposed Approach', '4'),
    ('Chapter 3 - Requirements and System Architecture', '7'),
    ('Chapter 4 - Technologies and Development Methodology', '12'),
    ('Chapter 5 - System Implementation', '15'),
    ('Chapter 6 - Machine Learning and Detection Methodology', '23'),
    ('Chapter 7 - Results and Discussion', '30'),
    ('Chapter 8 - Security, Privacy and Limitations', '36'),
    ('Chapter 9 - Conclusion and Future Work', '40'),
    ('References', '43'),
    ('Appendices', '45'),
    ('Subject Index', '50'),
]
toc_rows = [[t, '' if p is None else p] for t, p in toc_entries]
doc.add(table(['Section', 'Page'], toc_rows, widths=[7400, 1600], body_size=10, align='left', col_aligns=['left', 'right']))
doc.add(page_break())

# ============================ LIST OF FIGURES AND TABLES ============================
doc.add(heading('List of Figures and Tables', level=1))
subheading('Figures')
doc.add(table(['No.', 'Figure', 'Caption', 'Page'],
    [
        ['1', 'Figure 3.1', 'High-level system architecture of Protctus.', '-'],
        ['2', 'Figure 3.2', 'NeuroShield phishing detection data-flow (per URL/DNS query).', '-'],
        ['3', 'Figure 3.3', 'Malware scanning architecture and threat correlation.', '-'],
        ['4', 'Figure 3.4', 'PhishingVpnService split-tunnel DNS shielding flow.', '-'],
        ['5', 'Figure 5.1', 'Android application layering (UI - ViewModel - Repository - Engine).', '-'],
    ],
    widths=[700, 1400, 5600, 1300], body_size=9))
doc.add(spacer(8))
subheading('Tables')
doc.add(table(['No.', 'Table', 'Caption', 'Page'],
    [
        ['1', 'Table 6.1', 'NeuroShield ONNX held-out evaluation results (50k sample).', '-'],
        ['2', 'Table 6.2', 'Model comparison on same held-out test set.', '-'],
        ['3', 'Table 6.3', 'Recall >= 0.95 operating-point assessment.', '-'],
        ['4', 'Table 6.4', 'External OpenPhish validation vs held-out test.', '-'],
        ['5', 'Table 6.5', 'Acceptance targets vs measured results.', '-'],
        ['6', 'Table 7.1', 'Baseline vs final model comparison.', '-'],
        ['7', 'Table 8.1', 'Fail-open / fail-closed behaviour summary.', '-'],
    ],
    widths=[700, 1400, 5600, 1300], body_size=9))
P('Page numbers are placeholders ("-") to be updated when the document is paginated in Microsoft Word; figure and table captions appear alongside their content in the body.', size=9, italic=True, color='595959')
doc.add(page_break())

# ============================ ABBREVIATIONS ============================
doc.add(heading('Abbreviations, Acronyms and Full Forms', level=1))
abbrev = [
    ('AI', 'Artificial Intelligence'),
    ('APK', 'Android Package (application package file format)'),
    ('API', 'Application Programming Interface'),
    ('CI', 'Confidence Interval (statistical)'),
    ('CPU', 'Central Processing Unit'),
    ('CMake', 'Cross-platform build system for native (C/C++) code'),
    ('DGA', 'Domain Generation Algorithm'),
    ('DNS', 'Domain Name System'),
    ('DoT', 'DNS-over-TLS'),
    ('DoH', 'DNS-over-HTTPS'),
    ('EMBER', 'Endpoint Malware & Emulation Benchmarking Research (feature dataset format)'),
    ('F1', 'F1 Score - harmonic mean of precision and recall'),
    ('FNR', 'False Negative Rate'),
    ('FPR', 'False Positive Rate'),
    ('FP', 'False Positive'),
    ('FN', 'False Negative'),
    ('GPU', 'Graphics Processing Unit'),
    ('HGB', 'Histogram-based Gradient Boosting (scikit-learn HistGradientBoostingClassifier)'),
    ('HTTPS', 'Hypertext Transfer Protocol Secure'),
    ('IDN', 'Internationalized Domain Name'),
    ('JNI', 'Java Native Interface'),
    ('JSON', 'JavaScript Object Notation'),
    ('LR', 'Logistic Regression'),
    ('ML', 'Machine Learning'),
    ('MTU', 'Maximum Transmission Unit'),
    ('NDK', 'Android Native Development Kit'),
    ('ONNX', 'Open Neural Network Exchange (model format)'),
    ('ORT', 'ONNX Runtime'),
    ('OS', 'Operating System'),
    ('PII', 'Personally Identifiable Information'),
    ('PSL', 'Public Suffix List'),
    ('RAM', 'Random Access Memory'),
    ('ROC', 'Receiver Operating Characteristic'),
    ('SDK', 'Software Development Kit'),
    ('SLD', 'Second-Level Domain'),
    ('SQLite', 'Embedded relational database (used by Room)'),
    ('TFLite', 'TensorFlow Lite (mobile model format)'),
    ('TLS', 'Transport Layer Security'),
    ('TP', 'True Positive'),
    ('TN', 'True Negative'),
    ('UI', 'User Interface'),
    ('URL', 'Uniform Resource Locator'),
    ('VPN', 'Virtual Private Network'),
    ('VpnService', 'Android system service for building VPN applications'),
    ('YARA', 'YARA pattern-matching tool (name used as-is; no official expansion)'),
]
doc.add(table(['Abbreviation', 'Full Form'], abbrev, widths=[2200, 6800], body_size=9.5, col_aligns=['left', 'left']))
doc.add(page_break())

# ============================ GLOSSARY ============================
doc.add(heading('Glossary of Technical Terms', level=1))
glossary = [
    ('Classification threshold', 'The decision boundary used to convert a model probability into a verdict. In Protctus, scores >= 0.65 are classified as phishing and scores >= 0.30 as suspicious; below 0.30 is safe.'),
    ('Confusion matrix', 'A 2x2 table summarizing classification results: true positives, false positives, true negatives and false negatives. It is the basis for accuracy, precision, recall and F1.'),
    ('Cross-dataset validation', 'Evaluating a trained model on an independent dataset not used for training or threshold tuning, to estimate generalization to unseen data.'),
    ('False positive', 'A benign domain incorrectly classified as phishing (a benign site flagged as a threat).'),
    ('False negative', 'A phishing domain incorrectly classified as safe or below threshold (a threat missed).'),
    ('Feature extraction', 'The process of computing numeric descriptors (here, 53 lexical features) from a raw domain string so a model can score it.'),
    ('Generalization', 'The ability of a model to perform well on data drawn from a distribution different from its training data (e.g., held-out vs OpenPhish).'),
    ('Heuristic detection', 'Rule-based detection that scores structural or lexical signals (entropy, TLD risk, homoglyphs, brand proximity) without a learned model.'),
    ('Inference', 'Running a trained model on an input to produce a prediction/probability.'),
    ('Model calibration', 'Adjusting raw model probabilities so they match observed frequencies (Protctus uses temperature scaling with T approx 1.0177).'),
    ('On-device processing', 'Computation performed entirely on the user\u2019s Android device, with no data sent to a remote server by default.'),
    ('Precision', 'TP / (TP + FP): the fraction of domains flagged as phishing that are truly phishing.'),
    ('Recall', 'TP / (TP + FN): the fraction of true phishing domains correctly detected.'),
    ('Signature-based detection', 'Detection by exact matching against a known list of malicious indicators (e.g., known phishing domains or file hashes).'),
    ('Static analysis', 'Inspection of a file (APK, DEX, manifest) without executing it, as used by Androguard and the Clam/YARA scanners.'),
    ('Threat telemetry', 'Detection results and hashes stored locally; optionally uploaded only with user consent.'),
    ('Training / validation / test data', 'Disjoint subsets of labeled data used to fit the model, select the threshold, and report final held-out performance, respectively.'),
]
doc.add(table(['Term', 'Definition'], glossary, widths=[2600, 6400], body_size=9.5, col_aligns=['left', 'left']))
doc.add(page_break())

# ============================ CHAPTER 1 ============================
doc.add(heading('Chapter 1 - Introduction', level=1))

subheading('1.1 Cybersecurity Problem Context')
P('Android smartphones are the primary means by which a large share of users access the internet, and consequently they are a primary target for phishing attacks and malicious applications. Phishing sites imitate trusted brands to harvest credentials, while malware apps abuse device permissions to exfiltrate data, send premium SMS or install further payloads. Many commercial security products address these threats by uploading URLs, file hashes or browsing behaviour to cloud-based analysis services. That model raises privacy concerns, requires continuous network access, and may be unsuitable for users who want detection to remain local and offline.')

subheading('1.2 Motivation')
P('Protctus was developed to explore whether a privacy-first Android security application can provide meaningful phishing and malware protection entirely on the device, without uploading browsing data or URLs to external AI providers by default. The motivation is to keep user browsing data under the user\u2019s control while still offering real-time warning and blocking of known- and heuristically-suspicious threats.')

subheading('1.3 Problem Statement')
P('Build an Android application that performs real-time phishing and malware detection on the device, using local models and heuristics, with user consent gating any cloud telemetry, and without depending on continuous cloud connectivity for the core detection path. The application must surface genuine detection verdicts (not placeholder "secure" badges) and must fail safely when a model or signal is unavailable.')

subheading('1.4 Project Objectives')
for o in [
    'Implement on-device phishing detection (NeuroShield) combining heuristics and a machine-learning model.',
    'Implement an on-device malware scanner (Quark, Clam, YARA, Androguard, Bloom filter, feature-vector model).',
    'Implement real-time DNS phishing shielding via a split-tunnel VpnService.',
    'Keep all detection local by default; gate cloud telemetry behind explicit user consent (default OFF).',
    'Train, export, calibrate and evaluate an on-device phishing model on genuine held-out data.',
    'Document honest performance against stated accuracy/recall targets and disclose all limitations.',
]:
    B(o)

subheading('1.5 Scope')
P('In scope: on-device phishing detection, on-device malware scanning of installed apps and files, real-time DNS shielding, local threat storage (Room), user consent controls for telemetry, anti-repackaging signature verification (placeholder), biometric/PIN unlock, education screens, and payments scaffolding (disabled without real keys). Out of scope: TLS inspection, domain-age lookup, redirect monitoring, cloud blocklist reputation by default, iOS/desktop platforms, and live-malware execution.')

subheading('1.6 Intended Users and Use Cases')
P('Intended users are Android users, particularly in regions where privacy-respecting, offline-first security tools are valued. Representative use cases: a user taps a suspicious link and the URL scanner warns or blocks it; the DNS shield intercepts resolution of a known phishing domain at the network level; a user initiates a malware scan of installed apps; the user toggles cloud telemetry off in settings so no detection data leaves the device.')

subheading('1.7 Constraints and Assumptions')
P('Constraints: detection runs on-device with bounded CPU/battery (models run on-demand per query, never continuously); the APK build and device testing were not possible in this environment; the phishing model is lexical-only (domain string, no URL path/TLS/reputation signals). Assumptions: the Android OS sandbox and permission model are trusted; the user grants VPN permission consciously for DNS shielding; bundled benign/phishing datasets are representative enough for held-out evaluation; dynamic feature statistics bundled with the app approximate production values.')

subheading('1.8 Intended Product Vision vs Delivered Capability')
P('The intended vision is a fully operational, device-tested security application meeting the 0.95 accuracy and 0.95 recall targets. The delivered capability is a complete implementation in source code with verified offline ML evaluation, but (a) Android-side behaviour is unverified because the APK could not be built or installed, and (b) the detection targets were not met. This report distinguishes the intended product from verified results throughout.')
doc.add(page_break())

# ============================ CHAPTER 2 ============================
doc.add(heading('Chapter 2 - Existing Systems and Proposed Approach', level=1))

subheading('2.1 Limitations of Existing Approaches')
P('Existing mobile security products generally fall into two categories: (a) cloud-reliant scanners that submit URLs and file hashes to remote reputation services, and (b) purely signature-based local scanners that match against known-bad lists. Cloud-reliant scanners can detect novel threats but expose browsing metadata and require network access; purely local signature scanners are private and fast but miss zero-day or obfuscated threats. Neither category, by itself, offers both strong privacy and heuristic detection of previously unseen phishing domains.')
P('No specific competitor products were benchmarked or tested for this project, so no comparative performance claims are made against existing products.')

subheading('2.2 Proposed Solution')
P('Protctus proposes a hybrid, on-device approach: a fast Bloom-filter whitelist and structural heuristic layer to handle the common case at near-zero cost, followed by a calibrated ML model (NeuroShield) for phishing and a multi-layer Malware Engine for apps/files, all running locally. A split-tunnel VpnService adds real-time DNS shielding. Cloud telemetry is opt-in and default OFF. Detection signals are fused by a verdict engine that never converts missing evidence into a benign classification.')

subheading('2.3 Key Design Decisions')
for lead, txt in [
    ('On-device only by default', 'Detection models and heuristics are bundled in the APK; no URL is uploaded by default. This keeps browsing data private and allows offline operation.'),
    ('Cascaded inference', 'The cheap ONNX LogisticRegression model runs on every query; the larger LightGBM model runs only when the ONNX score is in an uncertainty band (0.15-0.85), saving CPU and battery.'),
    ('Fail-safe on missing evidence', 'When no detection signal is available, the verdict is REQUIRES_NETWORK_VERIFICATION, never SAFE. Missing visibility is never treated as benign.'),
    ('Consent-gated telemetry', 'Cloud upload of phishing/malware telemetry is gated behind an explicit user-controlled DataStore setting, default OFF.'),
    ('Lexical feature schema', 'The phishing model uses 53 features derived solely from the domain string, so no network, TLS or domain-age lookups are needed (and none are claimed).'),
]:
    B(txt, lead=lead + ': ')

P('External references are cited only where the source was actually consulted. Phishing-Database (community-verified phishing domains), OpenPhish Community Feed and Tranco-style trusted lists were used as evaluation data; no academic papers were cited as experimental comparators because no head-to-head benchmarking was performed.')
doc.add(page_break())

# ============================ CHAPTER 3 ============================
doc.add(heading('Chapter 3 - Requirements and System Architecture', level=1))

subheading('3.1 Functional Requirements (as implemented)')
doc.add(table(['ID', 'Requirement', 'Implementation evidence', 'Status'],
    [
        ['FR-01', 'On-device phishing detection of a domain/URL', 'PhishingDetector (L0 whitelist -> Bloom -> IP guard -> heuristics -> ensemble ONNX/LightGBM -> verdict)', 'Implemented; Android runtime UNVERIFIED'],
        ['FR-02', 'Calibrated ML phishing model', 'NeuroShieldEngine loads neuroshield_model.onnx; temperature calibration T=1.0177', 'Implemented; offline Python verified'],
        ['FR-03', 'On-device malware scan of apps/files', 'Quark, Clam, YARA, Androguard, Bloom filter, FeatureVectorMalwareEngine', 'Implemented; UNVERIFIED on device'],
        ['FR-04', 'Real-time DNS phishing shield', 'PhishingVpnService split-tunnel, native edge_phishing_pipeline.cpp', 'Implemented; UNVERIFIED on device'],
        ['FR-05', 'User consent for cloud telemetry', 'SettingsManager DATA_SHARING_ENABLED_KEY; CloudThreatReporter/MalwareTelemetryReporter gated', 'Implemented; code-review verified'],
        ['FR-06', 'APK anti-tamper signature verification', 'SignatureVerifier with placeholder hash (UPDATE_ME...)', 'Implemented; NOT fail-closed (placeholder)'],
        ['FR-07', 'Education / microlearning screens', 'Education ViewModels and Compose screens present', 'Implemented; UNVERIFIED'],
    ],
    widths=[900, 3000, 3400, 1900], body_size=8.5, col_aligns=['left','left','left','left']))
caption('3.1 Functional requirements and implementation status.')
doc.add(page_break())

subheading('3.2 Non-Functional Requirements')
P('Privacy: detection on-device; no URL upload by default; telemetry default OFF; cleartext HTTP blocked; allowBackup disabled; broad storage permissions removed. Performance: per-query inference budget ~3 ms DNS path; models loaded once at startup. Security: fail-safe verdicts; signature verification (placeholder); no embedded real secrets (.env uses REPLACE_ME placeholders).')

subheading('3.3 System Architecture')
P('Figure 3.1 shows the high-level layering. The application is built with Jetpack Compose and Hilt dependency injection. The UI layer (Compose screens) talks to ViewModels, which talk to Repository/manager classes, which invoke the detection engines. Detection is split between phishing (com.protctus.edge.phishing and com.protctus.app.neuroshield.engine) and malware (com.protctus.app.engine.*) subsystems. Native code (C++ via JNI) implements the VPN DNS loop and supports YARA.')

arch = [
    '                          USER',
    '                           |',
    '                 Jetpack Compose UI (screens)',
    '                           |',
    '                    ViewModels (Hilt)',
    '                           |',
    '              Repository / Manager layer',
    '                           |',
    '   +---------------------+-------------------------+',
    '   |                     |                         |',
    '   PhishingDetector     Malware Engine         PhishingVpnService',
    '   (NeuroShield)         (Quark/Clam/YARA/     (split-tunnel DNS)',
    '   + heuristics          Androguard/Bloom)     + native C++ loop)',
    '   |                     |                         |',
    '   Room DB (threat alerts) Room DB (scan results)  DNS cache + blocklist',
    '                           |                         |',
    '                  Local storage only (app-private)  Upstream DNS 8.8.8.8',
    '                           |                         |',
    '                  +-----------+----------+     +-----+------+',
    '                  Cloud telemetry (gated) Local DNS   interception',
    '                  consent = OFF   default shield     (no TLS inspect)',
    '                           |                         |',
    '                   Nothing leaves device     Tunnel: DNS only',
]
doc.add(code_block(arch, size=8.5))
figure_caption('Figure 3.1 - High-level system architecture (layering and subsystem boundaries). All detection is on-device; the only network path is optional, consent-gated telemetry and the VPN\u2019s upstream DNS forwarder.')

subheading('3.4 NeuroShield Phishing Data-Flow')
P('Figure 3.2 traces a single URL/DNS query through the phishing pipeline. Layers L0-L2 are near-zero-cost guards; L3 is heuristic analysis; L4-L6 are the ensemble (calibrated ONNX, LightGBM cascade, heuristics, signature layer) fused into a verdict.')
flow = [
    'Input: domain/URL -> getSafeHost() (strip scheme/www/port/path/query)',
    'L0: user whitelist exact match -> SAFE (0.0)',
    'L1: Tranco Bloom-filter (500k domains) -> SAFE if mightContain',
    'L2: raw IP address -> HEURISTIC_CAP (0.80)',
    'L3: dynamic threat score (BK-Tree + Markov + Viterbi + structural), capped 0.80',
    'L4: calibrated ONNX LogisticRegression (53 features)',
    'L5: LightGBM cascade - runs only if ONNX score in (0.15, 0.85)',
    'L6: signature layer (exact known-phishing hosts) -> immediate PHISHING',
    'Fuse ONNX + LightGBM + heuristic + signature (re-normalized weights)',
    'VerdictEngine: PHISHING >=0.65 / SUSPICIOUS >=0.30 / SAFE / REQUIRES_NETWORK_VERIFICATION',
    'No signal -> REQUIRES_NETWORK_VERIFICATION (never SAFE)',
]
doc.add(code_block(flow, size=8.5))
figure_caption('Figure 3.2 - NeuroShield phishing detection data-flow (per URL/DNS query).')

subheading('3.5 Malware Scanning Architecture')
P('The Malware Engine correlates several independent detectors through ThreatCorrelationEngine. Quark applies behavioral "five stages of crime" rules to installed packages; Clam matches SHA256/MD5 hashes and byte patterns; YARA matches compiled rules (native, degraded to empty when the native library is unavailable); Androguard inspects APK manifest/DEX/certificate; a Bloom filter checks a signature database (asset malware_signatures.bin is missing, so the filter is empty at runtime); and the additive FeatureVectorMalwareEngine scores a 68-feature vector. Results are fused into a consensus verdict.')
malware_flow = [
    'Input: APK file / installed package (PackageManager)',
    'QuarkEngine -> behavioral rules (permissions, DEX APIs, sequence, data-flow)',
    'ClamSignatureEngine -> SHA256/MD5 hash + Boyer-Moore byte pattern (+ entropy)',
    'YaraScanner -> native YARA rules (empty results if libyara unavailable)',
    'AndroguardEngine -> APK manifest / DEX / certificate static analysis',
    'SignatureDatabase -> Bloom filter on malware_signatures.bin (MISSING -> empty)',
    'FeatureVectorMalwareEngine -> 68-feature logistic regression (synthetic demo model)',
    'ThreatCorrelationEngine -> consensus verdict (Quark + Clam + Androguard + origin context)',
]
doc.add(code_block(malware_flow, size=8.5))
figure_caption('Figure 3.3 - Malware scanning architecture and threat correlation.')

subheading('3.6 PhishingVpnService DNS Shielding Flow')
P('Figure 3.4 shows the DNS shielding path. The VpnService establishes a split-tunnel interface that routes only DNS (10.0.0.1:53) through a native C++ epoll loop. The native loop parses DNS queries, forwards them to an upstream resolver (hardcoded 8.8.8.8), and invokes the Kotlin JNI callback jniIsMalicious() which scores the domain via PhishingDetector. Blocked/suspicious domains produce notifications and Room records. Other traffic bypasses the tunnel at native speed. Private DNS (DoT/DoH) can bypass interception and triggers a user warning.')
vpn_flow = [
    'VpnService.Builder -> tun 10.0.0.2, DNS 10.0.0.1, route 10.0.0.1/32 (split-tunnel)',
    'startNativeEngine(tunFd) -> native edge_phishing_pipeline.cpp epoll loop',
    'Native: read DNS query from tun, forward UDP to upstream 8.8.8.8:53',
    'Native -> JNI jniIsMalicious(domain) -> PhishingDetector.analyzeDomainRiskDnsOnly()',
    'Score >=0.65 -> BLOCK + notify + persist to Room; 0.30-0.65 -> SUSPICIOUS + notify',
    'Score <0.30 -> allow; non-DNS traffic bypasses tunnel at native speed',
    'checkPrivateDnsBypass() -> warn if DoT/DoH enabled (may bypass interception)',
    'getNativeMetrics() -> returns zeros (placeholder, not actionable)',
]
doc.add(code_block(vpn_flow, size=8.5))
figure_caption('Figure 3.4 - PhishingVpnService split-tunnel DNS shielding flow.')

P('Trust boundaries: the device/app process and Android OS are trusted hosts; the user is partially trusted (may allowlist domains or grant VPN permission); local storage is app-private and trusted; the network and upstream DNS (8.8.8.8) are untrusted/partially trusted; external AI/cloud services are untrusted and not used by default.')
doc.add(page_break())

# ============================ CHAPTER 4 ============================
doc.add(heading('Chapter 4 - Technologies and Development Methodology', level=1))

subheading('4.1 Technologies Used')
doc.add(table(['Category', 'Technology', 'Version / ref', 'Role'],
    [
        ['Language', 'Kotlin', '2.0.21', 'Application source'],
        ['UI', 'Jetpack Compose', 'BOM 2024.11.00', 'Declarative UI'],
        ['Navigation', 'Navigation Compose', '2.8.4', 'Screen navigation'],
        ['DI', 'Hilt', '2.51.1', 'Dependency injection'],
        ['Database', 'Room', '2.6.1', 'Local threat/scan storage'],
        ['Preferences', 'DataStore Preferences', '1.0.0', 'User settings (incl. consent)'],
        ['ML inference', 'ONNX Runtime Android', '1.17.0', 'Run neuroshield_model.onnx on device'],
        ['ML format', 'ONNX', 'input float_input [None,53]', 'Phishing model'],
        ['ML export/training', 'scikit-learn / skl2onnx / lightgbm', 'Python 3.14.7', 'Offline training & export'],
        ['Native/C++', 'CMake + NDK', 'CMake 3.22.1, NDK r25b', 'VPN DNS loop, YARA native'],
        ['Build', 'Gradle / AGP', 'AGP 8.7.3', 'Android build'],
        ['YARA module', 'yara-core-module (Kotlin wrapper)', 'compileSdk 34', 'YARA scanner (native sources missing)'],
        ['Network', 'OkHttp / Retrofit', '4.12.0 / 2.11.0', 'HTTP client (telemetry, gated)'],
        ['Payments', 'Razorpay Checkout', '1.6.39', 'Payments scaffolding (disabled)'],
        ['Auth/DB/FCM', 'Firebase BOM', '33.6.0', 'Auth/Firestore/FCM (placeholders)'],
    ],
    widths=[1800, 3000, 2200, 2000], body_size=8.5, col_aligns=['left','left','left','left']))
caption('4.1 Principal technologies, versions and roles.')
doc.add(page_break())

subheading('4.2 Technology Selection Rationale')
P('Kotlin and Jetpack Compose were selected as the standard modern Android stack. ONNX Runtime was chosen for the phishing model because it bundles a small inference engine suitable for mobile and avoids shipping a full TensorFlow runtime. scikit-learn and LightGBM were used offline (Python) for training and export, not shipped in the APK. The native C++ VPN loop was chosen so DNS interception runs at native speed with a small per-query budget. Hilt and Room provide DI and local structured storage without external servers.')

subheading('4.3 Development and Evaluation Methodology Followed')
P('The methodology actually followed was: implement subsystems in Kotlin; harden privacy controls (remove broad permissions, consent-gate telemetry); fix evaluation bugs (threshold selection, latency inflation, split leakage, preprocessing mismatch); train and evaluate the model offline in Python on genuine held-out data; document honest results and blockers. Android builds, device testing and instrumented tests were not executable in this environment and are disclosed as blocked rather than assumed complete. This is an evidence-driven, iterative engineering approach rather than a formally prescribed SDLC that was fully executed.')
doc.add(page_break())

# ============================ CHAPTER 5 ============================
doc.add(heading('Chapter 5 - System Implementation', level=1))

subheading('5.1 Android Application Structure and Jetpack Compose UI')
P('The app (package com.protctus.app) uses a single Activity (MainActivity) with Hilt DI, Navigation Compose, Room for local storage and DataStore for settings. Screens include auth, URL scanner, settings (with the data-sharing toggle), education/microlearning and dashboard. MainActivity.registerWorkers() registers WorkManager scans/notifications. The UI was consolidated to use the on-device PhishingDetector; the former cloud RBI/Llama3 URL-upload path was removed for privacy.')
P('Status: code present; not runnable on a device in this environment (no Android SDK), so all Compose UI behaviour is UNVERIFIED at runtime.')
ui_layer = [
    'MainActivity (navigation, worker registration, Hilt entry point)',
    'ViewModels (SettingsViewModel exposes isDataSharingEnabled StateFlow)',
    'Compose screens: UrlScannerScreen, PreferencesScreen, education, auth, dashboard',
    'Repository / managers: SettingsManager, UserWhitelistManager, ThreatReporter',
    'Engines: PhishingDetector, EnsemblePhishingEngine, NeuroShieldEngine, ThreatCorrelationEngine',
    'Local storage: Room (ThreatAlert entities, DAOs), DataStore (consent flag)',
]
doc.add(code_block(ui_layer, size=8.5))
figure_caption('Figure 5.1 - Android application layering (UI -> ViewModel -> Repository -> Engine -> storage).')

subheading('5.2 NeuroShield Production Inference Pipeline')
P('NeuroShieldEngine loads neuroshield_model.onnx once at application startup (Hilt singleton), reuses the ONNX Runtime session, and runs inference on a background thread. It reads the temperature calibration (T=1.0177) from neuroshield_calibration.json. If the model fails to load, the engine enters DEGRADED mode and inference returns 0.0 (no signal), letting heuristics decide alone - fail-safe. infer() returns the calibrated probability; inferRaw() returns the uncalibrated output. Dynamic features 46-52 are populated by DynamicFeatureProvider from neuroshield_dynamic_data.json, matching production preprocessing.')
code52 = [
    'class NeuroShieldEngine(private val context: Context) {',
    '    private var ortSession: OrtSession? = null',
    '    @Volatile var isReady = false',
    '    @Volatile var temperature: Float = 1.0f',
    '    fun initialize() { // loads asset, creates OrtSession, loadCalibration()',
    '        // enters FULL mode on success, DEGRADED on failure }',
    '    fun infer(domain: String): Float = inferCalibrated(domain)',
    '    fun inferRaw(domain: String): Float { // base features (0-45) + dynamic (46-52)',
    '        // OnnxTensor.createTensor(env, floatBuffer, shape [1,53]) }',
    '    private fun calibrate(raw, T) = sigmoid(logit(raw) / T)',
    '}',
]
doc.add(code_block(code52, size=8.5))

subheading('5.3 Feature Extraction and Preprocessing')
P('LexicalFeatureExtractor computes exactly 53 features (indices 0-45 base, 46-52 dynamic) purely from the domain string: length, subdomain count, entropy, digit/alpha/vowel ratios, hyphen count, TLD length, consonant runs, punycode flag, raw-IP flag, SLD entropy/digit ratio, free-hosting flag, rare-character count, character-transition variance, etc. Dynamic features 46-52 (brand-in-SLD, TLD phishing rate, new-gTLD flag, keyword presence) come from DynamicFeatureProvider / neuroshield_dynamic_data.json. The Python evaluation port was made consistent with production by populating features 46-52 via --dynamic-data.')

subheading('5.4 Ensemble, Verdict and Calibration')
P('EnsemblePhishingEngine combines: a signature layer (exact known-phishing hosts -> immediate PHISHING), calibrated ONNX (primary), LightGBM cascade (only in uncertainty band 0.15-0.85), and detector heuristics, fused with re-normalized weights over active signals only. VerdictEngine classifies fused score: >=0.65 PHISHING, >=0.30 SUSPICIOUS, else SAFE; no signal -> REQUIRES_NETWORK_VERIFICATION. Battery design: ONNX runs on every query (~2 ms), LightGBM is skipped when ONNX is confident; inference is on-demand, never continuous.')

subheading('5.5 Malware Scanning Architecture')
P('DeepScannerService orchestrates QuarkEngine (behavioral rules), ClamSignatureEngine (hash + byte patterns), YaraScanner (native YARA; empty if libyara unavailable), AndroguardEngine (APK manifest/DEX/cert), SignatureDatabase (Bloom filter; asset missing -> empty) and FeatureVectorMalwareEngine (68-feature logistic regression; synthetic demo model). ThreatCorrelationEngine runs these concurrently and fuses a consensus verdict with origin context (sideloaded vs store). All scan on-demand; none runs continuously.')

subheading('5.6 VPN/DNS Protection')
P('PhishingVpnService implements the split-tunnel VpnService, loads vpn-core via JNI, establishes tun 10.0.0.2 with DNS 10.0.0.1, intercepts only DNS traffic, scores domains through PhishingDetector, and persists blocked/suspicious threats to Room. It warns when Private DNS (DoT/DoH) may bypass interception. Native getNativeMetrics() returns zeros (placeholder). Upstream DNS is hardcoded to 8.8.8.8 (privacy disclosure). Runtime behaviour is UNVERIFIED (no device).')

subheading('5.7 Privacy Consent and Telemetry Controls')
P('CloudThreatReporter and MalwareTelemetryReporter both gate uploads behind isDataSharingEnabled() (DataStore), default OFF. SettingsViewModel exposes the consent flag and PreferencesScreen provides a toggle. No browsing data or URLs are uploaded by default. Verified by code inspection; not runnable on device.')

subheading('5.8 Error Handling and Fail-Safe Behaviour')
P('Model load failure -> DEGRADED mode (heuristics only, score 0.0). Malformed URL -> SAFE (fail-open, avoids blocking valid traffic). No signal -> REQUIRES_NETWORK_VERIFICATION (never SAFE). VPN setup failure -> logged, traffic not intercepted. Signature verification placeholder logs and continues (not fail-closed - must be fixed for release). Exceptions in key paths are logged rather than swallowed silently.')

subheading('5.9 Resource and Battery Considerations')
P('Models are loaded once at startup and sessions reused. Inference is per-query (URL/DNS), never continuous. ONNX uses a single intra-op thread to minimize CPU contention. LightGBM is skipped outside the uncertainty band. The Markov training set (~40 MB) is released after training. The VPN tunnel routes only DNS so other traffic runs at native speed. These are design claims; device-level battery/latency measurement was not possible.')
doc.add(page_break())

# ============================ CHAPTER 6 ============================
doc.add(heading('Chapter 6 - Machine Learning and Detection Methodology', level=1))

subheading('6.1 Feature Schema (53 features)')
P('NeuroShield uses 53 lexical features computed from the domain string. Indices 0-45 are base lexical/structural features (domain length, subdomain count, domain entropy, digit/alpha/vowel ratios, digit count, dot/hyphen count, TLD length, max consonant run, punycode flag, raw-IP flag, consonant/count/vowel ratios, longest/average subdomain length, SLD length/entropy/digit/hyphen count, free-hosting flag, etc.). Indices 46-52 are dynamic features (brand-in-SLD, TLD phishing rate, new-gTLD flag, keyword presence, etc.) populated from neuroshield_dynamic_data.json in both production and evaluation. Feature order must match the training pipeline exactly because the ONNX model reads fixed indices.')

subheading('6.2 Preprocessing')
P('Domains are sanitized (scheme/www/port/path/query/fragment stripped), validated (must contain a dot, no spaces, <=253 chars, bare IPv4 excluded), deduplicated, and IDN->ASCII via idna. Dynamic features 46-52 are populated from bundled statistics. The evaluation was corrected to match production so features 46-52 are no longer zeroed.')

subheading('6.3 Deployed Model and Candidates')
P('The production asset app/src/main/assets/neuroshield_model.onnx is a 1,068-byte LogisticRegression model exported via skl2onnx (input float_input [None,53], outputs output_label and output_probability), temperature-calibrated at T=1.0177. It replaced an earlier weak/degenerate 1,068-byte asset. Candidates evaluated on the same 53 features: LogisticRegression (production), RandomForest, HistGradientBoosting and LightGBM. The TFLite model (phishing_model.tflite, 3.6 MB) is the legacy path and was not evaluated (interpreter blocked).')

subheading('6.4 Dataset Provenance and Splits')
P('Benign (negative): app/src/main/assets/vvip_domains.txt, 380,000 Tranco-style trusted domains. Phishing (positive): ml/evaluation/fixtures/phishing-domains-ACTIVE.txt, 386,613 community-verified phishing domains (retrieved 2026-10-26), 200 overlaps removed with benign. Split: stratified 60/20/20 train/val/test, seed 42, grouped by registrable domain (last two labels) to prevent subdomain leakage. Positive class = phishing. The 50k-per-class sample gave 58,911 train / 21,528 val / 19,557 test (test: 10,210 benign / 9,347 phishing). Full-data evaluation was runtime-limited; the 50k sample was used as representative.')

subheading('6.5 Threshold Selection')
P('Thresholds are selected on the validation set only (max F1), never on the test set. The production operating threshold is 0.5730. The recall>=0.95 check selects, on validation only, the highest threshold achieving recall >=0.95 with minimum FPR (not the trivial thr~0.0 with FPR~1.0).')

subheading('6.6 Held-Out Evaluation Results (NeuroShield ONNX)')
P('Table 6.1 reports the verified held-out results for the current production asset, reproduced by running evaluate_phishing.py against neuroshield_model.onnx (1,068 bytes) with dynamic features populated. Accuracy 0.9274, recall 0.8935, precision 0.9517, F1 0.9217, FPR 0.0415, FNR 0.1065; confusion matrix TN=9,786 FP=424 FN=995 TP=8,352 (test: 10,210 benign / 9,347 phishing), threshold 0.5730.')
doc.add(table(['Metric', 'Value', '95% CI'],
    [
        ['Accuracy', '0.9274', '[0.9236, 0.9312]'],
        ['Recall (phishing)', '0.8935', '[0.8871, 0.8996]'],
        ['Precision', '0.9517', '[0.9472, 0.9561]'],
        ['F1', '0.9217', '[0.9175, 0.9260]'],
        ['FPR', '0.0415', '[0.0378, 0.0454]'],
        ['FNR', '0.1065', '-'],
    ],
    widths=[3400, 1800, 3800], body_size=9.5))
caption('Table 6.1 - NeuroShield ONNX held-out results (50k sample, threshold 0.5730 from validation). Confidence intervals are bootstrap 95% (2,000 resamples).')
doc.add(page_break())

subheading('6.7 Model Comparison (same held-out set)')
doc.add(table(['Model', 'Accuracy', 'Recall', 'Precision', 'F1', 'FPR'],
    [
        ['LightGBM', '0.9313', '0.9035', '0.9503', '0.9263', '0.0433'],
        ['HistGradientBoosting', '0.9312', '0.9065', '0.9473', '0.9265', '0.0461'],
        ['RandomForest', '0.9304', '0.8958', '0.9558', '0.9248', '0.0379'],
        ['LogisticRegression (production ONNX)', '0.9274', '0.8935', '0.9517', '0.9217', '0.0415'],
        ['Baseline weak ONNX (original 1KB)', '0.8731', '0.8775', '0.8599', '0.8686', '0.1309'],
    ],
    widths=[3400, 1100, 1100, 1200, 1000, 1000], body_size=9))
caption('Table 6.2 - Model comparison on the same 50k held-out test set (domain-grouped, seed 42). None reaches 0.95 accuracy or 0.95 recall.')
P('The production LogisticRegression ONNX was chosen over the slightly more accurate LightGBM/HGB because it offers the best accuracy/battery trade-off (tiny model, ~2 ms CPU inference, single thread), with LightGBM reserved as a cascade in the uncertainty band.')

subheading('6.8 Recall >= 0.95 Operating-Point Assessment')
P('Forcing recall >= 0.95 requires lowering the threshold, which collapses accuracy and raises FPR. It is not a useful operating point.')
doc.add(table(['Model', 'Test Acc', 'Test Recall', 'Test FPR', 'Verdict'],
    [
        ['LogisticRegression', '0.8883', '0.9443', '0.1630', 'Not useful'],
        ['RandomForest', '0.8984', '0.9468', '0.1459', 'Not useful'],
        ['HistGradientBoosting', '0.8995', '0.9504', '0.1471', 'Not useful'],
    ],
    widths=[2600, 1400, 1400, 1200, 2200], body_size=9))
caption('Table 6.3 - Recall >= 0.95 is reachable only at the cost of accuracy ~0.89-0.90 and FPR ~0.145-0.163. Thresholds selected on validation only.')

subheading('6.9 External-Dataset Validation (OpenPhish, independent)')
P('The production ONNX model was evaluated on an independent external phishing dataset (OpenPhish Community Feed, retrieved 2026-10-31: 300 URLs -> 298 valid hostnames) plus 894 benign domains, using the held-out validation threshold 0.5730 (not tuned on external data). Accuracy 0.9287 and recall 0.8859 generalize well vs held-out, but precision drops to 0.8381 and FPR rises to 0.0570 because OpenPhish contains newly registered/obscure domains (roblox.com.kz, pages.dev subdomains) that look benign to a lexical-only model.')
doc.add(table(['Metric', 'External OpenPhish', 'Held-out test', 'Generalizes?'],
    [
        ['Accuracy', '0.9287', '0.9274', 'Yes (~equal)'],
        ['Recall (phishing)', '0.8859', '0.8935', 'Yes (~equal)'],
        ['Precision', '0.8381', '0.9517', 'No (drops ~0.11)'],
        ['F1', '0.8613', '0.9217', 'No (drops ~0.06)'],
        ['FPR', '0.0570', '0.0415', 'No (rises ~0.016)'],
    ],
    widths=[2600, 2000, 1800, 2600], body_size=9))
caption('Table 6.4 - External OpenPhish validation (298 phishing, 894 benign; threshold 0.5730 from held-out validation, not tuned externally). Confusion matrix: TN=843 FP=51 FN=34 TP=264.')
P('Sample size is modest (298 phishing) and single-source; cross-dataset generalization is suggested but not proven. Precision loss reflects the lexical-only feature set.')

subheading('6.10 False Positives, False Negatives and Limitations')
P('False positives include benign domains that look suspicious lexically (psychologicalscience.org, energieevitalidade.com, 124.in-addr.arpa). False negatives include newly registered or homograph-like domains (roblox.com.kz, roblox.rw, s.team-yw.com). The model is lexical-only: no URL path, TLS, reputation, domain-age or redirect signals. Generalization to future phishing is not measured (random, not time-separated, split). Temporal drift is a known limitation.')

subheading('6.11 Acceptance Targets')
doc.add(table(['Target', 'Required', 'Best measured', 'Met?'],
    [
        ['Accuracy', '>= 0.95', '0.9313 (LightGBM)', 'No'],
        ['Recall (phishing)', '>= 0.95', '0.9065 (HistGradientBoosting)', 'No'],
    ],
    widths=[2600, 1800, 2400, 1200], body_size=9.5))
caption('Table 6.5 - Acceptance targets vs measured. Both targets NOT met; disclosed honestly.')

subheading('6.12 Malware Engine Evaluation (separate from NeuroShield)')
P('The Malware Engine components exist in source (Quark, Clam, YARA, Androguard, Bloom filter, FeatureVectorMalwareEngine) but were NOT evaluated on labeled real malware data: no device or emulator is available, EMBER is incompatible (PE vs APK), and MALDROID-2020 is behind a registration wall, exceeds size limits and contains live malware APKs (not executed per safety policy). The shipped feature_vector_malware_model.json is explicitly a SYNTHETIC demonstration model and is not a claim of real detection performance. Malware metrics are therefore NOT reported or inferred from phishing metrics.')
doc.add(page_break())

# ============================ CHAPTER 7 ============================
doc.add(heading('Chapter 7 - Results and Discussion', level=1))

subheading('7.1 What Improved During Development')
P('Replacing the weak/degenerate ONNX asset with a trained LogisticRegression model improved accuracy from 0.8731 to 0.9274 and FPR from 0.1309 to 0.0415 on the held-out sample. Fixing preprocessing (dynamic features 46-52 populated) removed a train/eval mismatch. Fixing threshold selection removed the thr~0.0 artifact. Gating cloud telemetry behind consent default OFF closed a privacy violation. The ensemble with signature layer and LightGBM cascade gives battery-efficient, multi-signal detection.')
doc.add(table(['Metric', 'Baseline (weak ONNX)', 'Final (trained LR ONNX)', 'Change'],
    [
        ['Accuracy', '0.8731', '0.9274', '+0.0543'],
        ['Recall (phishing)', '0.8775', '0.8935', '+0.0160'],
        ['Precision', '0.8599', '0.9517', '+0.0918'],
        ['F1', '0.8686', '0.9217', '+0.0531'],
        ['FPR', '0.1309', '0.0415', '-0.0894'],
        ['FNR', '0.1225', '0.1065', '-0.0160'],
    ],
    widths=[2400, 2200, 2200, 2000], body_size=9))
caption('Table 7.1 - Baseline vs final model (50k held-out test, dynamic features populated).')

subheading('7.2 What the Measurements Demonstrate')
P('The measurements demonstrate that a small, on-device lexical model can separate many phishing from benign domains with precision ~0.95 at the production threshold, and that accuracy/recall generalize to an independent OpenPhish feed. They demonstrate honest, reproducible offline evaluation with domain-grouped splits and validation-only threshold selection.')

subheading('7.3 What the Measurements Do Not Demonstrate')
P('They do not demonstrate real-world deployment performance, on-device latency/battery, or Android runtime behaviour (none was executable here). They do not demonstrate malware-detection effectiveness (no labeled malware evaluation). They do not prove generalization to future or multi-source phishing (single source, random split). Precision on OpenPhish (0.8381) shows the lexical-only model misses newly registered/obscure domains.')

subheading('7.4 External vs Internal Results')
P('External accuracy (0.9287) approx held-out (0.9274) and external recall (0.8859) approx held-out (0.8935), but external precision (0.8381) drops vs held-out (0.9517) and FPR rises (0.0570 vs 0.0415). The modest, single-source external sample means these directional results are indicative, not conclusive.')

subheading('7.5 Unresolved Security and Integration Issues')
for i in [
    'APK build blocked: YARA native sources missing (yara-src/libyara/ empty) -> CMake fails.',
    'SignatureVerifier uses a placeholder hash; not fail-closed for release.',
    'Native VPN upstream DNS hardcoded to 8.8.8.8 (user DNS visible to Google).',
    'getNativeMetrics() returns zeros (placeholder; not actionable).',
    'Malware_signatures.bin asset missing -> Bloom filter empty at runtime.',
    'TFLite model unevaluated (interpreter blocked).',
    'ML accuracy/recall below 0.95/0.95 targets.',
    'Android-side behaviour (UI, VPN, malware scan) not verified on a device.',
]:
    B(i)

subheading('7.6 Features or Tests That Could Not Be Verified')
P('APK build/install, unit/instrumented test execution, manual UI interaction, real-time DNS interception on device, TFLite inference, device latency/battery measurement, and Malware Engine labeled-dataset evaluation could not be verified because no Android SDK/device/emulator was available and the native build is blocked. These are disclosed as BLOCKED/NOT VERIFIED throughout Document 2.')
doc.add(page_break())

# ============================ CHAPTER 8 ============================
doc.add(heading('Chapter 8 - Security, Privacy and Limitations', level=1))

subheading('8.1 Threat Model and Security Assumptions')
P('The threat model (docs/THREAT_MODEL.md) covers on-device phishing/malware detection, real-time DNS shielding, local storage and network integration. Trusted boundaries: device/app process, Android OS, app-private local storage. Partially trusted: user (may allowlist/grant VPN). Untrusted: network, upstream DNS (8.8.8.8), external AI/cloud. Out of scope: TLS inspection, domain-age lookup, redirect monitoring. The model is fail-safe: missing evidence is never classified safe.')

subheading('8.2 Privacy Controls and Network Behaviour')
P('Detection is 100% local by default; no URL or browsing data is sent remotely. Firebase (Auth/Firestore/FCM/Crashlytics) is configured but credentials are placeholders and not used to exfiltrate browsing data. Threat telemetry is stored locally in Room and uploaded only if the user enables data sharing (default OFF). Cleartext HTTP is blocked; system CAs only in release. The VPN tunnels only DNS; other traffic bypasses at native speed.')

subheading('8.3 Permissions')
P('MANAGE_EXTERNAL_STORAGE and BATTERY_STATS were removed (least privilege); android:allowBackup="false"; VPN permission required for DNS shielding (user-granted); USE_BIOMETRIC declared for unlock. No broad storage permission is requested.')

subheading('8.4 Signature Verification and Error Handling')
P('Runtime APK signature verification (anti-repackaging) exists but uses a placeholder hash (UPDATE_ME...) and logs-and-continues on mismatch rather than failing closed. Exceptions in key paths are logged. The placeholder must be replaced with a real expected hash and made fail-closed for release.')

subheading('8.5 Fail-Safe Decisions')
doc.add(table(['Situation', 'Behaviour', 'Fail mode'],
    [
        ['ONNX model fails to load', 'DEGRADED mode; inference returns 0.0; heuristics decide alone', 'Fail-open (no false block)'],
        ['Malformed / empty URL', 'Classified SAFE', 'Fail-open (avoid blocking valid traffic)'],
        ['No detection signal available', 'REQUIRES_NETWORK_VERIFICATION', 'Fail-safe (never SAFE)'],
        ['VPN setup failure', 'Logged; traffic not intercepted', 'Fail-open (connectivity preserved)'],
        ['Signature hash placeholder', 'Logs and continues (not fail-closed)', 'Insecure until fixed'],
        ['YARA native library unavailable', 'YaraScanner returns empty results + warning', 'Degraded (no YARA matches claimed)'],
        ['malware_signatures.bin missing', 'Bloom filter empty at runtime', 'No signature matches claimed'],
    ],
    widths=[3000, 3600, 2400], body_size=8.5))
caption('Table 8.1 - Fail-open / fail-closed behaviour summary.')

subheading('8.6 Known Limitations and Unresolved Issues')
P('The 0.95/0.95 detection targets are not met. Android-side behaviour is unverified. The APK cannot build due to missing YARA native sources. The TFLite model is unevaluated. The malware engine has no labeled-data evaluation. The feature-vector malware model is synthetic/demo. The upstream DNS is hardcoded to 8.8.8.8. getNativeMetrics() is a placebo. None of these are represented as resolved.')
P('No absolute claims are made: Protctus is not "completely secure," not "100% accurate," and does not detect every threat.', italic=True)
doc.add(page_break())

# ============================ CHAPTER 9 ============================
doc.add(heading('Chapter 9 - Conclusion and Future Work', level=1))

subheading('9.1 Achievements')
P('Protctus implements a complete on-device, offline-first Android security stack: NeuroShield phishing detection (Bloom whitelist + heuristics + calibrated ONNX + LightGBM cascade), a multi-layer Malware Engine, a split-tunnel DNS shielding VpnService, and consent-gated privacy controls. The production ONNX model was trained, exported, calibrated and evaluated on genuine held-out data (accuracy 0.9274, recall 0.8935, precision 0.9517 on the 50k sample), with honest external OpenPhish validation. All privacy violations found during the audit were fixed.')

subheading('9.2 Objectives vs Outcome')
doc.add(table(['Objective', 'Outcome'],
    [
        ['On-device phishing detection', 'Implemented; ML verified offline; Android runtime UNVERIFIED'],
        ['On-device malware scanning', 'Implemented in source; NOT evaluated on real malware data'],
        ['Real-time DNS shielding', 'Implemented in source; UNVERIFIED on device'],
        ['Local detection by default; consent-gated telemetry', 'Implemented; verified by code inspection'],
        ['Accuracy >= 0.95 and recall >= 0.95', 'NOT met (best 0.9313 / 0.9065) - disclosed'],
        ['Device-tested APK', 'NOT achieved (build blocked; no device/emulator)'],
    ],
    widths=[3600, 5400], body_size=9))
caption('9.2 - Project objectives and delivery status.')

subheading('9.3 Major Limitations')
P('Detection below target; no device/emulator testing; YARA native build blocker; missing malware_signatures.bin asset; placeholder SignatureVerifier hash; hardcoded 8.8.8.8 upstream DNS; synthetic demo malware model; lexical-only features; single-source datasets; random (not time-separated) split.')

subheading('9.4 Future Work (clearly labelled as planned, not completed)')
for f in [
    'Resolve the YARA native source blocker and achieve a debug APK build; then run unit/instrumented tests.',
    'Test on a device/emulator: UI flows, VPN DNS interception, malware scan, latency and battery.',
    'Replace the weak ONNX asset with a stronger, larger trained model; retrain on larger, diverse, time-separated phishing/benign data to approach the 0.95/0.95 targets.',
    'Add brand/reputation and URL-path signals to improve external precision on newly registered domains.',
    'Set a real SignatureVerifier hash and make anti-tamper fail-closed; configure release signing.',
    'Make the upstream DNS user-configurable (address TM-04 privacy finding); instrument real native metrics.',
    'Obtain malware_signatures.bin or document the empty-filter consequence; evaluate the Malware Engine on a compatible labeled APK dataset without executing live malware.',
    'Replace the synthetic feature-vector malware model with one trained on real Droidware 68-feature data.',
]:
    B(f)
P('All items above are future work and are not represented as completed.', italic=True)
doc.add(page_break())

# ============================ REFERENCES ============================
doc.add(heading('References', level=1))
refs = [
    '[1] Phishing-Database, "phishing-domains-ACTIVE.txt" (community-verified phishing domains). https://github.com/Phishing-Database/Phishing.Database. Accessed 2026-10-26.',
    '[2] OpenPhish Community Feed. https://www.openphish.com/feed.txt. Accessed 2026-10-31.',
    '[3] Tranco list (trusted/benign domains, referenced via bundled vvip_domains.txt). https://tranco-list.org/.',
    '[4] ONNX Runtime documentation. https://onnxruntime.ai/.',
    '[5] scikit-learn documentation (LogisticRegression, HistGradientBoostingClassifier). https://scikit-learn.org/.',
    '[6] LightGBM documentation. https://lightgbm.readthedocs.io/.',
    '[7] skl2onnx (scikit-learn to ONNX converter). https://github.com/ai-onnx/skl2onnx.',
    '[8] Android Developer documentation (VpnService, Jetpack Compose, Hilt, Room, DataStore). https://developer.android.com/.',
    '[9] YARA documentation (pattern matching tool). https://yara.readthedocs.io/.',
    '[10] OWASP MASVS / MSTG (referenced for the static security review in docs/THREAT_MODEL.md). https://owasp.org/www-project-masvs/.',
    '[11] CIC MalDroid-2020 dataset (investigated; not downloaded/executed). https://www.unb.ca/cic/datasets/maldroid-2020.html.',
]
for r in refs:
    doc.add(paragraph(run(r, size=9.5), space_after=4))
P('Only sources actually consulted or used as data are listed. No paper was cited as an experimental comparator because no head-to-head benchmarking was performed.', size=9, italic=True, color='595959')
doc.add(page_break())

# ============================ APPENDICES ============================
doc.add(heading('Appendices', level=1))

subheading('Appendix A - Reproducibility: NeuroShield Evaluation Commands')
P('Run from the repository root. Python 3.10+ with onnxruntime, numpy, scikit-learn, idna (lightgbm, skl2onnx for training/comparison).')
cmds = [
    '# Download the phishing fixture once (external source):',
    'curl -fsSL -o ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \\',
    '  https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt',
    '',
    '# Evaluate the current production ONNX asset on held-out data:',
    'python ml/evaluation/evaluate_phishing.py \\',
    '    --onnx app/src/main/assets/neuroshield_model.onnx \\',
    '    --benign app/src/main/assets/vvip_domains.txt \\',
    '    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \\',
    '    --seed 42 --max-samples 50000 \\',
    '    --dynamic-data app/src/main/assets/neuroshield_dynamic_data.json \\',
    '    --out ml/evaluation/results/latest.json',
    '',
    '# Train / export the production LogisticRegression ONNX model + calibration:',
    'python ml/evaluation/train_export_lr.py \\',
    '    --benign app/src/main/assets/vvip_domains.txt \\',
    '    --phishing ml/evaluation/fixtures/phishing-domains-ACTIVE.txt \\',
    '    --max-samples 50000 --dynamic-data app/src/main/assets/neuroshield_dynamic_data.json \\',
    '    --out app/src/main/assets/neuroshield_model.onnx \\',
    '    --calibration-out ml/evaluation/results/lr_temperature.txt \\',
    '    --seed 42',
]
doc.add(code_block(cmds, size=8.5))

subheading('Appendix B - Saved Machine-Readable Results')
doc.add(table(['File', 'Contents'],
    [
        ['ml/evaluation/results/current_asset_50k_dynamic.json', 'Reproduced held-out results for current ONNX asset (acc 0.9274, rec 0.8935, CM TN=9786 FP=424 FN=995 TP=8352).'],
        ['ml/evaluation/results/external_openphish_validation.json', 'External OpenPhish validation (TN=843 FP=51 FN=34 TP=264; acc 0.9287, rec 0.8859, prec 0.8381).'],
        ['app/src/main/assets/neuroshield_model_metrics.json', 'Metrics saved alongside the production asset.'],
        ['app/src/main/assets/neuroshield_calibration.json', 'Temperature T=1.0177.'],
        ['ml/evaluation/results/lr_temperature.txt', 'Calibration temperature record.'],
        ['evaluation/DATASET_MANIFEST.json', 'Dataset manifest and provenance.'],
    ],
    widths=[4200, 4800], body_size=8.5))
caption('Appendix B - Key saved result files.')
doc.add(page_break())

subheading('Appendix C - Verified Source Locations')
doc.add(table(['Component', 'File'],
    [
        ['NeuroShield ONNX inference', 'app/src/main/java/com/protctus/app/neuroshield/engine/NeuroShieldEngine.kt'],
        ['Feature extraction (53 features)', 'app/src/main/java/com/protctus/app/neuroshield/engine/LexicalFeatureExtractor.kt'],
        ['Verdict/thresholds', 'app/src/main/java/com/protctus/app/neuroshield/engine/VerdictEngine.kt'],
        ['Ensemble engine', 'app/src/main/java/com/protctus/app/neuroshield/engine/EnsemblePhishingEngine.kt'],
        ['LightGBM model loader', 'app/src/main/java/com/protctus/app/neuroshield/engine/LightGBMModel.kt'],
        ['Dynamic feature provider', 'app/src/main/java/com/protctus/app/neuroshield/engine/DynamicFeatureProvider.kt'],
        ['Phishing pipeline', 'app/src/main/java/com/protctus/edge/phishing/PhishingDetector.kt'],
        ['VPN/DNS service', 'app/src/main/java/com/protctus/edge/phishing/PhishingVpnService.kt'],
        ['Native DNS loop', 'app/src/main/cpp/edge_phishing_pipeline.cpp'],
        ['URL scanner UI', 'app/src/main/java/com/protctus/app/ui/screens/scanner/UrlScannerScreen.kt'],
        ['Malware correlation', 'app/src/main/java/com/protctus/app/engine/correlation/ThreatCorrelationEngine.kt'],
        ['Quark engine', 'app/src/main/java/com/protctus/app/engine/quark/QuarkEngine.kt'],
        ['Clam engine', 'app/src/main/java/com/protctus/app/engine/clam/ClamSignatureEngine.kt'],
        ['YARA scanner (Kotlin)', 'yara-core-module/src/main/java/com/protctus/yaracore/YaraScanner.kt'],
        ['Feature-vector malware engine', 'app/src/main/java/com/protctus/app/engine/ml/FeatureVectorMalwareEngine.kt'],
        ['Settings/consent', 'app/src/main/java/com/protctus/app/data/local/SettingsManager.kt'],
        ['Cloud telemetry reporter', 'app/src/main/java/com/protctus/edge/phishing/CloudThreatReporter.kt'],
        ['Evaluation script', 'ml/evaluation/evaluate_phishing.py'],
        ['Model training/export', 'ml/evaluation/train_export_lr.py'],
    ],
    widths=[3400, 5600], body_size=8.5))
caption('Appendix C - Verified source locations used in this report.')
doc.add(page_break())

# ============================ SUBJECT INDEX ============================
doc.add(heading('Subject Index', level=1))
P('Alphabetical index of concepts discussed in this report, with indicative page numbers to be finalized after pagination in Microsoft Word.', size=9, italic=True, color='595959', space_after=8)

index_entries = [
    ('A', ['Acceptance targets', 'Accuracy', 'Android application structure', 'Anti-repackaging (signature verification)', 'APK'], ['7', '6,7', '5', '8', '5']),
    ('D', ['Data-flow (NeuroShield)', 'DNS shielding', 'Domain-grouped split', 'Dynamic features'], ['3', '3,7', '6', '5,6']),
    ('F', ['Fail-safe / fail-open behaviour', 'False positive / false negative', 'Feature schema (53)'], ['5,8', '6', '5,6']),
    ('M', ['Malware Engine', 'Machine learning methodology', 'Model calibration'], ['3,5,6', '6', '5,6']),
    ('N', ['NeuroShield', 'ONNX inference'], ['1,3,5,6', '5,6']),
    ('P', ['Phishing detection', 'Precision', 'Privacy controls'], ['1,3,5,6', '6', '2,5,8']),
    ('R', ['Recall', 'References'], ['6', 'Ref']),
    ('S', ['Signature-based detection', 'Split-tunnel VPN', 'Static analysis'], ['5', '3,5', '5']),
    ('V', ['Verdict engine', 'VPN/DNS protection'], ['5', '3,5,8']),
]
for letter, terms, pages in index_entries:
    doc.add(paragraph(run(letter, bold=True, size=11, color='1F3864'), space_before=8, space_after=2))
    for term, pg in zip(terms, pages):
        doc.add(paragraph(run('    ' + term, size=9.5) + run(' ... ' + pg, size=9.5, color='595959'), space_after=1, indent=360))
P('Page numbers are indicative and will be correct after final pagination in Microsoft Word.', size=8.5, italic=True, color='595959')

doc.save('A:\\Protctus-app\\Protctus_Final_Major_Project_Report.docx')
print('Document 1 saved: Protctus_Final_Major_Project_Report.docx')
