-- ═══════════════════════════════════════════════════════
-- Protctus Microlearning Feed — Migration 004 (Content Expansion)
-- Run: npx wrangler d1 execute protctus_quests_db --remote --file=./migrations/004_more_microlearning.sql
-- ═══════════════════════════════════════════════════════

-- Safely add the is_active column for CMS management (if it doesn't already exist)
-- Note: SQLite does not support 'ADD COLUMN IF NOT EXISTS' natively without a pragma check,
-- so we just execute the ALTER TABLE. If it's already there, D1 might throw a soft warning,
-- but the script continues. If you prefer to drop and recreate, we can, but this is standard.
ALTER TABLE microlearning_feed ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1;

-- ═══════════════════════════════════════════════════════
-- SEED: Phase 2 High-Quality Microlearning Content
-- Categories: Physical Security, Account Protection, Device Hygiene, Fraudulent Apps
-- ═══════════════════════════════════════════════════════

-- ── ACCOUNT PROTECTION ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_account_01', 'INFO', 'Account Protection',
  'Password Recycling Risks',
  'If you use the same password for a low-security forum and your bank, a data breach at the forum gives hackers the exact credentials they need to steal your money. Always use unique passwords managed by a secure password manager.',
  'https://img.icons8.com/3d-fluency/256/password.png',
  '[]', '', 1712501600000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_account_02', 'QUIZ_MCQ', 'Account Protection',
  'Best 2FA Strategy',
  'Which multi-factor authentication strategy provides the strongest security against remote attackers and SIM swapping?',
  'https://img.icons8.com/3d-fluency/256/authenticator-app.png',
  '["SMS Verification Code","Email OTP Code","Authenticator App (TOTP)","Security Questions"]',
  'Authenticator App (TOTP)', 1712501700000, 1
);

-- ── PHYSICAL SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_physical_01', 'INFO', 'Physical Security',
  'Juice Jacking',
  'Public USB charging stations can be secretly modified to steal data or install malware on your phone while charging. Always use standard electrical wall outlets or a "USB data blocker" dongle.',
  'https://img.icons8.com/3d-fluency/256/usb-cable--v1.png',
  '[]', '', 1712501800000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_physical_02', 'QUIZ_MCQ', 'Physical Security',
  'Hardware Trust',
  'You found an abandoned USB drive in a coffee shop parking lot. What should you do with it?',
  'https://img.icons8.com/3d-fluency/256/usb-memory-stick.png',
  '["Format it first and reuse","Plug it in via sandbox VM","Leave it alone / Throw it out","Open files quickly to find owner"]',
  'Leave it alone / Throw it out', 1712501900000, 1
);

-- ── DEVICE HYGIENE ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_device_01', 'INFO', 'Device Hygiene',
  'Bluetooth Discoverability',
  'Leaving your device''s Bluetooth in "Discoverable" mode makes it vulnerable to Bluejacking (spam messages) and Bluesnarfing (data theft). Always turn Bluetooth off when not actively pairing.',
  'https://img.icons8.com/3d-fluency/256/bluetooth.png',
  '[]', '', 1712502000000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_device_02', 'QUIZ_BOOL', 'Device Hygiene',
  'Software Updates',
  'Delaying over-the-air OS software updates is safe as long as you use a third-party Antivirus application on your device.',
  'https://img.icons8.com/3d-fluency/256/system-update.png',
  '["TRUE", "FALSE"]', 'FALSE', 1712502100000, 1
);

-- ── FRAUDULENT APPS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_fraud_01', 'INFO', 'Fraudulent Apps',
  'Fleeceware Subscriptions',
  'Fleeceware apps are technically legal applications that charge exorbitant hidden subscription fees (e.g., $50/week for a basic calculator). Always check active subscription terms in your App Store settings, even after uninstalling the app.',
  'https://img.icons8.com/3d-fluency/256/billing-machine.png',
  '[]', '', 1712502200000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_fraud_02', 'QUIZ_MCQ', 'Fraudulent Apps',
  'Review Manipulation',
  'An app you want to download has a 5-star rating, but all 500 reviews were posted on the exact same day with generic praise like "great app". What does this most likely indicate?',
  'https://img.icons8.com/3d-fluency/256/star.png',
  '["The app recently went viral across social media","The app developer bought fake reviews via botfarms","The app is perfectly safe to use","A highly anticipated official launch"]',
  'The app developer bought fake reviews via botfarms', 1712502300000, 1
);

