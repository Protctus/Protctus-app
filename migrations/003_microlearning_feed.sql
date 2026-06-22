-- ═══════════════════════════════════════════════════════
-- Protctus Microlearning Feed — D1 Migration
-- Run: npx wrangler d1 execute protctus_quests_db --remote --file=./migrations/003_microlearning_feed.sql
-- ═══════════════════════════════════════════════════════

-- Create the new microlearning_feed table
CREATE TABLE IF NOT EXISTS microlearning_feed (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL DEFAULT 'INFO',
  category TEXT NOT NULL DEFAULT '',
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  visualUrl TEXT NOT NULL DEFAULT '',
  options TEXT NOT NULL DEFAULT '[]',
  correctAnswer TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL DEFAULT 0
);

-- Drop old lab data (optional — the old tables still exist for backward compat)
-- DELETE FROM lab_quizzes;
-- DELETE FROM lab_topics;
-- DELETE FROM lab_chapters;

-- ═══════════════════════════════════════════════════════
-- SEED: High-Quality Microlearning Cards
-- Categories: Phishing, Malware, Network Security, Privacy, Social Engineering
-- ═══════════════════════════════════════════════════════

-- ── PHISHING ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_phishing_01', 'INFO', 'Phishing',
  'The Anatomy of a Phishing Email',
  'Phishing emails exploit urgency and trust. They mimic legitimate brands, use spoofed sender addresses, and contain links to credential-harvesting pages. Always verify the sender domain and hover over links before clicking.',
  'https://img.icons8.com/3d-fluency/256/email.png',
  '[]', '', 1712500000000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_phishing_02', 'QUIZ_BOOL', 'Phishing',
  'Spot the Threat',
  'You receive an email from "support@amaz0n-security.com" asking you to verify your payment method within 24 hours. Is this safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/phishing.png',
  '[]', 'SCAM', 1712500100000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_phishing_03', 'QUIZ_MCQ', 'Phishing',
  'Phishing Red Flags',
  'Which of the following is the MOST reliable indicator that an email is a phishing attempt?',
  'https://img.icons8.com/3d-fluency/256/security-checked.png',
  '["The email has a company logo","The sender''s domain doesn''t match the official website","The email was sent during business hours","The email is written in English"]',
  'The sender''s domain doesn''t match the official website', 1712500200000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_phishing_04', 'QUIZ_BOOL', 'Phishing',
  'SMS Phishing (Smishing)',
  'You get a text message: "Your bank account has been locked. Click here to restore access: bit.ly/3xF9kQ2". Is this safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/sms.png',
  '[]', 'SCAM', 1712500300000
);

-- ── MALWARE ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_malware_01', 'INFO', 'Malware',
  'What is Ransomware?',
  'Ransomware is malicious software that encrypts your files and demands payment for the decryption key. It spreads through phishing emails, malicious downloads, and unpatched software vulnerabilities. Regular offline backups are your best defense.',
  'https://img.icons8.com/3d-fluency/256/ransomware.png',
  '[]', '', 1712500400000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_malware_02', 'QUIZ_MCQ', 'Malware',
  'Malware Classification',
  'A program that appears to be a free game but secretly installs a keylogger is best classified as:',
  'https://img.icons8.com/3d-fluency/256/malware.png',
  '["Worm","Rootkit","Trojan Horse","Adware"]',
  'Trojan Horse', 1712500500000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_malware_03', 'QUIZ_BOOL', 'Malware',
  'APK Sideloading Risk',
  'You found a cracked version of a popular paid app on a third-party website. The APK file is 2x the size of the official app. Is installing it safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/android.png',
  '[]', 'SCAM', 1712500600000
);

-- ── NETWORK SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_network_01', 'INFO', 'Network Security',
  'Understanding Man-in-the-Middle Attacks',
  'In a MITM attack, an adversary secretly intercepts and relays communication between two parties. Public Wi-Fi networks are prime targets. Always use a VPN on untrusted networks and verify HTTPS certificates.',
  'https://img.icons8.com/3d-fluency/256/wi-fi-connected.png',
  '[]', '', 1712500700000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_network_02', 'QUIZ_MCQ', 'Network Security',
  'VPN Knowledge Check',
  'What does a VPN primarily protect when you connect to a public Wi-Fi hotspot?',
  'https://img.icons8.com/3d-fluency/256/vpn.png',
  '["Your device battery life","Your data in transit from eavesdropping","Your device storage space","Your social media passwords from being changed"]',
  'Your data in transit from eavesdropping', 1712500800000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_network_03', 'QUIZ_BOOL', 'Network Security',
  'Free Airport Wi-Fi',
  'You are at an airport and see an open Wi-Fi network called "Airport_Free_WiFi_Fast" with no password. Is connecting to it and logging into your bank safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/wifi.png',
  '[]', 'SCAM', 1712500900000
);

-- ── PRIVACY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_privacy_01', 'INFO', 'Privacy',
  'Digital Footprint Awareness',
  'Every search query, social media post, and app permission creates a digital breadcrumb. Data brokers aggregate these into detailed profiles sold to advertisers and threat actors. Regularly audit your app permissions and limit location sharing.',
  'https://img.icons8.com/3d-fluency/256/privacy.png',
  '[]', '', 1712501000000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_privacy_02', 'QUIZ_MCQ', 'Privacy',
  'App Permission Hygiene',
  'A flashlight app requests access to your contacts, microphone, and location. What should you do?',
  'https://img.icons8.com/3d-fluency/256/smartphone.png',
  '["Grant all permissions since the app needs them","Deny all unnecessary permissions","Only grant microphone access","Uninstall your phone"]',
  'Deny all unnecessary permissions', 1712501100000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_privacy_03', 'QUIZ_BOOL', 'Privacy',
  'Data Broker Opt-Out',
  'A website offers to remove your personal data from all data brokers for free, but requires your SSN and full address to "verify your identity". Is this safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/data-protection.png',
  '[]', 'SCAM', 1712501200000
);

-- ── SOCIAL ENGINEERING ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_social_01', 'INFO', 'Social Engineering',
  'The Human Firewall',
  'Social engineering bypasses technical security by manipulating people. Attackers exploit trust, authority, and urgency. The best defense is healthy skepticism — always verify unusual requests through a separate communication channel.',
  'https://img.icons8.com/3d-fluency/256/conference-call.png',
  '[]', '', 1712501300000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_social_02', 'QUIZ_MCQ', 'Social Engineering',
  'Pretexting Attack',
  'Someone calls claiming to be IT support and asks for your password to "fix a critical server issue." What''s the best response?',
  'https://img.icons8.com/3d-fluency/256/phone.png',
  '["Give them your password since they sound legitimate","Tell them you''ll call the official IT helpdesk to verify","Ask them to email you instead","Change your password and give the new one"]',
  'Tell them you''ll call the official IT helpdesk to verify', 1712501400000
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at)
VALUES (
  'ml_social_03', 'QUIZ_BOOL', 'Social Engineering',
  'CEO Fraud',
  'You receive a WhatsApp message from your "CEO" (new number) asking you to urgently buy gift cards and send the codes. They say it is confidential. Is this safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/businessman.png',
  '[]', 'SCAM', 1712501500000
);
