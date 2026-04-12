package com.example.daycast

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.font.FontWeight
import com.example.daycast.ui.theme.DayCastTheme
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// =========================================
// Google Sign-In Web Client ID
// Get this from: Firebase Console > Authentication > Sign-in method > Google > Web client ID
// =========================================
private const val GOOGLE_WEB_CLIENT_ID = "494066219423-fsr0kk1btfts64q77jrup405462n0ktt.apps.googleusercontent.com"

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DayCastTheme {
                DayCastApp()
            }
        }
    }
}

// =========================================
// App Root
// =========================================

@Composable
fun DayCastApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val factory = remember { DayCastViewModelFactory(db.dayCastDao()) }
    val viewModel: DayCastViewModel = viewModel(factory = factory)
    val lockManager = remember { AppLockManager(context) }

    var isUnlocked by remember { mutableStateOf(!lockManager.isLockEnabled) }

    if (!isUnlocked) {
        LockScreen(
            lockManager = lockManager,
            onUnlocked = { isUnlocked = true }
        )
    } else {
        AdaptiveAppHost(viewModel, lockManager)
    }
}

// =========================================
// Nav Item Data
// =========================================

private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem("Planner", Icons.Filled.DateRange,     Icons.Outlined.DateRange),
    NavItem("Notes",   Icons.Filled.Edit,          Icons.Outlined.Edit),
    NavItem("Journal", Icons.Filled.Book,          Icons.Outlined.Book),
    NavItem("PDF",     Icons.Filled.PictureAsPdf,  Icons.Outlined.PictureAsPdf)
)

// =========================================
// Floating Nav Bar
// =========================================

@Composable
fun FloatingNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEachIndexed { index, item ->
                    FloatingNavItem(
                        item = item,
                        selected = selectedIndex == index,
                        onClick = { onItemSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Subtle scale on selection — no bouncy overshoot
    val scale by animateFloatAsState(
        targetValue   = if (selected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium
        ),
        label = "navItemScale"
    )

    // Smooth color transitions
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(250),
        label = "navBg"
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else          MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "navFg"
    )

    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(24.dp),
        color    = bgColor,
        modifier = Modifier.scale(scale)
    ) {
        Column(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector        = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint               = fgColor,
                modifier           = Modifier.size(24.dp)
            )
            Text(
                text  = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor
            )
        }
    }
}

// =========================================
// Adaptive App Host with Floating Nav
// =========================================

@Composable
fun AdaptiveAppHost(viewModel: DayCastViewModel, lockManager: AppLockManager? = null) {
    var selectedItemIndex  by remember { mutableIntStateOf(0) }
    var showBackupDialog   by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            FloatingNavBar(
                selectedIndex  = selectedItemIndex,
                onItemSelected = { selectedItemIndex = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedItemIndex,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness    = Spring.StiffnessLow
                        )
                    ) { width -> direction * (width / 4) } +
                            fadeIn(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness    = Spring.StiffnessLow
                                )
                            ) { width -> -direction * (width / 4) } +
                                    fadeOut(tween(150))
                        )
                },
                label = "tabTransition"
            ) { index ->
                val onSettingsClick = { showBackupDialog = true }
                when (index) {
                    0 -> PlannerAdaptiveScreen(viewModel, onSettingsClick = onSettingsClick)
                    1 -> NotesAdaptiveScreen(viewModel, onSettingsClick = onSettingsClick)
                    2 -> JournalAdaptiveScreen(viewModel, onSettingsClick = onSettingsClick)
                    3 -> PdfViewerAdaptiveScreen()
                }
            }
        }
    }

    if (showBackupDialog) {
        BackupDialog(
            viewModel = viewModel,
            onDismiss = { showBackupDialog = false },
            onOpenSecurity = {
                showBackupDialog = false
                showSecurityDialog = true
            }
        )
    }

    if (showSecurityDialog && lockManager != null) {
        SecuritySettingsDialog(
            lockManager = lockManager,
            onDismiss = { showSecurityDialog = false }
        )
    }
}

// =========================================
// Backup & Cloud Sync Dialog
// =========================================

