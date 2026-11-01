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
fun TermsOfServiceScreen(navController: NavController) {
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
                    text = stringResource(R.string.terms_of_service_eula),
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

                PolicySection(title = "1. BINDING AGREEMENT AND ACCEPTANCE OF TERMS")
                PolicyText("This Terms of Service Agreement (the \"Agreement\") constitutes a legally binding contract between you (the \"User\", \"You\") and Protctus (the \"Company\", \"We\", \"Us\"). By deliberately downloading, installing, authorizing, or interacting with the Protctus mobile cybersecurity application (the \"Application\"), you unequivocally affirm that you have read, comprehensively understood, and agree to be bound by all stipulations enclosed within this Agreement.")

                PolicySection(title = "2. NATURE OF THE SERVICE; SUPPLEMENTAL EXCLUSION")
                PolicySubSection(title = "2.1 Security Assistance Utility:", content = "Protctus functions as a localized supplemental cybersecurity utility. It deploys heuristic scanners, file analysis, and network interception capabilities to assist users in identifying probabilistic threats. Protctus is NOT an enterprise-grade endpoint detection and response (EDR) platform.")
                PolicySubSection(title = "2.2 Explicit Disavowal of Guaranteed Protection:", content = "The cybersecurity arena is characterized by zero-day exploits and polymorphic threats. You explicitly acknowledge that no algorithmic software can mathematically guarantee absolute protection against all malware permutations, phishing vectors, or targeted intrusion attempts. Protctus is provided as a mitigative strategy, not an impenetrable shield. You remain legally and functionally responsible for the integrity of your personal device and the data hosted within.")

                PolicySection(title = "3. VPN SERVICE (\"PHISHING SHIELD\") COVENANTS")
                PolicyText("The utilization of the 'Phishing Shield' capability necessitates the activation of the Android VpnService. By enabling this granular feature, you bind yourself to the following covenants:")
                BulletPoint("Local Traffic Routing: The VPN tunnel operates exclusively within the physical boundaries of your local device architecture to audit unencrypted DNS calls against recognized hazardous matrices.")
                BulletPoint("Prohibited Interception: You are strictly forbidden from installing or executing this Application on any device wherein you lack explicit, documented legal authorization to intercept network traffic. Violating this clause constitutes a breach of wiretapping statutes.")
                BulletPoint("Zero-Throttling Affirmation: The Company does not impose artificial traffic shaping, bandwidth throttling, or remote routing manipulations.")

                PolicySection(title = "4. STRICT PERMITTED USAGE AND EXPLICIT PROHIBITIONS")
                PolicyText("Your operational license is expressly contingent upon strict adherence to the following behavioral parameters:")
                BulletPoint("No Reverse Engineering: You are legally barred from decompiling, disassembling, reverse-engineering, or attempting any cryptographic extraction of the underlying source code, algorithmic threat databases, or proprietary security definitions.")
                BulletPoint("No Malicious Deployment: The Application shall not be weaponized or utilized to mask, facilitate, or orchestrate distributed denial of service (DDoS) attacks, malware propagation, or advanced persistent threats (APTs).")
                BulletPoint("No Subsidy Bypassing: You are prohibited from spoofing the Google Play Billing Library, applying fraudulent cryptographic signatures, or otherwise subverting the monetization frameworks of the Application.")
                BulletPoint("No Derivative Exploitation: You may not duplicate, clone, rebrand, or syndicate the Application's UI vectors or core business logic to produce a competitive derivative work.")

                PolicySection(title = "5. INTELLECTUAL PROPERTY SOVEREIGNTY")
                PolicyText("Protctus forcefully asserts and retains absolute sovereign rights, title, and interest across all global jurisdictions over the Application. This encompasses all compiled binaries, source architectures, trademarked logos, UI schematics, proprietary threat intelligence matrices, and heuristic algorithms. Nothing in this Agreement transfers any intellectual property asset to You.")

                PolicySection(title = "6. SUBSCRIPTION MECHANICS AND PLAY STORE BILLING")
                PolicyText("Advanced threat intelligence capabilities within the Application command a premium subscription, strictly arbitrated via the Google Play Billing ecosystem.")
                BulletPoint("Auto-Renewal Dynamics: Subscriptions operate on continuous cycle auto-renewal. Your payment instrument will be charged cyclically unless a cancellation directive is executed no less than 24 hours prior to the cycle terminus.")
                BulletPoint("Sovereign Cancellation: The Company has no jurisdiction over your Play Store billing profile. You must manually navigate to your Google Play Account parameters to initiate a termination of the subscription lifecycle.")
                BulletPoint("Refund Policy: All sub-cycle refunds are adjudicated exclusively according to the Google Play Developer Distribution Agreement and standard user refund matrices.")

                PolicySection(title = "7. COMPREHENSIVE LIMITATION OF LIABILITY")
                PolicyText("READ THIS SECTION CAREFULLY AS IT LIMITS OUR LIABILITY TO YOU.")
                PolicyText("TO THE ABSOLUTE MAXIMUM EXTENT PERMITTED BY APPLICABLE GLOBAL LAW, IN NO EVENT WHATSOEVER SHALL PROTCTUS, ITS OFFICERS, DIRECTORS, SHAREHOLDERS, EMPLOYEES, OR AFFILIATED THIRD-PARTY CONTRACTORS BE HELD LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, EXEMPLARY, OR PUNITIVE DAMAGES. THIS EXPLICITLY INCLUDES, BUT IS NOT BOUND TO, CATASTROPHIC DATA LOSS, RANSOMWARE ENCRYPTION DAMANGES, HARDWARE BRICKING, LOSS OF PROFIT REVENUES, OR SEVERE REPUTATIONAL HARM RESULTING DIRECTLY OR INDIRECTLY FROM (A) YOUR DEPLOYMENT OR INABILITY TO DEPLOY THE APPLICATION; (B) THE FAILURE OF THE APPLICATION TO DETECT A NOVEL MALWARE PAYLOAD OR PHISHING VECTOR; OR (C) ANY UNAUTHORIZED EXPLOITATION OF YOUR DEVICE BY MALICIOUS THIRD ACTORS DESPITE THE ACTIVE DEPLOYMENT OF THE APPLICATION. OUR CUMULATIVE AGGREGATE LIABILITY SHALL NOT EXCEED THE FISCAL SUM PAID BY YOU FOR SUBSCRIPTION ACCESS IN THE PRECEDING SIX (6) MONTHS.")

                PolicySection(title = "8. UNILATERAL TERMINATION PRIVILEGES")
                PolicyText("We retain the absolute, unconditional, and unilateral right to paralyze, suspend, or permanently terminate your authorized access to the Application infrastructure at any moment, devoid of prior notice or resulting liability, particularly upon the identification of a material breach of these Terms.")

                PolicySection(title = "9. GOVERNING LAW AND SEVERABILITY")
                PolicyText("This Agreement shall be robustly governed by and adjudicated in accordance with definitive corporate law, nullifying any conflict of law provisions. If any specific clause herein is deemed judicially unenforceable or legally void, that singular provision will be functionally severed, leaving the remaining corpus of this Agreement legally intact and in full proactive effect.")

                PolicySection(title = "10. OFFICIAL LEGAL CORRESPONDENCE")
                PolicyText("For all formal legal notifications, cease and desist declarations, or inquiries concerning the parameters of this Agreement, direct correspondence to:")
                BulletPoint("Department: Protctus Legal Affairs")
                BulletPoint("Digital Protocol: legal@protctus.com")

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
        Text(
            text = content,
            fontSize = 14.sp,
            color = Color(0xFFE2E2E2),
            lineHeight = 22.sp
        )
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
fun PreviewTermsOfServiceScreen() {
    ProtctusTheme {
        TermsOfServiceScreen(navController = rememberNavController())
    }
}
