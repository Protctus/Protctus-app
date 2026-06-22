// seed_topics.js
// Script to dynamically pull Chapter IDs from Cloudflare and seed exactly 20 premium InfoSec topics.

const API_BASE = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev";
const JWT_TOKEN = "replace_with_your_admin_jwt"; // Set this to your valid Protctus Admin Token

// Highly-detailed, 80-100 word microlearning topics 
const topicDictionary = {
    "Phishing & Social Engineering 101": [
        {
            title: "The Anatomy of Spear Phishing",
            content: "Unlike traditional bulk phishing, spear phishing is a highly targeted attack directed at a specific individual or organization. Threat actors use OSINT (Open Source Intelligence) from social media and corporate websites to craft alarming, highly personalized emails. A classic spear phishing bait relies on manufacturing urgency—like threatening account deactivation or spoofing a direct manager's urgent payment request—bypassing the victim's natural skepticism. Defending against this requires strict email vetting processes, continuous awareness training, and DMARC (Domain-based Message Authentication) implementations to flag unauthorized domain spoofing before it reaches the inbox."
        },
        {
            title: "Pretexting & Authority Impersonation",
            content: "Pretexting occurs when an attacker fabricates an elaborate scenario, or 'pretext', to steal sensitive data or compel a victim to act. Rather than just asking for a password, the attacker establishes trust by impersonating authority figures—such as the IT Helpdesk, a vendor, or law enforcement. They exploit human psychology, leveraging our instinctual deference to authority and desire to be helpful. In advanced pretexting, threat actors may even reference authentic internal company jargon intercepted through prior reconnaissance, making the social engineering attempt nearly indistinguishable from legitimate corporate communications."
        },
        {
            title: "Typosquatting & Homograph Attacks",
            content: "Typosquatting targets users who incorrectly type a website address, while Homograph Attacks utilize visually similar characters—often from different Unicode alphabets, like Cyrillic—to create identical-looking URLs. For example, a user attempting to visit 'paypal.com' might accidentally click a link leading to 'paypa1.com' or 'рaypal.com' (using a Cyrillic 'р'). These malicious domains host pixel-perfect cloned login pages designed strictly for credential harvesting. Because the URL looks legitimate at a quick glance, standard browser warnings frequently fail to trigger, relying entirely on the user's granular inspection of the address bar."
        },
        {
            title: "The Evil Twin: Rogue Access Points",
            content: "An Evil Twin attack involves a hacker setting up a fraudulent Wi-Fi access point specifically designed to mimic a legitimate, trusted network—often found in coffee shops, hotels, or airports. When a victim connects to the rogue AP, the attacker successfully executes a Man-in-the-Middle (MitM) position. They can passively sniff unencrypted web traffic, hijack active session cookies, or intercept credentials in real-time. Even worse, an Evil Twin can aggressively inject a captive portal onto the user's screen, essentially forcing them to input their corporate credentials to 're-authenticate' to the internet."
        },
        {
            title: "Quishing: QR Code Threats",
            content: "Quishing, or QR Phishing, exploits the inherent inability of the human eye to parse the destination URL embedded inside a QR code. Attackers physically slap malicious QR stickers over legitimate payment codes at parking meters or restaurants, or embed them heavily into PDF attachments evading traditional text-based email security filters. Once scanned, the victim's mobile device is silently redirected to a credential harvesting portal or prompted to initiate a stealth malware download. Always use built-in mobile scanners that preview the destination domain before confirming the redirect."
        },
        {
            title: "Whaling: The C-Suite Compromise",
            content: "Whaling is a specialized subset of spear phishing that exclusively targets high-profile executives, such as CEOs, CFOs, or board members. Because these individuals possess widespread administrative access and financial authorization, a compromised C-suite account is exponentially more devastating than a standard employee breach. Attackers research executive schedules, recent corporate acquisitions, and ongoing litigation to draft highly sophisticated, context-aware emails carrying malicious attachments. Combating whaling requires implementing strict Out-of-Band (OOB) internal verification protocols for all high-value financial or wire transfers, regardless of the sender's perceived seniority."
        },
        {
            title: "Vishing & Deepfake Audio Spoofing",
            content: "Vishing (Voice Phishing) has evolved beyond simple scam calls into advanced, targeted acoustic social engineering. Utilizing Artificial Intelligence, threat actors can map an executive's voice using merely seconds of public audio from YouTube interviews or earnings calls. They deploy this synthesized Deepfake audio live over VoIP calls, issuing urgent, convincing verbal directives to fearful junior employees. This entirely bypasses standard email security gateways. Defense relies exclusively on robust Zero Trust human authentication—mandating verification through a secondary, trusted corporate channel like enterprise slack or specialized authenticator apps before complying."
        },
        {
            title: "Baiting Tactics & USB Drops",
            content: "Baiting preys on human curiosity and greed. While it can occur virtually through fraudulent 'free software' downloads, its most dangerous form is physical: the malicious USB drop. Attackers scatter malware-laden flash drives in corporate parking lots, lobbies, or breakrooms, often labeling them 'Executive Salaries 2024' or 'Confidential Firings.' An unsuspecting employee, driven by curiosity, plugs the drive into a workstation on the internal network. The device immediately executes a hidden reverse shell or keystroke logger payload masquerading as a standard keyboard peripheral (HID), breaching the physical perimeter."
        },
        {
            title: "OSINT & Reconnaissance in Social Engineering",
            content: "Before a sophisticated social engineering attack is launched, the threat actor engages in Open Source Intelligence (OSINT) gathering. By meticulously scraping LinkedIn profiles, GitHub repositories, and public forums, attackers construct extensive behavioral profiles of their targets. They identify the target's current software stack, weekend hobbies, recent vacations, and direct superiors. This passive reconnaissance phase is entirely silent and undetectable by corporate firewalls. The actionable intelligence gathered dramatically increases the success rate of subsequent phishing payloads by anchoring the malicious emails in undeniable, hyper-specific reality."
        },
        {
            title: "Defending Against the Human Element",
            content: "Despite billion-dollar security perimeters, the human remains the most vulnerable node in any cybersecurity architecture. Social engineers exploit emotions—fear, urgency, empathy, and curiosity—bypassing technological defenses entirely. Evolving past generic annual compliance videos, modern defense requires continuous, behavioral microlearning and simulated phishing campaigns designed to build actual cognitive muscle memory. Organizations must foster a 'No-Blame' reporting culture, prioritizing rapid incident notification over punishing the victim. When phishing inevitably bypasses the gateway, it is an empowered, alert workforce that stands as the final, critical line of defense."
        }
    ],
    "Ransomware Fundamentals": [
        {
            title: "Symmetric vs Asymmetric Payload Encryption",
            content: "Ransomware relies on advanced cryptography to achieve its objectives. Modern variants utilize a hybrid encryption approach to ensure rapid destruction and preventing decryption. The ransomware generates a unique Symmetric key (like AES-256) locally to execute the high-speed encryption of thousands of victim files. Immediately after, it utilizes an Asymmetric public key (like RSA-4096)—hardcoded into the malware by the attacker—to securely encrypt that specific Symmetric key. Because the victim does not possess the attacker's mathematically linked Private Asymmetric key, recovering the files independently without paying the extortion fee becomes mathematically impossible."
        },
        {
            title: "The Anatomy of Double Extortion",
            content: "Historically, ransomware focused purely on encrypting databases to grind business operations to a halt. Today, the meta has violently shifted to 'Double Extortion'. Before initial encryption algorithms are even triggered, the threat actor operates silently on the network for weeks, exfiltrating terabytes of highly sensitive intellectual property, PII, and financial records to an external offshore server. If the victim attempts to restore their systems entirely from backups and refuses the ransom, the attacker triggers the second extortion phase: threatening to leak the raw stolen data publicly on Dark Web shaming sites."
        }
    ],
    "Advanced Mobile Security": [
        {
            title: "Sim Swapping & Port-Out Fraud",
            content: "Sim Swapping is a sophisticated attack where a hacker tricks your mobile carrier into porting your phone number to a SIM card they control. Once they own your number, they can intercept all SMS-based 2FA codes for your bank and email. Protect yourself by setting a unique 'Port-Out PIN' with your carrier and switching to App-based authenticators (like Authy) instead of SMS."
        },
        {
            title: "The Android Sandbox Model",
            content: "Android uses a specialized isolation technique called sandboxing. Every application runs as a unique Linux user, meaning it cannot access the memory or files of another app without explicit permission. However, rooting your device or installing malicious 'System level' APKs can break this sandbox. Always maintain a non-rooted OS to ensure your banking and private data remains cryptographically isolated from potentially malicious third-party apps."
        }
    ],
    "Web3 & Wallet Safety": [
        {
            title: "Smart Contract Approvals",
            content: "When connecting your wallet to a dApp, you often sign an 'Approval' transaction. Malicious sites can trick you into granting 'Unlimited' permission to spend your tokens. If you don't revoke these approvals using tools like Revoke.cash, the attacker can drain your wallet weeks later without your further interaction. Always audit your active contract permissions after every trade."
        },
        {
            title: "Seed Phrase Hygiene",
            content: "Your 12 or 24-word seed phrase is the Master Key to your entire digital wealth on the blockchain. Never store it in your phone's 'Notes' app, email, or a cloud-synced screenshot, as these are primary targets for mobile malware. Physical, offline storage (such as a steel plate or paper in a safe) is the only secure way to prevent remote theft of your Web3 assets."
        }
    ]
};

