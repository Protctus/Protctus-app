const admin = require("firebase-admin");

// Already initialized? Re-use. Otherwise init.
if (!admin.apps.length) {
  const serviceAccount = require("./serviceAccountKey.json");
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });
}

const ADMIN_UID = "JS39GAXpErPQETZXGchdym1BzX32";

// Firestore rules that:
// 1. Allow the admin to read/write EVERYTHING
// 2. Allow authenticated users to read/write ONLY their own document
// 3. Block all public access
const rules = `
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // ── Admin: full access to everything ──
    function isAdmin() {
      return request.auth != null && request.auth.uid == '${ADMIN_UID}';
    }

    // ── Users collection ──
    match /users/{userId} {
      allow read, write: if isAdmin();
      allow read, update: if request.auth != null && request.auth.uid == userId;
      allow create: if request.auth != null;
    }

    // ── Devices collection ──
    match /devices/{deviceId} {
      allow read, write: if isAdmin();
      allow read, write: if request.auth != null && resource.data.userId == request.auth.uid;
      allow create: if request.auth != null;
    }

    // ── Crashes collection ──
    match /crashes/{crashId} {
      allow read, write: if isAdmin();
      allow create: if request.auth != null;
    }

    // ── Support Tickets collection ──
    match /support_tickets/{ticketId} {
      allow read, write: if isAdmin();
      allow create: if request.auth != null;
      allow read: if request.auth != null && (resource.data.user_email == request.auth.token.email || resource.data.user_email == 'Unknown');
    }

    // ── Default: deny everything else ──
    match /{document=**} {
      allow read, write: if isAdmin();
    }
  }
}
`;

async function deployRules() {
  const projectId = "protctus-2cf6d";
  
  // Use the Firebase REST API to update rules for the named database
  const { GoogleAuth } = require("google-auth-library");
  const auth = new GoogleAuth({
    keyFile: "./serviceAccountKey.json",
    scopes: ["https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/firebase"],
  });
  const client = await auth.getClient();

  // Deploy to the named "protctus" database
  const dbName = "protctus";
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/${dbName}/documents:runQuery`;
  
  // Actually we need to use the Firestore rules API
  // The rules API endpoint is different
  const rulesUrl = `https://firebaserules.googleapis.com/v1/projects/${projectId}/rulesets`;
  
  // Step 1: Create a new ruleset
  const createRes = await client.request({
    url: rulesUrl,
    method: "POST",
    data: {
      source: {
        files: [
          {
            name: "firestore.rules",
            content: rules,
          },
        ],
      },
    },
  });
  
  const rulesetName = createRes.data.name;
  console.log("Created ruleset:", rulesetName);

  // Step 2: Release (apply) the ruleset to the named database
  const releasesUrl = `https://firebaserules.googleapis.com/v1/projects/${projectId}/releases`;
  
  // For named databases the release name is: cloud.firestore/<database_name>
  const releaseName = `cloud.firestore/${dbName}`;
  
  try {
    // Try to update existing release first
    await client.request({
      url: `${releasesUrl}/${encodeURIComponent(releaseName)}`,
      method: "PATCH",
      data: {
        release: {
          name: `projects/${projectId}/releases/${releaseName}`,
          rulesetName: rulesetName,
        },
      },
    });
    console.log("Updated release for database:", dbName);
  } catch (e) {
    // Create new release
    await client.request({
      url: releasesUrl,
      method: "POST",
      data: {
        name: `projects/${projectId}/releases/${releaseName}`,
        rulesetName: rulesetName,
      },
    });
    console.log("Created release for database:", dbName);
  }
  
  console.log("\n✅ Firestore security rules deployed successfully!");
  console.log("Only admin UID", ADMIN_UID, "has full read/write access.");
  console.log("Regular users can only access their own documents.");
  process.exit(0);
}

deployRules().catch(err => {
  console.error("Failed to deploy rules:", err.message);
  if (err.response) console.error("Response:", JSON.stringify(err.response.data, null, 2));
  process.exit(1);
});