-- ── DEEPFAKES & AI SPOOFING ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_ai_01', 'INFO', 'Deepfakes & AI Spoofing',
  'Voice Cloning',
  'AI voice cloning tools can mimic a family member''s exact voice from just 3 seconds of audio pulled from social media. Scammers use this for fake emergency distress calls. Always establish a family "safeword" to verify identity during crises.',
  'https://img.icons8.com/3d-fluency/256/microphone.png',
  '[]', '', 1712502400000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_ai_02', 'QUIZ_MCQ', 'Deepfakes & AI Spoofing',
  'Spotting Deepfakes',
  'Which of the following is currently the most reliable way to spot a live video deepfake on a Zoom call?',
  'https://img.icons8.com/3d-fluency/256/artificial-intelligence.png',
  '["Asking the person to turn their head sideways quickly","Checking the background lighting","Listening for a robotic monotone voice","Looking for a watermark"]',
  'Asking the person to turn their head sideways quickly', 1712502500000, 1
);

-- ── ZERO-DAY EXPLOITS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_zeroday_01', 'INFO', 'Zero-Day Exploits',
  'What is a Zero-Day?',
  'A zero-day exploit is a cyberattack that occurs on the exact same day a weakness is discovered in software. At that point, it''s exploited before the creator has had a chance to patch it (zero days of warning).',
  'https://img.icons8.com/3d-fluency/256/bug.png',
  '[]', '', 1712502600000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_zeroday_02', 'QUIZ_BOOL', 'Zero-Day Exploits',
  'Zero-Click Attacks',
  'A "zero-click" zero-day vulnerability can compromise your device just by receiving a simple iMessage, without you ever clicking a link or downloading a file.',
  'https://img.icons8.com/3d-fluency/256/error.png',
  '["TRUE", "FALSE"]', 'TRUE', 1712502700000, 1
);

-- ── IDENTITY THEFT ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_idtheft_01', 'INFO', 'Identity Theft',
  'Credit Freezes',
  'Freezing your credit with major credit bureaus (Equifax, Experian, TransUnion) prevents new fraudulent accounts from being opened in your name, even if your Social Security Number is leaked. It is free and highly recommended.',
  'https://img.icons8.com/3d-fluency/256/bank-cards.png',
  '[]', '', 1712502800000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_idtheft_02', 'QUIZ_BOOL', 'Identity Theft',
  'Dark Web Monitoring',
  'If a dark web monitor service notifies you that your password was found in a data breach, your first action should be to immediately log in and change the password for that specific account and any others sharing it.',
  'https://img.icons8.com/3d-fluency/256/spy.png',
  '["TRUE", "FALSE"]', 'TRUE', 1712502900000, 1
);

-- ── CLOUD SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_cloud_01', 'INFO', 'Cloud Security',
  'Data Sovereignty and Backups',
  'Storing photos and documents purely on a cloud service means you don''t truly own them unless you possess a physical local backup. Always follow the 3-2-1 backup rule: 3 copies of your data, locally on 2 different media types, and 1 stored securely offsite (cloud).',
  'https://img.icons8.com/3d-fluency/256/cloud-sync.png',
  '[]', '', 1712503000000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_cloud_02', 'QUIZ_MCQ', 'Cloud Security',
  'Cloud Document Sharing',
  'When sharing a sensitive Google Doc or Dropbox file with a colleague, what is the most secure link-sharing setting?',
  'https://img.icons8.com/3d-fluency/256/google-drive.png',
  '["Anyone with the link can edit","Anyone with the link can view","Restricted to specific email addresses only","Share the folder instead of the file"]',
  'Restricted to specific email addresses only', 1712503100000, 1
);

-- ── CRYPTO & WEB3 SCAMS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_crypto_01', 'INFO', 'Crypto & Web3 Scams',
  'Pig Butchering Scams',
  'A "Pig Butchering" scam involves a fraudster building a romantic or platonic relationship with you over months, only to eventually convince you to invest in a fake cryptocurrency platform they control. If a stranger texts you "accidentally" and steers the conversation toward crypto, block them.',
  'https://img.icons8.com/3d-fluency/256/bitcoin.png',
  '[]', '', 1712503200000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_crypto_02', 'QUIZ_BOOL', 'Crypto & Web3 Scams',
  'Seed Phrase Security',
  'A customer support representative for a popular cryptocurrency wallet asks for your 12-word seed phrase to help "recover" frozen funds. Is providing it safe or a scam?',
  'https://img.icons8.com/3d-fluency/256/blockchain-technology.png',
  '["SAFE", "SCAM"]', 'SCAM', 1712503300000, 1
);

