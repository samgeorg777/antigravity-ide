package com.agyide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agyide.engine.BootstrapProgress
import com.agyide.ui.theme.CosmicColors

@Composable
fun SetupProgressScreen(
    progress: BootstrapProgress,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CosmicColors.Surface)
                .border(1.dp, CosmicColors.Cyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = CosmicColors.Cyan,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "ANTIGRAVITY IDE",
                color = CosmicColors.Cyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Initializing Standalone ARM64 Workspace",
                color = CosmicColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = progress.stepTitle,
                color = CosmicColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progress.detail,
                color = CosmicColors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (progress.error == null) {
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = CosmicColors.Cyan,
                    trackColor = CosmicColors.SurfaceElevated
                )
            } else {
                Text(
                    text = progress.error.take(400),
                    color = CosmicColors.Red,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CosmicColors.Background)
                        .padding(10.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
                ) {
                    Text("Retry Setup", color = CosmicColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AuthSetupDialog(
    currentApiKey: String,
    hasOAuthToken: Boolean,
    onSaveApiKey: (String) -> Unit,
    onLaunchGoogleLogin: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CosmicColors.Surface,
        title = {
            Text(
                text = "Connect Antigravity AI",
                color = CosmicColors.Cyan,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (hasOAuthToken) {
                        "Google OAuth credentials detected. You can also configure a Gemini API key below."
                    } else {
                        "Sign in with Google via the embedded terminal (`agy`) or enter a Gemini API key to activate the Agent."
                    },
                    color = CosmicColors.TextSecondary,
                    fontSize = 13.sp
                )

                Button(
                    onClick = onLaunchGoogleLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Purple)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In via Terminal (agy)", color = CosmicColors.TextPrimary)
                }

                HorizontalDivider(color = CosmicColors.SurfaceBorder)

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = CosmicColors.Cyan) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveApiKey(apiKeyInput) },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicColors.Cyan)
            ) {
                Text("Save", color = CosmicColors.Background, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CosmicColors.TextSecondary)
            }
        }
    )
}
