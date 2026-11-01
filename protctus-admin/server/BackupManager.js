// ═══════════════════════════════════════════════════════════════
// BackupManager.js — Protctus Locale File Rotation & Cleanup
// ═══════════════════════════════════════════════════════════════
//
// Before new translated JSON files are published:
//   1. Move current /public/locales/ → /backup/locales_{timestamp}/
//   2. Purge old backups, keeping only the last MAX_BACKUPS (3)
//   3. Write new files atomically via temp dir → rename

const fs = require("fs");
const path = require("path");

// ── Configuration ──
const MAX_BACKUPS = 3;

/**
 * Copies a directory tree recursively.
 */
function copyDirSync(src, dest) {
  if (!fs.existsSync(src)) return;
  fs.mkdirSync(dest, { recursive: true });

  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);

    if (entry.isDirectory()) {
      copyDirSync(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

/**
 * Removes a directory recursively (rm -rf).
 */
function removeDirSync(dir) {
  if (!fs.existsSync(dir)) return;
  fs.rmSync(dir, { recursive: true, force: true });
}

/**
 * Lists all backup directories sorted by timestamp (oldest first).
 * Backup dirs are named: locales_{timestamp}
 */
function listBackups(backupRoot) {
  if (!fs.existsSync(backupRoot)) return [];

  return fs
    .readdirSync(backupRoot, { withFileTypes: true })
    .filter((d) => d.isDirectory() && d.name.startsWith("locales_"))
    .map((d) => {
      const ts = parseInt(d.name.replace("locales_", ""), 10);
      return {
        name: d.name,
        path: path.join(backupRoot, d.name),
        timestamp: isNaN(ts) ? 0 : ts,
      };
    })
    .sort((a, b) => a.timestamp - b.timestamp); // oldest first
}

/**
 * Purges old backups, keeping only the last `maxKeep` backups.
 */
function purgeOldBackups(backupRoot, maxKeep = MAX_BACKUPS) {
  const backups = listBackups(backupRoot);

  if (backups.length <= maxKeep) {
    console.log(`  ✓ ${backups.length} backups exist (max: ${maxKeep}) — no purge needed`);
    return;
  }

  const toDelete = backups.slice(0, backups.length - maxKeep);
  for (const old of toDelete) {
    console.log(`  🗑 Purging old backup: ${old.name}`);
    removeDirSync(old.path);
  }

  console.log(`  ✓ Purged ${toDelete.length} old backup(s), kept ${maxKeep}`);
}

/**
 * Full backup-and-rotate cycle:
 *
 * 1. If /public/locales/ exists and has files, copy it to /backup/locales_{now}/
 * 2. Purge backups exceeding MAX_BACKUPS
 * 3. Clear /public/locales/ for new files
 *
 * @param {string} localesDir  Path to the active locales directory (e.g., /public/locales/)
 * @param {string} backupRoot  Path to the backup root directory (e.g., /backup/)
 * @returns {string|null}      Path to the created backup, or null if no backup was needed
 */
function rotateBackup(localesDir, backupRoot) {
  console.log("\n━━━ BACKUP ROTATION ━━━");

  // Check if there are existing files to back up
  if (!fs.existsSync(localesDir)) {
    console.log("  ✓ No existing locales directory — nothing to back up");
    fs.mkdirSync(localesDir, { recursive: true });
    return null;
  }

  const existingFiles = fs.readdirSync(localesDir);
  if (existingFiles.length === 0) {
    console.log("  ✓ Locales directory is empty — nothing to back up");
    return null;
  }

  // Step 1: Create backup
  const timestamp = Date.now();
  const backupName = `locales_${timestamp}`;
  const backupPath = path.join(backupRoot, backupName);

  fs.mkdirSync(backupRoot, { recursive: true });

  console.log(`  📁 Creating backup: ${backupName}`);
  console.log(`     Source: ${localesDir}`);
  console.log(`     Dest:   ${backupPath}`);

  copyDirSync(localesDir, backupPath);
  console.log(`  ✓ Backup created with ${existingFiles.length} files`);

  // Step 2: Purge old backups
  purgeOldBackups(backupRoot, MAX_BACKUPS);

  // Step 3: Clear locales directory for new files
  console.log("  🧹 Clearing locales directory for fresh publish...");
  for (const file of existingFiles) {
    const filePath = path.join(localesDir, file);
    if (fs.statSync(filePath).isFile()) {
      fs.unlinkSync(filePath);
    }
  }
  console.log("  ✓ Locales directory cleared\n");

  return backupPath;
}

/**
 * Prints a summary of all current backups.
 */
function printBackupSummary(backupRoot) {
  const backups = listBackups(backupRoot);

  if (backups.length === 0) {
    console.log("  📭 No backups found");
    return;
  }

  console.log(`  📦 ${backups.length} backup(s) on disk:`);
  for (const b of backups) {
    const date = new Date(b.timestamp).toLocaleString();
    const files = fs.existsSync(b.path)
      ? fs.readdirSync(b.path).length
      : 0;
    console.log(`     • ${b.name} — ${date} (${files} files)`);
  }
}

// ══════════════════════════════════════════════
// EXPORTS
// ══════════════════════════════════════════════

module.exports = {
  rotateBackup,
  purgeOldBackups,
  listBackups,
  printBackupSummary,
  copyDirSync,
  removeDirSync,
  MAX_BACKUPS,
};
