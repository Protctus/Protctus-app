// ═══════════════════════════════════════════════════════════════
// ManifestGenerator.js — Protctus Daily JSON Compilation Engine
// ═══════════════════════════════════════════════════════════════
//
// Pulls all translatable content from:
//   1. Quests          → Cloudflare D1 (via public API)
//   2. Lab Chapters    → Cloudflare D1 (via public API)
//   3. Notifications   → Firebase Firestore (via firebase-admin)
//
// For each target language, generates:
//   - quest_{lang}.json
//   - lab_{lang}.json
//   - notif_{lang}.json
//
// Then generates a manifest.json with:
//   - version_code (timestamp)
//   - generated_at (ISO string)
//   - languages array
//   - files map

const fs = require("fs");
const path = require("path");
const { translateObjectBatch } = require("./TranslationService");

// ── Configuration ──
const TARGET_LANGUAGES = ["hi", "kn", "bn", "ta", "te", "or"];

const LANGUAGE_NAMES = {
  hi: "Hindi",
  kn: "Kannada",
  bn: "Bengali",
  ta: "Tamil",
  te: "Telugu",
  or: "Odia",
};

// ══════════════════════════════════════════════
// DATA FETCHERS
// ══════════════════════════════════════════════

/**
 * Fetches all active quests from the Cloudflare D1 API.
 */
async function fetchQuests(apiBase) {
  console.log("  📦 Fetching Quests from D1...");
  const res = await fetch(`${apiBase}/quests?limit=50`);
  if (!res.ok) throw new Error(`Quest fetch failed: HTTP ${res.status}`);
  const quests = await res.json();
  console.log(`     ✓ Fetched ${quests.length} quests`);
  return quests;
}

/**
 * Fetches all Lab chapters with nested topics from D1.
 */
async function fetchLabData(apiBase) {
  console.log("  📦 Fetching Lab Chapters + Topics from D1...");
  const res = await fetch(`${apiBase}/lab/chapters`);
  if (!res.ok) throw new Error(`Lab fetch failed: HTTP ${res.status}`);
  const chapters = await res.json();

  let topicCount = 0;
  for (const ch of chapters) {
    topicCount += (ch.topics || []).length;
  }
  console.log(`     ✓ Fetched ${chapters.length} chapters, ${topicCount} topics`);
  return chapters;
}

/**
 * Fetches all notifications from Firestore via firebase-admin.
 * Falls back gracefully if firebase-admin is not configured.
 */
async function fetchNotifications(firestore) {
  if (!firestore) {
    console.log("  ⚠ Firestore not configured — skipping notifications");
    return [];
  }

  console.log("  📦 Fetching Notifications from Firestore...");
  try {
    const snap = await firestore.collection("notifications").get();
    const notifs = snap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    console.log(`     ✓ Fetched ${notifs.length} notifications`);
    return notifs;
  } catch (e) {
    console.error(`     ✗ Firestore error: ${e.message}`);
    return [];
  }
}

// ══════════════════════════════════════════════
// TRANSLATION PIPELINE
// ══════════════════════════════════════════════

/**
 * Translates quests for a single language.
 * Translatable fields: title, description
 */
async function translateQuests(quests, lang) {
  console.log(`     🔄 Translating ${quests.length} quests → ${LANGUAGE_NAMES[lang]}...`);
  return await translateObjectBatch(
    quests,
    ["title", "description"],
    lang,
    (i, total) => {
      process.stdout.write(`\r       [${i + 1}/${total}] quests translated`);
    }
  );
}

/**
 * Translates lab chapters and their nested topics.
 * Chapter: title
 * Topic: title, content
 */
async function translateLabData(chapters, lang) {
  const totalTopics = chapters.reduce((sum, ch) => sum + (ch.topics || []).length, 0);
  console.log(`     🔄 Translating ${chapters.length} chapters + ${totalTopics} topics → ${LANGUAGE_NAMES[lang]}...`);

  const translatedChapters = [];
  let topicsDone = 0;

  for (const chapter of chapters) {
    // Translate chapter title
    const translatedChapter = await translateObjectBatch(
      [chapter],
      ["title"],
      lang
    );

    const ch = translatedChapter[0];

    // Translate nested topics
    if (ch.topics && ch.topics.length > 0) {
      ch.topics = await translateObjectBatch(
        ch.topics,
        ["title", "content"],
        lang,
        (i, total) => {
          topicsDone++;
          process.stdout.write(`\r       [${topicsDone}/${totalTopics}] topics translated`);
        }
      );
    }

    translatedChapters.push(ch);
  }

  console.log(""); // newline after progress
  return translatedChapters;
}

/**
 * Translates notifications for a single language.
 * Translatable fields: title, message
 */
