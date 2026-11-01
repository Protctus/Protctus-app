package com.protctus.app.ui.screens.scanner

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.protctus.app.viewmodel.ScannerViewModel
import com.protctus.app.viewmodel.ScanState

@Composable
fun ScanResultsScreen(navController: NavController, scannerViewModel: ScannerViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val scanThreats by scannerViewModel.scanThreats.collectAsState()
    val scanState by scannerViewModel.scanState.collectAsState()
    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.scan_results),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.your_system_health_report),
                color = Color(0xFFA0A0A0),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (scanThreats.isEmpty()) {
                // Safe Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.system_is_safe), color = Color(0xFF4CAF50), fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_critical_threats_or_highly_privileged),
                            color = Color(0xFFA0A0A0),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                // Header Card for Threats
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(stringResource(R.string.critical_issues), color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${scanThreats.size} high-risk apps detected", color = Color(0xFFA0A0A0), fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Details for each threat
                scanThreats.forEach { threat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(threat.appName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            ResultRow("Package: ", threat.packageName)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Type: ", threat.threatType)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Risk Score: ", threat.riskScore)
                            Spacer(modifier = Modifier.height(8.dp))
                            ResultRow("Details: ", threat.detailedReason)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Third Card - Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = com.protctus.app.ui.theme.SurfaceColor)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.summary), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.please_review_the_permissions_for_the_li),
                            color = Color(0xFFA0A0A0),
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color(0xFFA0A0A0), fontSize = 14.sp, fontStyle = FontStyle.Italic)
    }
}
