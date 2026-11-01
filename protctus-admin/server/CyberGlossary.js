// ═══════════════════════════════════════════════════════════════
// CyberGlossary.js — Protctus Infosec Term Protection Registry
// ═══════════════════════════════════════════════════════════════
//
// Every term in this list will be replaced with a {{N}} placeholder
// BEFORE translation and restored AFTER. This prevents Google
// Translate from mangling security-specific vocabulary into
// nonsensical local dictionary words.
//
// Priority: Longer terms first (prevents partial matches).
// E.g., "Zero-Day" must be matched before "Day".

const CYBER_GLOSSARY = [
  // ── Brand & Product Names ──
  "Protctus",
  "Chakravyuh",
  "Atmanirbhar",

  // ── Multi-Word Compound Terms (MUST be before single words) ──
  "Ransomware as a Service",
  "Initial Access Brokers",
  "Principle of Least Privilege",
  "Domain Generation Algorithms",
  "Man-in-the-Middle",
  "Volume Shadow Copy Service",
  "Endpoint Detection and Response",
  "Open Source Intelligence",
  "Domain-based Message Authentication",
  "Command and Control",
  "Group Policy Objects",
  "Double Extortion",
  "Evil Twin",
  "Spear Phishing",
  "Rogue Access Points",
  "Active Directory",
  "Out-of-Band",
  "Lateral Movement",
  "Shadow Copies",
  "Sandbox Evasion",
  "Deepfake Audio",
  "Homograph Attacks",
  "QR Phishing",
  "Authority Impersonation",

  // ── Abbreviations & Acronyms ──
  "AES-256",
  "RSA-4096",
  "HMAC-SHA256",
  "SHA-256",
  "DMARC",
  "OSINT",
  "TFLite",
  "Zero-Day",
  "RaaS",
  "IAB",
  "C2",
  "EDR",
  "HID",
  "PII",
  "MitM",
  "PoLP",
  "VPN",
  "MFA",
  "DGA",
  "VSS",
  "VoIP",
  "NAS",
  "DNS",
  "OOB",
  "RDP",
  "APK",
  "API",
  "HTTP",
  "HTTPS",
  "URL",
  "OTP",

  // ── Malware / Attack Classification ──
  "Phishing",
  "Malware",
  "Ransomware",
  "Trojan",
  "Spyware",
  "Adware",
  "Keylogger",
  "Stalkerware",
  "Rootkit",
  "Botnet",
  "Worm",
  "Exploit",
  "Payload",
  "Backdoor",

  // ── Tools & Frameworks ──
  "BloodHound",
  "Mimikatz",
  "WannaCry",
  "Wireshark",
  "Metasploit",
  "Nmap",

  // ── Techniques & Concepts ──
  "Typosquatting",
  "Quishing",
  "Vishing",
  "Whaling",
  "Pretexting",
  "Baiting",
  "Beaconing",
  "Killswitch",
  "Heuristics",
  "Cryptography",
  "Exfiltration",
  "Encryption",
  "Decryption",
  "Immutable",
  "Air-Gapped",
  "Credential Harvesting",

  // ── Infrastructure ──
  "Firewall",
  "Dark Web",
  "Captive Portal",
  "Reverse Shell",
  "Domain Admin",
];

// Sort by length descending so longer terms match first
// This prevents "Spear Phishing" from being partially matched as "Phishing"
CYBER_GLOSSARY.sort((a, b) => b.length - a.length);

module.exports = { CYBER_GLOSSARY };