async function translateNotifications(notifs, lang) {
  if (notifs.length === 0) return [];
  console.log(`     🔄 Translating ${notifs.length} notifications → ${LANGUAGE_NAMES[lang]}...`);
  return await translateObjectBatch(
    notifs,
    ["title", "message"],
    lang,
    (i, total) => {
      process.stdout.write(`\r       [${i + 1}/${total}] notifications translated`);
    }
  );
}

// ══════════════════════════════════════════════
// JSON GENERATION
// ══════════════════════════════════════════════

/**
 * Generates all translated JSON files and the manifest.
 *
 * @param {Object}   options
 * @param {string}   options.apiBase      Cloudflare Worker API base URL
 * @param {Object}   options.firestore    Firebase Firestore instance (or null)
 * @param {string}   options.outputDir    Directory to write JSON files to
 * @param {string[]} options.languages    Array of target language codes
 * @param {boolean}  options.dryRun       If true, skip actual translation (for testing)
 * @returns {Object} The generated manifest object
 */
async function generateAll(options) {
  const {
    apiBase,
    firestore = null,
    outputDir,
    languages = TARGET_LANGUAGES,
    dryRun = false,
  } = options;

  console.log("\n╔══════════════════════════════════════════════╗");
  console.log("║   PROTCTUS DAILY SYNC — MANIFEST GENERATOR  ║");
  console.log("╚══════════════════════════════════════════════╝\n");

  // ── Step 1: Fetch all source data ──
  console.log("━━━ PHASE 1: Data Acquisition ━━━");
  const quests = await fetchQuests(apiBase);
  const labData = await fetchLabData(apiBase);
  const notifications = await fetchNotifications(firestore);

  // ── Step 2: Ensure output directory exists ──
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  // ── Step 3: Translate + write for each language ──
  const filesMap = {};
  const versionCode = Date.now();

  // Also write the English source as base reference
  const enQuestFile = "quest_en.json";
  const enLabFile = "lab_en.json";
  const enNotifFile = "notif_en.json";

  fs.writeFileSync(
    path.join(outputDir, enQuestFile),
    JSON.stringify(quests) // Minified
  );
  fs.writeFileSync(
    path.join(outputDir, enLabFile),
    JSON.stringify(labData)
  );
  fs.writeFileSync(
    path.join(outputDir, enNotifFile),
    JSON.stringify(notifications)
  );

  filesMap["en"] = {
    quests: enQuestFile,
    lab: enLabFile,
    notifications: enNotifFile,
  };

  console.log(`\n  ✓ English base files written to ${outputDir}\n`);

  for (const lang of languages) {
    console.log(`\n━━━ PHASE 2: Translation [${lang.toUpperCase()} — ${LANGUAGE_NAMES[lang]}] ━━━`);

    let translatedQuests, translatedLab, translatedNotifs;

    if (dryRun) {
      console.log("     🏃 DRY RUN — skipping actual translation");
      translatedQuests = quests;
      translatedLab = labData;
      translatedNotifs = notifications;
    } else {
      translatedQuests = await translateQuests(quests, lang);
      console.log(""); // newline after progress
      translatedLab = await translateLabData(labData, lang);
      translatedNotifs = await translateNotifications(notifications, lang);
      console.log(""); // newline after progress
    }

    // ── Write minified JSON ──
    const questFile = `quest_${lang}.json`;
    const labFile = `lab_${lang}.json`;
    const notifFile = `notif_${lang}.json`;

    fs.writeFileSync(
      path.join(outputDir, questFile),
      JSON.stringify(translatedQuests)
    );
    fs.writeFileSync(
      path.join(outputDir, labFile),
      JSON.stringify(translatedLab)
    );
    fs.writeFileSync(
      path.join(outputDir, notifFile),
      JSON.stringify(translatedNotifs)
    );

    filesMap[lang] = {
      quests: questFile,
      lab: labFile,
      notifications: notifFile,
    };

    console.log(`  ✅ ${LANGUAGE_NAMES[lang]} — all files written`);
  }

  // ── Step 4: Generate manifest.json ──
  const manifest = {
    version_code: versionCode,
    generated_at: new Date(versionCode).toISOString(),
    languages: ["en", ...languages],
    files: filesMap,
  };

  fs.writeFileSync(
    path.join(outputDir, "manifest.json"),
    JSON.stringify(manifest) // Minified
  );

  console.log("\n━━━ PHASE 3: Manifest ━━━");
  console.log(`  ✓ manifest.json written`);
  console.log(`  ✓ version_code: ${versionCode}`);
  console.log(`  ✓ Total files: ${Object.keys(filesMap).length * 3 + 1}`);

  return manifest;
}

// ══════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════

module.exports = {
  fetchQuests,
  fetchLabData,
  fetchNotifications,
  translateQuests,
  translateLabData,
  translateNotifications,
  generateAll,
  TARGET_LANGUAGES,
  LANGUAGE_NAMES,
};