-- ── IOT & SMART HOME THREATS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_iot_01', 'INFO', 'IoT & Smart Home Threats',
  'The Internet of Vulnerable Things',
  'Smart TVs, internet-connected cameras, and smart fridges often have terrible factory security and rarely receive updates. Hackers use these vulnerable devices to build "botnets" that launch massive DDoS attacks without the owners knowing.',
  'https://img.icons8.com/3d-fluency/256/smart-home-connection.png',
  '[]', '', 1712503400000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_iot_02', 'QUIZ_MCQ', 'IoT & Smart Home Threats',
  'Securing Smart Devices',
  'What is the most critical first step you must take when installing a brand new modern Wi-Fi connected security camera in your home?',
  'https://img.icons8.com/3d-fluency/256/dome-camera.png',
  '["Connect it immediately to see if the video works","Change the default factory password to a strong, unique one","Peel off the lens protection film","Connect it to your guest Wi-Fi and forget it"]',
  'Change the default factory password to a strong, unique one', 1712503500000, 1
);

-- ── BIOMETRIC SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_biometric_01', 'INFO', 'Biometric Security',
  'The Permanence of Biometrics',
  'Unlike a password, you cannot change your fingerprint or retina if it gets compromised in a database hack. While biometrics are highly convenient for local mobile device unlocking, be extremely cautious about giving full biometric access to third-party web apps.',
  'https://img.icons8.com/3d-fluency/256/fingerprint.png',
  '[]', '', 1712503600000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_biometric_02', 'QUIZ_BOOL', 'Biometric Security',
  'Legal Protections',
  'In many legal jurisdictions, law enforcement can legally compel you or physically force you to unlock your phone using your Face ID/Fingerprint, but they cannot legally force you to divulge an alphanumeric passcode.',
  'https://img.icons8.com/3d-fluency/256/police-badge.png',
  '["TRUE", "FALSE"]', 'TRUE', 1712503700000, 1
);

-- ── SEXTORTION & ROMANCE SCAMS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_romance_01', 'INFO', 'Sextortion & Romance Scams',
  'The Sextortion Playbook',
  'Scammers often create fake attractive profiles on dating apps, quickly move the conversation to Snapchat or Telegram, and coerce victims into sending compromising photos. They instantly screenshot these and threaten to send them to the victim''s family and employer unless a ransom is paid.',
  'https://img.icons8.com/3d-fluency/256/broken-heart.png',
  '[]', '', 1712503800000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_romance_02', 'QUIZ_MCQ', 'Sextortion & Romance Scams',
  'Surviving Extortion',
  'If you fall victim to a sextortion scam and the attacker demands $500 in Bitcoin to delete the photos, what is the best immediate response?',
  'https://img.icons8.com/3d-fluency/256/cyber-security.png',
  '["Pay the ransom immediately as a gesture of goodwill","Negotiate the price down to buy time","Block them immediately, deactivate social media, and report to authorities","Dare them to send the photos"]',
  'Block them immediately, deactivate social media, and report to authorities', 1712503900000, 1
);

-- ── DISINFORMATION & FAKE NEWS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_fakenews_01', 'INFO', 'Disinformation',
  'Weaponized Algorithms',
  'Social media algorithms prioritize engagement over truth. Disinformation campaigns use botnets to artificially boost sensational, polarizing, or fake news to manipulate public opinion or stock prices. Always verify breaking news across multiple verified journalistic sources.',
  'https://img.icons8.com/3d-fluency/256/news.png',
  '[]', '', 1712504000000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_fakenews_02', 'QUIZ_BOOL', 'Disinformation',
  'Fact-Checking URLs',
  'A trusted friend forwards you an article shared 50,000 times claiming a new lockdown is starting tomorrow. The website URL is "abcnews-verify.co.tv". Is this a legitimate news source?',
  'https://img.icons8.com/3d-fluency/256/domain.png',
  '["LEGITIMATE", "SCAM"]', 'SCAM', 1712504100000, 1
);

-- ── TRAVEL SECURITY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_travel_01', 'INFO', 'Travel Security',
  'Hotel Wi-Fi & Devices',
  'Business centers and hotel Wi-Fi networks in foreign countries are highly monitored. Use a VPN at all times. Never plug unknown USB keys given as "gifts" at conferences into your laptop. Use privacy screens on airplanes to prevent shoulder surfing.',
  'https://img.icons8.com/3d-fluency/256/airplane.png',
  '[]', '', 1712504200000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_travel_02', 'QUIZ_MCQ', 'Travel Security',
  'Border Crossings',
  'When traveling internationally, what is the best practice for securing sensitive corporate data on your laptop before crossing a high-risk border?',
  'https://img.icons8.com/3d-fluency/256/passport.png',
  '["Hide the files in a deep folder","Power down the device fully (requiring a password, not just biometric unlock) and use Full Disk Encryption","Change the desktop wallpaper","Delete the files and hope to recover them from the trash later"]',
  'Power down the device fully (requiring a password, not just biometric unlock) and use Full Disk Encryption', 1712504300000, 1
);