async function runSeed() {
    console.log("Fetching established Chapters from Protctus D1 Database...\n");

    let chapters = [];
    try {
        const res = await fetch(`${API_BASE}/lab/chapters`, { method: "GET" });
        if (!res.ok) throw new Error("Failed to fetch chapters.");
        chapters = await res.json();
    } catch (e) {
        console.error("🚨 CRITICAL: Cannot reach the backend API to map Chapter IDs.");
        console.error(e);
        process.exit(1);
    }

    if (!chapters || chapters.length === 0) {
        console.error("🚨 CRITICAL: The D1 Database returned 0 chapters. Did you run seed_chapters.js mapping first?");
        process.exit(1);
    }

    let topicsUploaded = 0;
    let topicsFailed = 0;

    for (const [targetTitle, targetTopics] of Object.entries(topicDictionary)) {
        console.log(`\n🔍 Searching DB for Chapter: '${targetTitle}'`);
        
        // Exact string match relying on previous chapter seeding script titles
        const matchedChapter = chapters.find(c => c.title.trim() === targetTitle.trim());

        if (!matchedChapter) {
            console.error(`⚠️ Chapter '${targetTitle}' was NOT found in the database. Ensure exact DB title matches.`);
            console.log(`⚠️ Available names in DB: ${chapters.map(c=>c.title).slice(0, 3).join(', ')}...`);
            continue;
        }

        console.log(`✅ Hooked Chapter ID: ${matchedChapter.id}`);
        console.log(`🚀 Commencing payload execution for 10 micro-modules...`);

        // Loop and POST the 10 microlearning topics synchronously
        for (const topicData of targetTopics) {
            const payload = {
                chapter_id: matchedChapter.id,
                title: topicData.title,
                content: topicData.content
            };

            try {
                const response = await fetch(`${API_BASE}/admin/lab/topic`, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Authorization": `Bearer ${JWT_TOKEN}`
                    },
                    body: JSON.stringify(payload)
                });

                if (response.ok) {
                    topicsUploaded++;
                    console.log(`   ✔️ [SUCCESS] Pushed module: ${topicData.title}`);
                } else {
                    topicsFailed++;
                    const errPayload = await response.text();
                    console.error(`   ❌ [FAILED] HTTP ${response.status} on module: ${topicData.title} -> ${errPayload}`);
                }
            } catch (err) {
                topicsFailed++;
                console.error(`   ❌ [FAILED] Network Exception on module: ${topicData.title} -> ${err.message}`);
            }

            // Artificial 100ms throttle preventing D1 write bottleneck ratelimits
            await new Promise(r => setTimeout(r, 100));
        }
    }

    console.log("\n=============================================");
    console.log("SEEDS COMPLETE: The Microlearning execution is complete.");
    console.log(`Modules Inserted: ${topicsUploaded}`);
    console.log(`Modules Failed: ${topicsFailed}`);
    console.log("=============================================\n");
}

runSeed();
