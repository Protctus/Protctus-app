package com.protctus.app.ui.screens.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import com.protctus.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.protctus.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(navController: NavController) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val profile by settingsViewModel.profile.collectAsState()
    val userRank by settingsViewModel.userRank.collectAsState()
    val currentTier by settingsViewModel.currentTier.collectAsState()
    val context = LocalContext.current

    // Editable state initialized from Room profile
    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Copy to internal storage so we own the file and don't lose permissions
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "profile_pic.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                inputStream?.close()
                outputStream.close()
                
                settingsViewModel.updateProfileImage(file.absolutePath)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Bottom sheet for profile picture actions
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = com.protctus.app.ui.theme.SurfaceColor
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.profile_photo), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            imagePickerLauncher.launch("image/*")
                            showSheet = false
                        }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.choose_from_gallery), color = Color(0xFFE2E2E2), fontSize = 16.sp)
                }

                if (profile.profileImageUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsViewModel.removeProfileImage()
                                showSheet = false
                            }
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.remove_photo), color = Color(0xFFEF4444), fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        containerColor = com.protctus.app.ui.theme.DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            item {
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.edit_profile),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Profile Image — tappable to change
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(com.protctus.app.ui.theme.SurfaceColor)
                            .clickable { showSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.profileImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(profile.profileImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Avatar",
                                tint = Color(0xFF4A5568),
                                modifier = Modifier.size(72.dp)
                            )
                        }

                        // Camera overlay icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4A5568)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change Photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Username and dynamic rank from Firebase
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "@${profile.username.ifEmpty { "user" }}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF222226), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = userRank,
                            color = Color(0xFFA0A0A0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val (tierColor, tierText) = when (currentTier) {
                        com.protctus.app.data.model.SubscriptionTier.ELITE -> Color(0xFFFFD700) to "Elite"
                        com.protctus.app.data.model.SubscriptionTier.PREMIUM -> Color(0xFF00C6FF) to "Premium"
                        else -> Color(0xFF7F909F) to "Free"
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(tierColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = tierText,
                            color = tierColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = profile.email.ifEmpty { "no email set" },
                        color = Color.White,
                        fontSize = 16.sp,
                        textDecoration = TextDecoration.Underline
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Editable Personal Info
                Text(
                    text = stringResource(R.string.personal),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name field
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.full_name), color = Color(0xFF7F909F)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedBorderColor = Color(0xFF4A5568),
                        unfocusedContainerColor = com.protctus.app.ui.theme.SurfaceColor,
                        focusedContainerColor = com.protctus.app.ui.theme.SurfaceColor,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.contact_info),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                val actualEmail = FirebaseAuth.getInstance().currentUser?.email ?: "No Email"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(com.protctus.app.ui.theme.SurfaceColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = Color(0xFF7F909F),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = actualEmail,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Verified, 
                                contentDescription = "Verified",
                                tint = Color(0xFF86A055), // green
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.verified), color = Color(0xFF86A055), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = { navController.navigate(com.protctus.app.navigation.Route.ChangeEmail) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Email",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Save Button
                Button(
                    onClick = {
                        if (displayName.isBlank()) {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        settingsViewModel.updateProfile(displayName)
                        Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.save_changes), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