@Composable
fun BackupDialog(
    viewModel: DayCastViewModel,
    onDismiss: () -> Unit,
    onOpenSecurity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val sync    = viewModel.firebaseSync

    var status    by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var isError   by remember { mutableStateOf(false) }

    // Auth state (recompose when sign-in changes)
    var isSignedIn by remember { mutableStateOf(sync.isSignedIn) }
    var userEmail  by remember { mutableStateOf(sync.userEmail ?: "") }

    // Sign-in form fields
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isNewAccount by remember { mutableStateOf(false) }

    fun setStatus(msg: String, error: Boolean = false) {
        status    = msg
        isError   = error
        isWorking = false
    }

    // File import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isWorking = true; status = "Importing..."
            scope.launch {
                val manager = BackupManager(AppDatabase.getDatabase(context).dayCastDao(), context)
                when (val r = manager.importFromUri(uri)) {
                    is ImportResult.Success ->
                        setStatus("Imported: ${r.notes} notes, ${r.tasks} tasks, ${r.events} events, ${r.journal} journal entries.")
                    is ImportResult.Failure -> setStatus("Import failed: ${r.message}", true)
                }
            }
        }
    }

    // Share launcher
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        icon  = { Icon(Icons.Outlined.Settings, contentDescription = null) },
        title = { Text("Backup & Cloud Sync") },
        text  = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // ---- Cloud Sync section ----
                item(key = "cloud_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Cloud Sync",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isSignedIn) {
                    // Signed-in state
                    item(key = "signed_in_info") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier              = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Signed in as", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(userEmail, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium)
                                }
                                TextButton(onClick = {
                                    sync.signOut()
                                    isSignedIn = false
                                    userEmail  = ""
                                    setStatus("Signed out.")
                                }) { Text("Sign out") }
                            }
                        }
                    }
                    item(key = "cloud_actions") {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Upload
                            FilledTonalButton(
                                onClick  = {
                                    isWorking = true; status = "Uploading to cloud..."
                                    scope.launch {
                                        val dao = AppDatabase.getDatabase(context).dayCastDao()
                                        when (val r = sync.uploadAll(dao)) {
                                            is SyncResult.Success ->
                                                setStatus("Uploaded: ${r.notes} notes, ${r.tasks} tasks, ${r.events} events, ${r.journal} entries.")
                                            is SyncResult.Failure -> setStatus("Upload failed: ${r.message}", true)
                                            is SyncResult.NotSignedIn -> setStatus("Not signed in.", true)
                                        }
                                    }
                                },
                                enabled  = !isWorking,
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.CloudUpload, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Upload")
                            }
                            // Download / Restore
                            FilledTonalButton(
                                onClick  = {
                                    isWorking = true; status = "Restoring from cloud..."
                                    scope.launch {
                                        val dao = AppDatabase.getDatabase(context).dayCastDao()
                                        when (val r = sync.downloadAll(dao)) {
                                            is SyncResult.Success ->
                                                setStatus("Restored: ${r.notes} notes, ${r.tasks} tasks, ${r.events} events, ${r.journal} entries.")
                                            is SyncResult.Failure -> setStatus("Restore failed: ${r.message}", true)
                                            is SyncResult.NotSignedIn -> setStatus("Not signed in.", true)
                                        }
                                    }
                                },
                                enabled  = !isWorking,
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.CloudDownload, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore")
                            }
                        }
                    }
                } else {
                    // Sign-in form
                    item(key = "sign_in_hint") {
                        Text(
                            "Sign in to sync your data across devices and protect it from reinstalls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ---- Google Sign-In (primary) ----
                    item(key = "google_sign_in") {
                        Button(
                            onClick = {
                                isWorking = true
                                status    = "Signing in with Google..."
                                scope.launch {
                                    try {
                                        val credentialManager = CredentialManager.create(context)

                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(GOOGLE_WEB_CLIENT_ID)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val credentialResponse = credentialManager.getCredential(
                                            context = context,
                                            request = request
                                        )

                                        val googleIdTokenCredential = GoogleIdTokenCredential
                                            .createFrom(credentialResponse.credential.data)

                                        val result = sync.signInWithGoogleToken(
                                            googleIdTokenCredential.idToken
                                        )

                                        result.fold(
                                            onSuccess = {
                                                isSignedIn = true
                                                userEmail  = sync.userEmail ?: ""
                                                setStatus("Signed in as $userEmail.")
                                            },
                                            onFailure = { e ->
                                                setStatus(e.message ?: "Firebase auth failed.", true)
                                            }
                                        )
                                    } catch (e: Exception) {
                                        setStatus(e.message ?: "Google sign-in failed.", true)
                                    }
                                }
                            },
                            enabled  = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    }

                    // ---- Divider between methods ----
                    item(key = "auth_divider") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text(
                                "or use email",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                    }

                    // ---- Email / Password (secondary) ----
                    item(key = "email_field") {
                        OutlinedTextField(
                            value         = email,
                            onValueChange = { email = it },
                            label         = { Text("Email") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(12.dp)
                        )
                    }
                    item(key = "password_field") {
                        OutlinedTextField(
                            value         = password,
                            onValueChange = { password = it },
                            label         = { Text("Password") },
                            singleLine    = true,
                            modifier      = Modifier.fillMaxWidth(),
                            shape         = RoundedCornerShape(12.dp)
                        )
                    }
                    item(key = "auth_buttons") {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick  = {
                                    isWorking = true
                                    status    = if (isNewAccount) "Creating account..." else "Signing in..."
                                    scope.launch {
                                        val result = if (isNewAccount)
                                            sync.createAccount(email.trim(), password)
                                        else
                                            sync.signIn(email.trim(), password)

                                        result.fold(
                                            onSuccess = {
                                                isSignedIn = true
                                                userEmail  = sync.userEmail ?: email.trim()
                                                setStatus("Signed in as ${userEmail}.")
                                            },
                                            onFailure = { setStatus(it.message ?: "Auth failed.", true) }
                                        )
                                    }
                                },
                                enabled  = email.isNotBlank() && password.length >= 6 && !isWorking,
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text(if (isNewAccount) "Create Account" else "Sign In") }

                            OutlinedButton(
                                onClick  = { isNewAccount = !isNewAccount },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text(if (isNewAccount) "Have account?" else "New account?") }
                        }
                    }
                }

                // ---- Divider ----
                item(key = "divider") { HorizontalDivider() }

                // ---- Local Backup section ----
                item(key = "local_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Local Backup",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                item(key = "local_hint") {
                    Text(
                        "Export to a JSON file you can save anywhere. Import merges data without deleting existing entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "local_actions") {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick  = {
                                isWorking = true; status = "Preparing export..."
                                scope.launch {
                                    try {
                                        val manager = BackupManager(AppDatabase.getDatabase(context).dayCastDao(), context)
                                        shareLauncher.launch(manager.createExportIntent())
                                        setStatus("")
                                    } catch (e: Exception) {
                                        setStatus("Export failed: ${e.message}", true)
                                    }
                                }
                            },
                            enabled  = !isWorking,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export")
                        }
                        FilledTonalButton(
                            onClick  = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled  = !isWorking,
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.FileDownload, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import")
                        }
                    }
                }

                // ---- Divider ----
                item(key = "security_divider") { HorizontalDivider() }

                // ---- Security section ----
                item(key = "security_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Security",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                item(key = "security_action") {
                    FilledTonalButton(
                        onClick  = onOpenSecurity,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("App Lock & Biometric")
                    }
                }

                // ---- Status / progress ----
                if (isWorking) {
                    item(key = "progress") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                }
                if (status.isNotEmpty()) {
                    item(key = "status") {
                        Text(
                            text  = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else         MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Done") }
        }
    )
}

// =========================================
// PDF Viewer Screen
// =========================================

enum class AnnotationTool { NONE, PEN, HIGHLIGHTER, ERASER }

data class InkStroke(
    val path: Path,
    val color: Color,
    val width: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerAdaptiveScreen() {
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var currentTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var hasEdits by remember { mutableStateOf(false) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) { pdfUri = uri; hasEdits = false } }
    )

    // Save-to-device launcher (exports a copy of the PDF)
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { destUri ->
            if (destUri != null && pdfUri != null) {
                try {
                    context.contentResolver.openInputStream(pdfUri!!)?.use { input ->
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    )

    if (pdfUri == null) {
        // ── Empty State ──
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("PDF Viewer", style = MaterialTheme.typography.headlineMedium)
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No document open",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "Open a PDF to view, annotate, and save",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            documentPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select a PDF")
                    }
                }
            }
        }
    } else {
        // ── Viewer State ──
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("PDF Viewer", style = MaterialTheme.typography.headlineMedium)
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        // Save to Device — only visible after edits
                        AnimatedVisibility(
                            visible = hasEdits,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f)
                        ) {
                            FilledTonalButton(
                                onClick = { saveLauncher.launch("DayCast_annotated.pdf") },
                                modifier = Modifier.padding(end = 4.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.SaveAlt,
                                    contentDescription = "Save to device",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        IconButton(onClick = { pdfUri = null; hasEdits = false }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close PDF")
                        }
                    }
                )
            },
            bottomBar = {
                AnnotationToolbar(
                    currentTool = currentTool,
                    onToolSelected = { currentTool = it }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                val uri = pdfUri
                AndroidFragment<PdfViewerFragment>(
                    modifier = Modifier.fillMaxSize(),
                    onUpdate = { fragment ->
                        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
                            if (fragment.documentUri != uri) {
                                fragment.documentUri = uri
                            }
                        }
                    }
                )
                PdfInkOverlay(
                    currentTool = currentTool,
                    onStrokeAdded = { hasEdits = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AnnotationToolbar(
    currentTool: AnnotationTool,
    onToolSelected: (AnnotationTool) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            data class ToolOption(
                val tool: AnnotationTool,
                val label: String,
                val icon: ImageVector
            )

            val tools = remember {
                listOf(
                    ToolOption(AnnotationTool.NONE,        "Scroll",    Icons.Outlined.PanTool),
                    ToolOption(AnnotationTool.PEN,         "Pen",       Icons.Outlined.Edit),
                    ToolOption(AnnotationTool.HIGHLIGHTER, "Highlight", Icons.Outlined.Create),
                    ToolOption(AnnotationTool.ERASER,      "Eraser",    Icons.Outlined.Delete)
                )
            }

            tools.forEach { option ->
                val isSelected = currentTool == option.tool
                FilterChip(
                    selected = isSelected,
                    onClick = { onToolSelected(option.tool) },
                    label = {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            option.icon,
                            contentDescription = option.label,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

// =========================================
// Ink Overlay Canvas
// =========================================

@Composable
fun PdfInkOverlay(
    currentTool: AnnotationTool,
    onStrokeAdded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var recomposeTrigger by remember { mutableIntStateOf(0) }

    val strokeColor = when (currentTool) {
        AnnotationTool.PEN        -> Color(0xFFD32F2F)
        AnnotationTool.HIGHLIGHTER -> Color(0xFFFFEB3B).copy(alpha = 0.5f)
        else                      -> Color.Transparent
    }

    val strokeWidth = when (currentTool) {
        AnnotationTool.PEN        -> 8f
        AnnotationTool.HIGHLIGHTER -> 40f
        else                      -> 0f
    }

    val canvasModifier = if (currentTool != AnnotationTool.NONE) {
        modifier.pointerInput(currentTool) {
            if (currentTool == AnnotationTool.ERASER) {
                detectTapGestures(onTap = { strokes.clear() })
                return@pointerInput
            }
            detectDragGestures(
                onDragStart = { offset ->
                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                },
                onDrag = { change, _ ->
                    change.consume()
                    currentPath?.lineTo(change.position.x, change.position.y)
                    recomposeTrigger++
                },
                onDragEnd = {
                    currentPath?.let { path ->
                        strokes.add(InkStroke(path, strokeColor, strokeWidth))
                        onStrokeAdded()
                    }
                    currentPath = null
                }
            )
        }
    } else {
        modifier
    }

    Canvas(modifier = canvasModifier) {
        @Suppress("UNUSED_EXPRESSION")
        recomposeTrigger

        strokes.forEach { stroke ->
            drawPath(
                path  = stroke.path,
                color = stroke.color,
                style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        currentPath?.let { path ->
            drawPath(
                path  = path,
                color = strokeColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}