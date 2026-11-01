package com.protctus.app.ui.screens.scanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protctus.app.ui.theme.ProtctusTheme

class ThreatBlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val packageName = intent.getStringExtra("PACKAGE_NAME") ?: "unknown"
        val appName = intent.getStringExtra("APP_NAME") ?: "Unknown App"
        val riskScore = intent.getIntExtra("RISK_SCORE", 100)
        val anomalies = intent.getStringArrayListExtra("ANOMALIES") ?: arrayListOf()

        setContent {
            ProtctusTheme {
                Scaffold { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF8B0000)) // Blood Red Threat Level
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = "Warning",
                                tint = Color.White,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = stringResource(R.string.threat_blocked),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.protctus_sentinel_intervened),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Malicious App: $appName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Package: $packageName", color = Color.Gray, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Risk Score: $riskScore / 100", color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(stringResource(R.string.anomalies_found), color = Color.White)
                                    anomalies.forEach { anomaly ->
                                        Text("• $anomaly", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            Button(
                                onClick = {
                                    val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                                    startActivity(uninstallIntent)
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth().height(60.dp)
                            ) {
                                Text(stringResource(R.string.uninstall_immediately), color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.dismiss_not_recommended), color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
