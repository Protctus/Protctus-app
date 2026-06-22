// auto_translate_strings.js
// Node.js script to automatically generate Android strings.xml for 6 Indian languages.
// Usage: 
// 1. npm install @vitalets/google-translate-api
// 2. node auto_translate_strings.js

const fs = require('fs');
const path = require('path');

// We use dynamic import for ESM modules (google-translate-api is ESM)
const importTranslate = async () => {
    try {
        const { translate } = await import('@vitalets/google-translate-api');
        return translate;
    } catch (e) {
        console.error("Please run: npm install @vitalets/google-translate-api");
        process.exit(1);
    }
};

const BASE_STRINGS_PATH = path.join(__dirname, 'app', 'src', 'main', 'res', 'values', 'strings.xml');
const RES_DIR = path.join(__dirname, 'app', 'src', 'main', 'res');

const TARGET_LANGUAGES = ['hi', 'kn', 'bn', 'ta', 'te', 'or'];

// Protect these specific infosec terms from being mistranslated into bizarre local dictionary words
const CYBER_GLOSSARY = [
    "Phishing", "Malware", "Ransomware", "Trojan", "Protctus", 
    "Spyware", "Adware", "Zero-Day", "OSINT", "VPN", "MFA"
];

// Android placeholder regex (e.g., %s, %1$s, %d)
const PLACEHOLDER_REGEX = /%([0-9]+\$)?s|%([0-9]+\$)?d/g;

/**
 * Escapes characters for Android XML: ', ", &, <, >
 */
function escapeAndroidString(str) {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/'/g, "\\'")
        .replace(/"/g, '\\"');
}

/**
 * Unescapes base Android string so we translate raw text
 */
function unescapeAndroidString(str) {
    return str
        .replace(/\\'/g, "'")
        .replace(/\\"/g, '"')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>');
}

/**
 * Masks placeholders and glossary terms to prevent destruction by Google Translate
 */
function maskString(text) {
    let maskedText = text;
    const tokens = [];

    // Mask Placeholders
    let match;
    while ((match = PLACEHOLDER_REGEX.exec(text)) !== null) {
        tokens.push(match[0]);
        maskedText = maskedText.replace(match[0], `__T${tokens.length - 1}__`);
    }

    // Mask Glossary Terms (Case Insensitive Match)
    CYBER_GLOSSARY.forEach(term => {
        const regex = new RegExp(`\\b${term}\\b`, 'gi');
        let termMatch;
        while ((termMatch = regex.exec(text)) !== null) {
            tokens.push(termMatch[0]);
            maskedText = maskedText.replace(regex, `__T${tokens.length - 1}__`);
        }
    });

    return { maskedText, tokens };
}

/**
 * Restores original tokens into translated text
 */
function unmaskString(translatedText, tokens) {
    let restored = translatedText;
    for (let i = 0; i < tokens.length; i++) {
        // Google translate sometimes adds spaces around words, handles __T 0 __
        const regex = new RegExp(`__\\s?T\\s?${i}\\s?__`, 'g');
        restored = restored.replace(regex, tokens[i]);
    }
    return restored;
}

async function processTranslations() {
    const translate = await importTranslate();

    if (!fs.existsSync(BASE_STRINGS_PATH)) {
        console.error(`Base strings.xml not found at: ${BASE_STRINGS_PATH}`);
        return;
    }

    const baseXml = fs.readFileSync(BASE_STRINGS_PATH, 'utf-8');
    
    // Regex parsing string tags
    const stringTagRegex = /<string name="([^"]+)"(?:[^>]*)>([\s\S]*?)<\/string>/g;
    const translatableFalseRegex = /translatable="false"/i;

    const baseStrings = [];
    let match;
    while ((match = stringTagRegex.exec(baseXml)) !== null) {
        const fullTag = match[0];
        const key = match[1];
        const value = match[2];

        if (translatableFalseRegex.test(fullTag)) {
            continue; // Skip items marked translatable="false"
        }
        baseStrings.push({ key, rawValue: value });
    }

    console.log(`Found ${baseStrings.length} translatable strings. Beginning translation pipeline...`);

    for (const lang of TARGET_LANGUAGES) {
        const targetDir = path.join(RES_DIR, `values-${lang}`);
        if (!fs.existsSync(targetDir)) {
            fs.mkdirSync(targetDir, { recursive: true });
        }
        
        const targetFile = path.join(targetDir, 'strings.xml');
        
        let existingTranslations = {};
        if (fs.existsSync(targetFile)) {
            const existingXml = fs.readFileSync(targetFile, 'utf-8');
            let exMatch;
            while ((exMatch = stringTagRegex.exec(existingXml)) !== null) {
                existingTranslations[exMatch[1]] = exMatch[2];
            }
        }

        let outputXml = `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n`;
        let newTranslationsCount = 0;

        for (const item of baseStrings) {
            if (existingTranslations[item.key]) {
                // Already translated
                outputXml += `    <string name="${item.key}">${existingTranslations[item.key]}</string>\n`;
            } else {
                try {
                    const unescaped = unescapeAndroidString(item.rawValue);
                    const { maskedText, tokens } = maskString(unescaped);
                    
                    const res = await translate(maskedText, { to: lang });
                    
                    const restoredText = unmaskString(res.text, tokens);
                    const finalEscaped = escapeAndroidString(restoredText);

                    outputXml += `    <string name="${item.key}">${finalEscaped}</string>\n`;
                    newTranslationsCount++;
                    
                    // Throttle slightly down to avoid ratelimits
                    await new Promise(r => setTimeout(r, 300));
                } catch (e) {
                    console.error(`Failed to translate '${item.key}' to ${lang}:`, e.message);
                    // Fallback to English
                    outputXml += `    <string name="${item.key}">${item.rawValue}</string>\n`;
                }
            }
        }

        outputXml += `</resources>`;
        fs.writeFileSync(targetFile, outputXml, 'utf-8');
        console.log(`✅ Completed ${lang.toUpperCase()}: Added ${newTranslationsCount} new translations.`);
    }

    console.log("🎉 Translation Pipeline finished successfully!");
}

processTranslations();
