package com.protctus.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.protctus.app.ui.theme.ProtctusTheme

@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    val interactionSource = remember { MutableInteractionSource() }

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(com.protctus.app.ui.theme.DarkBackground)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { navController.popBackStack() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFFFFFFF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.privacy_policy),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Last Updated: April 2, 2026\nEffective Date: April 2, 2026",
                    fontSize = 14.sp,
                    color = Color(0xFF7F909F),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                PolicySection(title = "1. INTRODUCTION AND SCOPE")
                PolicyText("Welcome to Protctus. This comprehensive Privacy Policy defines the exact parameters under which Protctus (\"we\", \"us\", \"our\") collects, utilizes, safeguards, and processes user data. This policy is explicitly formulated to comply with global privacy standards, including the General Data Protection Regulation (GDPR), the California Consumer Privacy Act (CCPA), and the stringent Data Safety and User Data policies mandated by the Google Play Developer Program.")
                PolicyText("By downloading, accessing, or utilizing the Application, you explicitly consent to the practices outlined within this framework. If you find any terms unacceptable, your sole recourse is to uninstall the Application.")

                PolicySection(title = "2. PROMINENT DISCLOSURE & VPN SERVICE POLICY")
                PolicySubSection(title = "2.1 VpnService Utilization:", content = "Protctus incorporates a robust network protection feature designated as \"Phishing Shield.\" To effectively intercept and analyze malicious web traffic in real-time without compromising user bandwidth, this feature leverages the Android VpnService API.")
                PolicySubSection(title = "2.2 No Remote Traffic Routing:", content = "Unlike traditional Virtual Private Networks (VPNs) designed for masking geographical location or encrypting public Wi-Fi traffic, Protctus does NOT route your internet traffic through external, remote, or third-party servers. The VpnService establishes a strictly local mechanism on your Android device.")
                PolicySubSection(title = "2.3 Strict No-Log & No-Collection Affirmation:", content = "We state unequivocally that Protctus DOES NOT record, monitor, log, capture, or transmit the contents of your internet traffic. Specifically, we do not collect your browsing history, DNS resolutions, data payloads, IP addresses, search queries, or private communications. All URL inspections against known threat databases occur instantaneously and ephemerally.")
                PolicySubSection(title = "2.4 Prohibition on Monetization:", content = "We absolutely forbid and do not engage in the monetization of user network data. Your traffic is never shared with, sold to, or analyzed by third-party advertising networks, analytics firms, or data brokers.")

                PolicySection(title = "3. DATA COLLECTION TYPOLOGY")
                PolicyText("Protctus is engineered on a \"Local-First\" processing paradigm, drastically minimizing external data transmission.")
                PolicySubSection(title = "3.1 Ephemeral On-Device Processing (No Cloud Storage):", content = "Features such as 'Smart Scan' and 'Deep Scan' require access to your device's file system and installed application manifests. This analysis is executed entirely on the local processor. We do not extract, backup, or copy your personal files, photographs, or proprietary documents to external repositories.")
                PolicySubSection(title = "3.2 Personally Identifiable Information (PII):", content = "Registration is strictly optional. If you elect to establish an account, we collect minimal PII: a valid email address and a cryptographically hashed password. This data is utilized solely for authentication, account recovery, and subscription validation.")
                PolicySubSection(title = "3.3 Cloud-Assisted Threat Intelligence:", content = "When utilizing the 'Dark Web Scan', the email address provided is transmitted via secure, encrypted channels to accredited third-party threat intelligence APIs (e.g., HaveIBeenPwned). This query is processed dynamically to ascertain breach exposure and is not persistently logged by our active databases.")
                PolicySubSection(title = "3.4 Automated Diagnostic Telemetry:", content = "Subject to your explicit opt-in consent, we may utilize Firebase Crashlytics to harvest anonymous diagnostic telemetry. This includes stack traces, device hardware models, operating system variants, and application version nomenclature. This data is structurally devoid of PII and is used exclusively for structural stabilization and bug remediation.")

                PolicySection(title = "4. REQUIRED DEVICE PERMISSIONS")
                PolicyText("The Application explicitly requests the following systemic permissions to perform its core cybersecurity functions. We do not stealthily invoke permissions. You retain full autonomy to revoke these via the Android Operating System Settings:")
                BulletPoint("MANAGE_EXTERNAL_STORAGE / READ_EXTERNAL_STORAGE: Strictly utilized by the 'Deep Scan' engine to parse file structures for recognized malicious payloads.")
                BulletPoint("QUERY_ALL_PACKAGES: Essential for the 'Smart Scan' heuristic engine to generate a localized manifest of installed applications to evaluate authorization risks and known malware signatures.")
                BulletPoint("POST_NOTIFICATIONS: Necessary to dispatch critical security alerts, scan completion reports, and localized threat warnings.")
                BulletPoint("BIND_VPN_SERVICE: Required exclusively for the localized operationalization of the 'Phishing Shield' feature as detailed in Section 2.")
                
                PolicySection(title = "5. THIRD-PARTY DATA PROCESSORS AND SUB-PROCESSORS")
                PolicyText("We vehemently oppose the unauthorized commoditization of user data. We do not execute data sales, rentals, or undocumented trades. We engage exclusively with essential infrastructure providers bound by rigorous Data Processing Agreements (DPAs):")
                BulletPoint("Google Play Services: Facilitates secure cryptographic validation of in-app purchases and subscription lifecycles.")
                BulletPoint("Google Firebase Platform: Provides secure authentication frameworks, push notification orchestration, and crash analytics.")

                PolicySection(title = "6. DATA RETENTION LIFECYCLE AND DELETION PROTOCOLS")
                PolicyText("We adhere to the principle of data minimization.")
                PolicySubSection(title = "6.1 Retention Limit:", content = "Account credentials (PII) are retained exclusively for the duration of an active account lifecycle.")
                PolicySubSection(title = "6.2 Right to Erasure / Deletion Process:", content = "You maintain sovereignty over your data. A streamlined, automated account deletion mechanism is embedded natively within the Application (Navigate to Settings -> Delete Account). Furthermore, deletion requests may be initiated formally via our support channels.")
                PolicySubSection(title = "6.3 Cryptographic Destruction:", content = "Upon execution of a deletion request, your primary PII is immediately rendered inaccessible. A systemic purge of backup archives is completed within a mandatory 30-day window, ensuring irrecoverable destruction.")

                PolicySection(title = "7. CHILDREN'S PRIVACY (COPPA COMPLIANCE)")
                PolicyText("Protctus asserts that the Application constitutes an advanced cybersecurity utility explicitly designed for mature audiences. We do not consciously solicit, collect, or process PII from individuals under the digital age of consent within their respective jurisdictions (predominantly individuals under 13 years of age, compliant with the Children's Online Privacy Protection Act). Should we ascertain that such data has been inadvertently captured, drastic summarial deletion protocols will be executed.")

                PolicySection(title = "8. MODIFICATIONS TO THIS REGULATORY FRAMEWORK")
                PolicyText("The digital threat landscape is volatile, necessitating periodic revisions to this framework. We reserve the unassailable right to unilaterally modify this Privacy Policy. Substantive alterations will be communicated via in-app notifications or explicit email correspondence. Continued utilization of the Application subsequent to such modifications constitutes binding legal acceptance of the revised terms.")

                PolicySection(title = "9. DIRECT COMMUNICATION AND DATA PROTECTION OFFICER")
                PolicyText("For formal inquiries regarding this document, the invocation of statutory privacy rights, or the initiation of a formalized Subject Access Request (SAR), please direct all correspondence to our dedicated privacy liaison:")
                BulletPoint("Entity: Protctus Security Operations")
                BulletPoint("Email Protocol: privacy@protctus.com")

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun PolicySection(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PolicySubSection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (content.isNotEmpty()) {
            Text(
                text = content,
                fontSize = 14.sp,
                color = Color(0xFFE2E2E2),
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun PolicyText(content: String) {
    Text(
        text = content,
        fontSize = 14.sp,
        color = Color(0xFFE2E2E2),
        lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun BulletPoint(content: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)) {
        Text(
            text = stringResource(R.string.str_18618),
            fontSize = 14.sp,
            color = Color(0xFFE2E2E2),
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = content,
            fontSize = 14.sp,
            color = Color(0xFFE2E2E2),
            lineHeight = 22.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPrivacyPolicyScreen() {
    ProtctusTheme {
        PrivacyPolicyScreen(navController = rememberNavController())
    }
}
