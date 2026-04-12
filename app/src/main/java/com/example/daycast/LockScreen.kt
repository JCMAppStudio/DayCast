package com.example.daycast

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// =========================================
// Lock Screen
// =========================================

@Composable
fun LockScreen(
    lockManager: AppLockManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf("") }

    // Attempt biometric on first composition if enabled
    var biometricAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (lockManager.isBiometricEnabled && !biometricAttempted) {
            biometricAttempted = true
            showBiometricPrompt(context as FragmentActivity, onUnlocked) { err ->
                errorMessage = err
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon / branding
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "DayCast is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (lockManager.lockType == AppLockManager.LockType.PIN)
                    "Enter your PIN to continue"
                else
                    "Enter your password to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            when (lockManager.lockType) {
                AppLockManager.LockType.PIN -> {
                    PinEntryView(
                        onPinComplete = { pin ->
                            if (lockManager.verifyCredential(pin)) {
                                errorMessage = ""
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect PIN"
                            }
                        },
                        errorMessage = errorMessage
                    )
                }
                AppLockManager.LockType.PASSWORD -> {
                    PasswordEntryView(
                        onSubmit = { password ->
                            if (lockManager.verifyCredential(password)) {
                                errorMessage = ""
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect password"
                            }
                        },
                        errorMessage = errorMessage
                    )
                }
                else -> { onUnlocked() }
            }

            // Biometric button
            if (lockManager.isBiometricEnabled) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        showBiometricPrompt(context as FragmentActivity, onUnlocked) { err ->
                            errorMessage = err
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Use biometric")
                }
            }
        }
    }
}

// =========================================
// PIN Entry (4-digit)
// =========================================

@Composable
private fun PinEntryView(
    onPinComplete: (String) -> Unit,
    errorMessage: String
) {
    var pin by remember { mutableStateOf("") }

    // Clear PIN on error so user can retry
    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) pin = ""
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // PIN dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        // Error message
        if (errorMessage.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(32.dp))

        // Number pad
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else if (label == "DEL") {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Backspace,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            onClick = {
                                if (pin.length < 4) {
                                    pin += label
                                    if (pin.length == 4) {
                                        onPinComplete(pin)
                                    }
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================
// Password Entry
// =========================================

@Composable
private fun PasswordEntryView(
    onSubmit: (String) -> Unit,
    errorMessage: String
) {
    var password by remember { mutableStateOf("") }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNotEmpty()) password = ""
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = errorMessage.isNotEmpty()
        )

        if (errorMessage.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSubmit(password) },
            enabled = password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Unlock")
        }
    }
}

// =========================================
// Biometric Prompt Helper
// =========================================

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuthenticate = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
    )

    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometric not available on this device")
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // User cancelled or other non-fatal error, don't show error for cancel
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                errorCode != BiometricPrompt.ERROR_CANCELED) {
                onError(errString.toString())
            }
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometric not recognized")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock DayCast")
        .setSubtitle("Use your biometric to continue")
        .setNegativeButtonText("Use PIN/Password")
        .build()

    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}

// =========================================
// Security Settings Dialog
// =========================================

