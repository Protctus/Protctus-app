const admin = require("firebase-admin");
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const ADMIN_EMAIL = "jatinsharmashivpuri02122008@gmail.com";
const ADMIN_PASSWORD = "wjegfjer[pytu34-@124";

async function fixAdmin() {
  try {
    // Find the user by email
    const user = await admin.auth().getUserByEmail(ADMIN_EMAIL);
    console.log("Found existing user:", user.uid);

    // Update their password to the correct one
    await admin.auth().updateUser(user.uid, {
      password: ADMIN_PASSWORD,
    });
    console.log("Password updated successfully for UID:", user.uid);
    console.log("\nAdmin UID:", user.uid);
    console.log("Use this UID in your Firestore security rules.");
  } catch (err) {
    if (err.code === "auth/user-not-found") {
      // Create the user
      const newUser = await admin.auth().createUser({
        email: ADMIN_EMAIL,
        password: ADMIN_PASSWORD,
      });
      console.log("Created new admin user:", newUser.uid);
    } else {
      console.error("Error:", err.message);
    }
  }
  process.exit(0);
}

fixAdmin();
