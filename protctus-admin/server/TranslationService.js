// ═══════════════════════════════════════════════════════════════
// TranslationService.js — Protctus Placeholder-Protected Translator
// ═══════════════════════════════════════════════════════════════
//
// Problem: Google Translate destroys security terms like "Phishing",
//          "OSINT", "TFLite" by translating them into bizarre local
//          dictionary words.
//
// Solution: Before translation, replace every glossary term with a
//           unique placeholder like {{0}}, {{1}}. Translate the
//           masked string. Then swap placeholders back.
//
// Rate Limiting: 2000ms sequential delay between EACH request
//                to avoid Google IP bans.

const { CYBER_GLOSSARY } = require("./CyberGlossary");

// ── Configuration ──
const RATE_LIMIT_MS = 2000;       // Delay between each translate() call
const MAX_RETRIES = 3;            // Retry count on transient failures
const INITIAL_BACKOFF_MS = 3000;  // First retry backoff (doubles each retry)

// ── Lazy-loaded translate function (ESM module) ──
let _translateFn = null;

async function getTranslateFn() {
  if (_translateFn) return _translateFn;
  try {
    const mod = await import("@vitalets/google-translate-api");
    _translateFn = mod.translate || mod.default?.translate || mod.default;
    if (!_translateFn) throw new Error("Could not resolve translate function from module");
    return _translateFn;
  } catch (e) {
    console.error("╔══════════════════════════════════════════════╗");
    console.error("║  FATAL: @vitalets/google-translate-api       ║");
    console.error("║  not installed. Run:                         ║");
    console.error("║  npm install @vitalets/google-translate-api  ║");
    console.error("╚══════════════════════════════════════════════╝");
    process.exit(1);
  }
}

// ══════════════════════════════════════════════
// MASKING ENGINE
// ══════════════════════════════════════════════

/**
 * Replaces all glossary terms in `text` with {{0}}, {{1}}, etc.
 * Returns the masked string and the ordered list of original tokens.
 *
 * Strategy:
 *  - Glossary is pre-sorted longest-first (in CyberGlossary.js)
 *  - Each term is matched case-insensitively with word boundaries
 *  - Already-masked regions are skipped to prevent double-masking
 */
function maskString(text) {
  let masked = text;
  const tokens = [];

  for (const term of CYBER_GLOSSARY) {
    // Build a regex that matches the term with word boundaries
    // Escape special regex chars in the term
    const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const regex = new RegExp(`\\b${escaped}\\b`, "gi");

    // Find all matches and replace them one-by-one
    let match;
    while ((match = regex.exec(masked)) !== null) {
      // Check if this position is inside an existing placeholder
      const before = masked.substring(0, match.index);
      const openCount = (before.match(/\{\{/g) || []).length;
      const closeCount = (before.match(/\}\}/g) || []).length;
      if (openCount > closeCount) continue; // inside a placeholder, skip

      const idx = tokens.length;
      tokens.push(match[0]); // Preserve original casing
      masked =
        masked.substring(0, match.index) +
        `{{${idx}}}` +
        masked.substring(match.index + match[0].length);

      // Reset regex lastIndex since string length changed
      regex.lastIndex = match.index + `{{${idx}}}`.length;
    }
  }

  return { masked, tokens };
}

/**
 * Restores original terms from {{N}} placeholders in translated text.
 *
 * Google Translate sometimes:
 *  - Adds spaces: {{ 0 }}, { {0} }
 *  - Removes braces: {0}
 *  - Adds punctuation after: {{0}},
 *
 * We handle all variants with a flexible regex.
 */
function unmaskString(translated, tokens) {
  let result = translated;

  for (let i = 0; i < tokens.length; i++) {
    // Match {{i}}, {{ i }}, { { i } }, {i}, and variants with optional spaces
    const pattern = new RegExp(
      `\\{\\s*\\{\\s*${i}\\s*\\}\\s*\\}|\\{\\s*${i}\\s*\\}`,
      "g"
    );
    result = result.replace(pattern, tokens[i]);
  }

  return result;
}