@Composable
fun SecuritySettingsDialog(
    lockManager: AppLockManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(SecurityStep.MAIN) }

    // For setting up new PIN/password
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var biometricOn by remember { mutableStateOf(lockManager.isBiometricEnabled) }

    // Biometric availability
    val biometricAvailable = remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
        title = {
            Text(when (currentStep) {
                SecurityStep.MAIN           -> "Security"
                SecurityStep.SET_PIN        -> "Set PIN"
                SecurityStep.CONFIRM_PIN    -> "Confirm PIN"
                SecurityStep.SET_PASSWORD   -> "Set Password"
                SecurityStep.CONFIRM_PASSWORD -> "Confirm Password"
                SecurityStep.VERIFY_CURRENT -> "Verify Current Lock"
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (currentStep) {
                    SecurityStep.MAIN -> {
                        // Current status
                        val statusText = when (lockManager.lockType) {
                            AppLockManager.LockType.PIN      -> "App lock: PIN enabled"
                            AppLockManager.LockType.PASSWORD  -> "App lock: Password enabled"
                            AppLockManager.LockType.NONE      -> "App lock: Off"
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Set PIN option
                        Surface(
                            onClick = {
                                if (lockManager.isLockEnabled) {
                                    // Need to verify current lock first
                                    currentStep = SecurityStep.VERIFY_CURRENT
                                } else {
                                    currentStep = SecurityStep.SET_PIN
                                }
                                newPin = ""; confirmPin = ""; errorMessage = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Pin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (lockManager.lockType == AppLockManager.LockType.PIN)
                                            "Change PIN" else "Set up PIN",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "4-digit numeric code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Set password option
                        Surface(
                            onClick = {
                                currentStep = SecurityStep.SET_PASSWORD
                                newPassword = ""; confirmPassword = ""; errorMessage = ""
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Password,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (lockManager.lockType == AppLockManager.LockType.PASSWORD)
                                            "Change password" else "Set up password",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Alphanumeric, 6+ characters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Biometric toggle
                        if (biometricAvailable && lockManager.isLockEnabled) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Fingerprint,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Biometric unlock",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "Fingerprint or face unlock",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = biometricOn,
                                        onCheckedChange = {
                                            biometricOn = it
                                            lockManager.isBiometricEnabled = it
                                        }
                                    )
                                }
                            }
                        }

                        // Remove lock option
                        if (lockManager.isLockEnabled) {
                            TextButton(
                                onClick = {
                                    lockManager.removeLock()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Outlined.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Remove app lock",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    SecurityStep.VERIFY_CURRENT -> {
                        var verifyInput by remember { mutableStateOf("") }
                        Text(
                            "Enter your current ${if (lockManager.lockType == AppLockManager.LockType.PIN) "PIN" else "password"} to continue.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = verifyInput,
                            onValueChange = { verifyInput = it },
                            label = { Text(if (lockManager.lockType == AppLockManager.LockType.PIN) "Current PIN" else "Current password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            isError = errorMessage.isNotEmpty()
                        )
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (lockManager.verifyCredential(verifyInput)) {
                                    errorMessage = ""
                                    currentStep = SecurityStep.SET_PIN
                                } else {
                                    errorMessage = "Incorrect"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Continue") }
                    }

                    SecurityStep.SET_PIN -> {
                        Text(
                            "Enter a 4-digit PIN.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                            label = { Text("New PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (newPin.length == 4) {
                                    errorMessage = ""
                                    currentStep = SecurityStep.CONFIRM_PIN
                                } else {
                                    errorMessage = "PIN must be 4 digits"
                                }
                            },
                            enabled = newPin.length == 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Next") }
                    }

                    SecurityStep.CONFIRM_PIN -> {
                        Text(
                            "Re-enter your PIN to confirm.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                            label = { Text("Confirm PIN") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            isError = errorMessage.isNotEmpty()
                        )
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (confirmPin == newPin) {
                                    lockManager.setPin(newPin)
                                    onDismiss()
                                } else {
                                    errorMessage = "PINs don't match"
                                    confirmPin = ""
                                }
                            },
                            enabled = confirmPin.length == 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Set PIN") }
                    }

                    SecurityStep.SET_PASSWORD -> {
                        Text(
                            "Choose a password (6+ characters).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (newPassword.length >= 6) {
                                    errorMessage = ""
                                    currentStep = SecurityStep.CONFIRM_PASSWORD
                                } else {
                                    errorMessage = "Password must be at least 6 characters"
                                }
                            },
                            enabled = newPassword.length >= 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Next") }
                    }

                    SecurityStep.CONFIRM_PASSWORD -> {
                        Text(
                            "Re-enter your password to confirm.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            isError = errorMessage.isNotEmpty()
                        )
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = {
                                if (confirmPassword == newPassword) {
                                    lockManager.setPassword(newPassword)
                                    onDismiss()
                                } else {
                                    errorMessage = "Passwords don't match"
                                    confirmPassword = ""
                                }
                            },
                            enabled = confirmPassword.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Set Password") }
                    }
                }
            }
        },
        confirmButton = {
            if (currentStep == SecurityStep.MAIN) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(onClick = {
                    currentStep = SecurityStep.MAIN
                    errorMessage = ""
                }) { Text("Back") }
            }
        }
    )
}

private enum class SecurityStep {
    MAIN, SET_PIN, CONFIRM_PIN, SET_PASSWORD, CONFIRM_PASSWORD, VERIFY_CURRENT
}