#!/usr/bin/env node
// ═══════════════════════════════════════════════════════════════
// publish.js — Protctus Daily Sync Pipeline Orchestrator
// ═══════════════════════════════════════════════════════════════
//
// Usage:
//   node publish.js              # Full translate + publish
//   node publish.js --dry-run    # Fetch data, skip translation, test pipeline
//   node publish.js --cron       # Enable automatic daily scheduling (00:00 UTC)
//
// What it does:
//   1. Fetches Quests, Lab, Notifications from APIs
//   2. Translates all content to 6 Indian languages
//   3. Backs up existing locale files (keeps last 3)
//   4. Writes minified JSON + manifest.json to /public/locales/

const path = require("path");
const fs = require("fs");

const { rotateBackup, printBackupSummary } = require("./BackupManager");
const { generateAll } = require("./ManifestGenerator");

// ── Paths ──
const ROOT = path.resolve(__dirname, "..");
const LOCALES_DIR = path.join(ROOT, "public", "locales");
const BACKUP_DIR = path.join(ROOT, "backup");
const SERVICE_ACCOUNT_PATH = path.resolve(ROOT, "..", "serviceAccountKey.json");

// ── API Configuration ──
const API_BASE = "https://protctus-quests-api.jatinsharmashivpuri02122008.workers.dev";

// ── CLI Flags ──
const args = process.argv.slice(2);
const DRY_RUN = args.includes("--dry-run");
const CRON_MODE = args.includes("--cron");

// ══════════════════════════════════════════════
// FIREBASE INITIALIZATION
// ══════════════════════════════════════════════

let firestore = null;

function initFirebase() {
  try {
    if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
      console.warn("  ⚠ serviceAccountKey.json not found — Firestore notifications will be skipped");
      return null;
    }

    const admin = require("firebase-admin");
    const serviceAccount = require(SERVICE_ACCOUNT_PATH);

    if (!admin.apps.length) {
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount),
      });
    }

    // Use the named database "protctus"
    const db = admin.firestore();
    db.settings({ databaseId: "protctus" });

    console.log("  ✓ Firebase Admin initialized (Firestore: protctus)");
    return db;
  } catch (e) {
    console.warn(`  ⚠ Firebase init failed: ${e.message}`);
    console.warn("    Notifications will be skipped.");
    return null;
  }
}

// ══════════════════════════════════════════════
// MAIN PIPELINE
// ══════════════════════════════════════════════

async function runPublishPipeline() {
  const startTime = Date.now();

  console.log("\n");
  console.log("╔══════════════════════════════════════════════════════════╗");
  console.log("║         PROTCTUS — DAILY SYNC PUBLISH PIPELINE         ║");
  console.log("║                                                        ║");
  console.log(`║  Mode: ${DRY_RUN ? "DRY RUN (no translation)" : "FULL TRANSLATION"}                          ║`);
  console.log(`║  Time: ${new Date().toISOString()}              ║`);
  console.log("╚══════════════════════════════════════════════════════════╝\n");

  // ── Step 1: Initialize Firebase ──
  console.log("━━━ INITIALIZATION ━━━");
  firestore = initFirebase();
  console.log(`  ✓ API Base: ${API_BASE}`);
  console.log(`  ✓ Output:   ${LOCALES_DIR}`);
  console.log(`  ✓ Backup:   ${BACKUP_DIR}\n`);

  // ── Step 2: Backup existing files ──
  rotateBackup(LOCALES_DIR, BACKUP_DIR);

  // ── Step 3: Generate all translated files ──
  const manifest = await generateAll({
    apiBase: API_BASE,
    firestore: firestore,
    outputDir: LOCALES_DIR,
    dryRun: DRY_RUN,
  });

  // ── Step 4: Print summary ──
  const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);

  console.log("\n╔══════════════════════════════════════════════════════════╗");
  console.log("║                  PUBLISH COMPLETE ✅                    ║");
  console.log("╚══════════════════════════════════════════════════════════╝");
  console.log(`  📋 Version Code: ${manifest.version_code}`);
  console.log(`  📅 Generated At: ${manifest.generated_at}`);
  console.log(`  🌐 Languages:    ${manifest.languages.join(", ")}`);
  console.log(`  ⏱  Elapsed:      ${elapsed}s`);
  console.log(`  📂 Output Dir:   ${LOCALES_DIR}`);

  // List generated files with sizes
  console.log("\n  📄 Generated Files:");
  if (fs.existsSync(LOCALES_DIR)) {
    const files = fs.readdirSync(LOCALES_DIR);
    for (const file of files) {
      const filePath = path.join(LOCALES_DIR, file);
      const stats = fs.statSync(filePath);
      const sizeKB = (stats.size / 1024).toFixed(1);
      console.log(`     • ${file} (${sizeKB} KB)`);
    }
  }

  // Backup summary
  console.log("\n  💾 Backup Status:");
  printBackupSummary(BACKUP_DIR);

  console.log("\n  🚀 The Android app can now sync from manifest.json\n");
}

// ══════════════════════════════════════════════
// CRON SCHEDULER (Optional)
// ══════════════════════════════════════════════

if (CRON_MODE) {
  let cron;
  try {
    cron = require("node-cron");
  } catch (e) {
    console.error("node-cron not installed. Run: npm install node-cron");
    console.error("Or run without --cron flag for manual publish.");
    process.exit(1);
  }

  console.log("⏰ CRON MODE ENABLED — Scheduling daily publish at 00:00 UTC");
  console.log("   Press Ctrl+C to stop.\n");

  // Run at midnight UTC every day
  cron.schedule("0 0 * * *", async () => {
    console.log(`\n[${ new Date().toISOString()}] Cron triggered — starting publish...`);
    try {
      await runPublishPipeline();
    } catch (e) {
      console.error(`\n❌ CRON PUBLISH FAILED: ${e.message}`);
      console.error(e.stack);
    }
  }, {
    timezone: "UTC",
  });

  // Also run once immediately on startup
  runPublishPipeline().catch((e) => {
    console.error(`\n❌ INITIAL PUBLISH FAILED: ${e.message}`);
    console.error(e.stack);
  });

} else {
  // ── Single manual run ──
  runPublishPipeline()
    .then(() => {
      process.exit(0);
    })
    .catch((e) => {
      console.error(`\n❌ PUBLISH FAILED: ${e.message}`);
      console.error(e.stack);
      process.exit(1);
    });
}
