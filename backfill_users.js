/**
 * Backfill: For every user in Firebase Auth, write their email
 * into the Firestore "users" doc (merge, won't overwrite other fields).
 */
const admin = require("firebase-admin");

if (!admin.apps.length) {
  const serviceAccount = require("./serviceAccountKey.json");
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
}
const { getFirestore } = require("firebase-admin/firestore");
const db = getFirestore(admin.app(), "protctus"); // named database

async function backfill() {
  let nextPageToken;
  let count = 0;

  do {
    const listResult = await admin.auth().listUsers(100, nextPageToken);

    for (const userRecord of listResult.users) {
      const uid = userRecord.uid;
      const email = userRecord.email || "";
      const createdAt = new Date(userRecord.metadata.creationTime).getTime();

      await db.collection("users").doc(uid).set(
        {
          email,
          createdAt,
          status: "active",
        },
        { merge: true }
      );
      count++;
      console.log(`  Synced: ${uid} → ${email || "(no email)"}`);
    }

    nextPageToken = listResult.pageToken;
  } while (nextPageToken);

  console.log(`\n✅ Backfilled ${count} user(s) with email & createdAt.`);
  process.exit(0);
}

backfill().catch((e) => {
  console.error("Error:", e.message);
  process.exit(1);
});
