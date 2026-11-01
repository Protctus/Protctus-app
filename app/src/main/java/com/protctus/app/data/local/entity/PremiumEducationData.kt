package com.protctus.app.data.local.entity

object PremiumEducationData {

    val getPremiumNodes = listOf(
        // CATEGORY 1: "Phishing 101"
        MicrolearningCardData(
            id = "phishing_1",
            type = "INFO",
            category = "Phishing 101",
            title = "The Art of Deception",
            description = "Welcome to the Cyber Realm! Hackers rarely break through the front door—they trick you into opening it. Phishing is a social engineering attack designed to steal your credentials.",
            visualUrl = "mascot_think",
            options = emptyList(),
            correctAnswer = "",
            priority = 1,
            orderIndex = 0
        ),
        MicrolearningCardData(
            id = "phishing_2",
            type = "QUIZ_BOOL",
            category = "Phishing 101",
            title = "Spot the Fake",
            description = "If an email comes from 'security@paypal-update.com', is it a legitimate PayPal email?",
            visualUrl = "mascot_alert",
            options = listOf("Yes, it looks official", "No, it's a lookalike domain"),
            correctAnswer = "No, it's a lookalike domain",
            priority = 1,
            orderIndex = 1
        ),
        MicrolearningCardData(
            id = "phishing_3",
            type = "QUIZ_MCQ",
            category = "Phishing 101",
            title = "Urgency is a Trap",
            description = "An SMS says: 'Your bank account will be locked in 24 hours. Click here to verify.' What is the primary tactic used here?",
            visualUrl = "mascot_sad",
            options = listOf("Creating fake urgency", "Offering a reward", "Using complex tech jargon", "Asking for money directly"),
            correctAnswer = "Creating fake urgency",
            priority = 1,
            orderIndex = 2
        ),

        // CATEGORY 2: "Malware Defense"
        MicrolearningCardData(
            id = "malware_1",
            type = "INFO",
            category = "Malware Defense",
            title = "The Silent Invaders",
            description = "Malware (Malicious Software) includes viruses, ransomware, and spyware. They sneak into your device to steal data, hold files hostage, or spy on you.",
            visualUrl = "mascot_alert",
            options = emptyList(),
            correctAnswer = "",
            priority = 2,
            orderIndex = 0
        ),
        MicrolearningCardData(
            id = "malware_2",
            type = "QUIZ_BOOL",
            category = "Malware Defense",
            title = "App Permissions",
            description = "A new Flashlight app asks for permission to access your Contacts and SMS. Should you allow it?",
            visualUrl = "mascot_think",
            options = listOf("Yes, all apps need permissions", "No, flashlights don't need contacts"),
            correctAnswer = "No, flashlights don't need contacts",
            priority = 2,
            orderIndex = 1
        ),
        MicrolearningCardData(
            id = "malware_3",
            type = "QUIZ_MCQ",
            category = "Malware Defense",
            title = "Ransomware Attack",
            description = "If your files suddenly become encrypted and a pop-up demands cryptocurrency, what should you do?",
            visualUrl = "mascot_sad",
            options = listOf("Pay the ransom immediately", "Ignore it and restart", "Disconnect from the internet and seek IT help", "Format only the C: drive"),
            correctAnswer = "Disconnect from the internet and seek IT help",
            priority = 2,
            orderIndex = 2
        ),

        // CATEGORY 3: "Password Mastery"
        MicrolearningCardData(
            id = "pass_1",
            type = "INFO",
            category = "Password Mastery",
            title = "The Keys to the Kingdom",
            description = "Using 'Password123' is like locking a bank vault with a piece of tape. You need long passphrases and MFA to secure your digital life.",
            visualUrl = "mascot_idle",
            options = emptyList(),
            correctAnswer = "",
            priority = 3,
            orderIndex = 0
        ),
        MicrolearningCardData(
            id = "pass_2",
            type = "QUIZ_BOOL",
            category = "Password Mastery",
            title = "Password Reuse",
            description = "Is it safe to use a highly complex, 20-character password for all your accounts?",
            visualUrl = "mascot_alert",
            options = listOf("Yes, it's very strong", "No, one breach compromises everything"),
            correctAnswer = "No, one breach compromises everything",
            priority = 3,
            orderIndex = 1
        ),

        // CATEGORY 4: "Network Security"
        MicrolearningCardData(
            id = "net_1",
            type = "INFO",
            category = "Network Security",
            title = "Public Wi-Fi Danger",
            description = "Free airport Wi-Fi is great, until a hacker intercepts your unencrypted traffic. Always use a VPN on untrusted networks.",
            visualUrl = "mascot_alert",
            options = emptyList(),
            correctAnswer = "",
            priority = 4,
            orderIndex = 0
        ),
        MicrolearningCardData(
            id = "net_2",
            type = "QUIZ_MCQ",
            category = "Network Security",
            title = "Encryption Matters",
            description = "Which protocol ensures your web traffic is encrypted?",
            visualUrl = "mascot_think",
            options = listOf("HTTP", "HTTPS", "FTP", "DNS"),
            correctAnswer = "HTTPS",
            priority = 4,
            orderIndex = 1
        ),

        // CATEGORY 5: "Social Engineering"
        MicrolearningCardData(
            id = "se_1",
            type = "INFO",
            category = "Social Engineering",
            title = "Hacking the Human",
            description = "Attackers will impersonate IT support, delivery drivers, or even your boss to manipulate you into breaking security protocols.",
            visualUrl = "mascot_idle",
            options = emptyList(),
            correctAnswer = "",
            priority = 5,
            orderIndex = 0
        )
    )
}