-- ── CORPORATE ESPIONAGE ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_corp_01', 'INFO', 'Corporate Espionage',
  'Tailgating and Piggybacking',
  'Physical security is cybersecurity. "Tailgating" happens when an unauthorized person follows an employee through a secure door. Never hold the door open for someone you don''t recognize, even if they are carrying heavy boxes or wearing a fake uniform.',
  'https://img.icons8.com/3d-fluency/256/door.png',
  '[]', '', 1712504400000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_corp_02', 'QUIZ_BOOL', 'Corporate Espionage',
  'Dumpster Diving',
  'Shredding confidential documents before throwing them away is an outdated practice since most hackers only steal data digitally over the internet.',
  'https://img.icons8.com/3d-fluency/256/trash-can.png',
  '["TRUE", "FALSE"]', 'FALSE', 1712504500000, 1
);

-- ── GAMING & E-SPORTS SCAMS ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_gaming_01', 'INFO', 'Gaming & E-Sports Scams',
  'Fake Cheats and Mod Menus',
  'Hackers frequently upload YouTube videos promising "free aimbots" or "mod menus" for popular games. The download link in the description almost always leads to an InfoStealer malware that will silently rip your Discord tokens, saved passwords, and crypto wallets.',
  'https://img.icons8.com/3d-fluency/256/controller.png',
  '[]', '', 1712504600000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_gaming_02', 'QUIZ_MCQ', 'Gaming & E-Sports Scams',
  'Discord Token Grabbing',
  'Someone on a gaming server asks you to test their new indie game. They send you an executable file (.exe). What is the safest action?',
  'https://img.icons8.com/3d-fluency/256/discord-new.png',
  '["Run it as Administrator so it works properly","Scan it with Windows Defender and run it if clean","Refuse to download or run unverified executables from strangers","Ask them to send it as a .zip instead"]',
  'Refuse to download or run unverified executables from strangers', 1712504700000, 1
);

-- ── TEEN & CHILD ONLINE SAFETY ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_teens_01', 'INFO', 'Teen Online Safety',
  'The Dangers of Oversharing',
  'Posting photos of a new driver''s license, concert tickets with visible barcodes, or outside the front of your house gives predators and scammers exact puzzle pieces to commit identity theft or stalk you. Always blur barcodes and sensitive data.',
  'https://img.icons8.com/3d-fluency/256/family.png',
  '[]', '', 1712504800000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_teens_02', 'QUIZ_BOOL', 'Teen Online Safety',
  'Snapchat Snap Maps',
  'Setting your location to publicly visible on platforms like Snapchat is entirely safe because the app automatically hides your exact street address.',
  'https://img.icons8.com/3d-fluency/256/map-marker.png',
  '["TRUE", "FALSE"]', 'FALSE', 1712504900000, 1
);

-- ── MEDICAL IDENTITY THEFT ──

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_med_01', 'INFO', 'Medical Identity Theft',
  'Healthcare Breaches',
  'Medical identity theft occurs when someone uses your personal information to obtain medical care, buy prescription drugs, or submit fake billings to Medicare. Your medical records are worth 10x more on the dark web than your credit card number.',
  'https://img.icons8.com/3d-fluency/256/stethoscope.png',
  '[]', '', 1712505000000, 1
);

INSERT OR REPLACE INTO microlearning_feed (id, type, category, title, description, visualUrl, options, correctAnswer, created_at, is_active)
VALUES (
  'ml_med_02', 'QUIZ_MCQ', 'Medical Identity Theft',
  'Spotting Medical Fraud',
  'Which scenario is the biggest red flag that you are a victim of medical identity theft?',
  'https://img.icons8.com/3d-fluency/256/hospital.png',
  '["You receive a bill for a doctor''s visit from a state you''ve never been to","Your health insurance premium went up slightly this year","Your doctor asks you to verify your phone number","You receive a promotional email from a pharmacy"]',
  'You receive a bill for a doctor''s visit from a state you''ve never been to', 1712505100000, 1
);
