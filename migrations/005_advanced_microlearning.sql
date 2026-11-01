-- ═══════════════════════════════════════════════════════
-- Protctus Microlearning Feed — Migration 005 (Advanced Content Expansion)
-- ═══════════════════════════════════════════════════════

-- ── ADVANCED MOBILE SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_mobile_adv_01', 'INFO', 'Advanced Mobile Security',
  'Sim Swapping & Port-Out Fraud',
  'Sim Swapping is a sophisticated attack where a hacker tricks your mobile carrier into porting your phone number to a SIM card they control. Once they own your number, they can intercept all SMS-based 2FA codes for your bank and email. Protect yourself by setting a unique "Port-Out PIN" with your carrier and switching to App-based authenticators (like Authy) instead of SMS.',
  'https://img.icons8.com/3d-fluency/256/sim-card.png',
  '[]', '', 1714900000000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_mobile_adv_02', 'QUIZ_MCQ', 'Advanced Mobile Security',
  'Managing App Sandboxes',
  'Android uses a "Sandbox" model to isolate apps. Which action most severely weakens this built-in security boundary?',
  'https://img.icons8.com/3d-fluency/256/box.png',
  '["Using a guest user profile","Rooting your device","Using an SD card for storage","Enabling developer options without ADB"]',
  'Rooting your device', 1714900100000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_mobile_adv_03', 'QUIZ_BOOL', 'Advanced Mobile Security',
  'Public USB Charging (revisited)',
  'Modern Android devices are 100% immune to Juice Jacking as long as you select "No data transfer" from the USB notification when plugging in.',
  'https://img.icons8.com/3d-fluency/256/usb-connector.png',
  '["TRUE", "FALSE"]', 'FALSE', 1714900200000, 1
);

-- ── WEB3 & WALLET SAFETY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_web3_01', 'INFO', 'Web3 & Wallet Safety',
  'Smart Contract Approvals',
  'When you "Connect Wallet" to a decentralized app (dApp), you often grant it permission to spend your tokens. Malicious dApps can trick you into signing an "Unlimited Approval" transaction, allowing them to drain your entire wallet later. Always use tools like Revoke.cash to audit and cancel active permissions regularly.',
  'https://img.icons8.com/3d-fluency/256/blockchain-technology.png',
  '[]', '', 1714900300000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_web3_02', 'QUIZ_MCQ', 'Web3 & Wallet Safety',
  'Hardware Wallet Recovery',
  'You lose your physical hardware wallet (e.g., Ledger/Trezor). What is the only way to recover your funds?',
  'https://img.icons8.com/3d-fluency/256/safe.png',
  '["Contacting the manufacturer''s support","Using your 24-word recovery seed phrase","Resetting your email password","Entering your PIN into a new device"]',
  'Using your 24-word recovery seed phrase', 1714900400000, 1
);

-- ── ENTERPRISE SECURITY CULTURE ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_enterprise_01', 'INFO', 'Enterprise Security',
  'Shadow IT Hazards',
  'Shadow IT refers to using unauthorized software or cloud services (like personal Dropbox or Trello) for corporate work without the IT department''s knowledge. This creates "Visibility Gaps" where sensitive company data is stored on unmanaged servers that lack enterprise-grade encryption or audit logs.',
  'https://img.icons8.com/3d-fluency/256/workflow.png',
  '[]', '', 1714900500000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_enterprise_02', 'QUIZ_BOOL', 'Enterprise Security',
  'Clear Desk Policy',
  'Leaving a sticky note with your "internal-only" extension numbers and seating chart on your desk is a physical security risk.',
  'https://img.icons8.com/3d-fluency/256/document.png',
  '["TRUE", "FALSE"]', 'TRUE', 1714900600000, 1
);

-- ── DARK WEB & OSINT ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_darkweb_01', 'INFO', 'Dark Web & OSINT',
  'Data Aggregation & Doxing',
  'OSINT (Open Source Intelligence) is the practice of gathering public data to build a profile of a target. Hackers combine leaked database records with your social media check-ins to perform "Doxing"—releasing your private home address and phone number online to encourage harassment.',
  'https://img.icons8.com/3d-fluency/256/search.png',
  '[]', '', 1714900700000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_darkweb_02', 'QUIZ_MCQ', 'Dark Web & OSINT',
  'Breach Response',
  'You find your email in a "Combo List" on a dark web forum. What is the most critical first step?',
  'https://img.icons8.com/3d-fluency/256/security-configuration.png',
  '["Change only that email password","Enable 2FA and change passwords for ALL accounts using that email","Delete the email account","Pay a hacker to remove the listing"]',
  'Enable 2FA and change passwords for ALL accounts using that email', 1714900800000, 1
);

-- ── AI & PROMPT INJECTION ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_ai_adv_01', 'INFO', 'AI & Prompt Injection',
  'The "Jailbreaking" of LLMs',
  'Prompt Injection is an attack where a user tricks an AI (like ChatGPT) into ignoring its safety filters by providing a specifically crafted "adversarial prompt." For businesses, this is dangerous because if an AI has access to internal databases, an attacker could trick it into leaking sensitive corporate secrets.',
  'https://img.icons8.com/3d-fluency/256/brain.png',
  '[]', '', 1714900900000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_ai_adv_02', 'QUIZ_BOOL', 'AI & Prompt Injection',
  'AI Content Reliability',
  'AI-generated phishing emails are often more successful because they lack the "traditional" red flags like poor grammar or spelling mistakes found in older scams.',
  'https://img.icons8.com/3d-fluency/256/verified-account.png',
  '["TRUE", "FALSE"]', 'TRUE', 1714901000000, 1
);
