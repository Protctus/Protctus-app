// seed_chapters.js
// Node script to inject 20 initial Lab Chapters into the Cloudflare D1 Database via Worker endpoints.
// Usage: node seed_chapters.js

const API_BASE = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev";
const JWT_TOKEN = "your_admin_jwt_token_here"; // Requires auth if the /admin/* route is protected!

const chapters = [
    { title: "Phishing & Social Engineering 101" },
    { title: "Ransomware Fundamentals" },
    { title: "Zero-Day Vulnerabilities" },
    { title: "Multi-Factor Authentication (MFA)" },
    { title: "Physical Security & Tailgating" },
    { title: "Public Wi-Fi Risks & VPNs" },
    { title: "Mobile Device Management (MDM)" },
    { title: "Password Hygiene & Managers" },
    { title: "Data Privacy & PII Handling" },
    { title: "Malware Types & Defense" },
    { title: "Insider Threats" },
    { title: "Cloud Security Defaults" },
    { title: "Incident Response Basics" },
    { title: "Secure Remote Work" },
    { title: "Device Encryption" },
    { title: "Software Supply Chain Attacks" },
    { title: "Deepfakes & AI Threats" },
    { title: "IoT Device Risks" },
    { title: "Browser Extensions & Malvertising" },
    { title: "Open Source Intelligence (OSINT)" }
];

async function seed() {
    console.log("Starting Chapter Seeding Process...");
    let successCount = 0;

    for (const chapter of chapters) {
        try {
            const response = await fetch(`${API_BASE}/admin/lab/chapter`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${JWT_TOKEN}`
                },
                body: JSON.stringify(chapter)
            });

            if (response.ok) {
                const data = await response.json();
                console.log(`✅ Seeded: ${chapter.title} (ID: ${data.id})`);
                successCount++;
            } else {
                console.error(`❌ Failed: ${chapter.title} - ${response.statusText}`);
            }
        } catch (error) {
            console.error(`❌ Error fetching for ${chapter.title}:`, error.message);
        }
    }

    console.log(`\n🎉 Seeding Complete! ${successCount}/${chapters.length} chapters successfully uploaded.`);
}

seed();