// ══════════════════════════════════════════════
// RATE-LIMITED TRANSLATION
// ══════════════════════════════════════════════

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Translates a single string with placeholder protection.
 *
 * @param {string} text       The English source string.
 * @param {string} targetLang ISO 639-1 language code (hi, kn, bn, ta, te, or).
 * @returns {Promise<string>} Translated string with glossary terms preserved.
 */
async function translateString(text, targetLang) {
  if (!text || text.trim().length === 0) return text;

  const translate = await getTranslateFn();

  // Step 1: Mask all glossary terms
  const { masked, tokens } = maskString(text);

  // Step 2: Translate with retry + exponential backoff
  let translated = masked;
  let lastError = null;

  for (let attempt = 0; attempt < MAX_RETRIES; attempt++) {
    try {
      const result = await translate(masked, { to: targetLang });
      translated = result.text;
      lastError = null;
      break;
    } catch (err) {
      lastError = err;
      const backoff = INITIAL_BACKOFF_MS * Math.pow(2, attempt);
      console.warn(
        `   ⚠ Translate attempt ${attempt + 1}/${MAX_RETRIES} failed: ${err.message}`
      );
      console.warn(`     Backing off for ${backoff}ms...`);
      await delay(backoff);
    }
  }

  if (lastError) {
    console.error(`   ✗ FAILED after ${MAX_RETRIES} retries: ${lastError.message}`);
    console.error(`     Returning original English text as fallback.`);
    return text; // Fallback to English
  }

  // Step 3: Unmask — restore glossary terms
  const final = unmaskString(translated, tokens);

  return final;
}

/**
 * Translates an array of strings sequentially with 2000ms delay
 * between each Google Translate API call.
 *
 * @param {string[]} strings   Array of English strings to translate.
 * @param {string}   targetLang ISO 639-1 code.
 * @param {function} onProgress Optional callback: (index, total, originalText) => void
 * @returns {Promise<string[]>} Array of translated strings.
 */
async function translateBatch(strings, targetLang, onProgress = null) {
  const results = [];
  const total = strings.length;

  for (let i = 0; i < total; i++) {
    const original = strings[i];

    if (onProgress) onProgress(i, total, original);

    const translated = await translateString(original, targetLang);
    results.push(translated);

    // Rate limit: wait 2000ms before next request
    // Skip delay after the last item
    if (i < total - 1) {
      await delay(RATE_LIMIT_MS);
    }
  }

  return results;
}

/**
 * Translates a structured object's string values. Non-string values
 * are passed through unchanged. Useful for translating quest/notification
 * objects in bulk.
 *
 * @param {Object}   obj        The object whose string values to translate.
 * @param {string[]} fields     Array of field names to translate (e.g., ["title", "description"]).
 * @param {string}   targetLang ISO 639-1 code.
 * @returns {Promise<Object>}   A new object with translated fields.
 */
async function translateObject(obj, fields, targetLang) {
  const translated = { ...obj };

  for (const field of fields) {
    if (typeof translated[field] === "string" && translated[field].trim()) {
      translated[field] = await translateString(translated[field], targetLang);
      await delay(RATE_LIMIT_MS);
    }
  }

  return translated;
}

/**
 * Translates an array of objects sequentially.
 *
 * @param {Object[]} items     Array of objects to translate.
 * @param {string[]} fields    Fields within each object to translate.
 * @param {string}   targetLang ISO 639-1 code.
 * @param {function} onProgress Optional: (index, total, item) => void
 * @returns {Promise<Object[]>}
 */
async function translateObjectBatch(items, fields, targetLang, onProgress = null) {
  const results = [];

  for (let i = 0; i < items.length; i++) {
    if (onProgress) onProgress(i, items.length, items[i]);
    const translated = await translateObject(items[i], fields, targetLang);
    results.push(translated);
  }

  return results;
}

// ══════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════

module.exports = {
  maskString,
  unmaskString,
  translateString,
  translateBatch,
  translateObject,
  translateObjectBatch,
  RATE_LIMIT_MS,
};
