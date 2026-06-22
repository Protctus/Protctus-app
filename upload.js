const admin = require('firebase-admin');

// 1. Apni Private Key aur Quiz Data ko import kar rahe hain
const serviceAccount = require('./serviceAccountKey.json');
const quizData = require('./quiz_data.json');

// 2. Firebase Admin SDK ko initialize karna (God Mode Access)
admin.initializeApp({

    credential: admin.credential.cert(serviceAccount),

    databaseURL: "https://protctus-2cf6d-default-rtdb.asia-southeast1.firebasedatabase.app"

});

const db = admin.firestore();

// 3. Main Asynchronous Upload Function
async function uploadQuizzes() {
    console.log(`\n🚀 Starting upload for ${quizData.length} questions to Firestore...\n`);

    let successCount = 0;
    let failCount = 0;

    // Har question ko ek-ek karke database me push karenge
    for (const question of quizData) {
        try {
            // Collection ka naam 'security_quiz' hai. 
            // Document ka ID hum wahi rakh rahe hain jo JSON me 'id' hai (e.g., 'q1')
            await db.collection('security_quiz').doc(question.id).set(question);
            console.log(`✅ Successfully Uploaded: ${question.id}`);
            successCount++;
        } catch (error) {
            console.error(`❌ Error uploading ${question.id}:`, error.message);
            failCount++;
        }
    }

    // 4. Final Status Report
    console.log(`\n================================`);
    console.log(`🏁 UPLOAD PROCESS COMPLETE`);
    console.log(`================================`);
    console.log(`Total Questions: ${quizData.length}`);
    console.log(`Successfully Added: ${successCount}`);
    console.log(`Failed: ${failCount}`);
    console.log(`================================\n`);
}

// Function ko trigger karna
uploadQuizzes();