@file:OptIn(ExperimentalLayoutApi::class)

package com.example.daycast

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch

// =========================================
// Sort Order
// =========================================

enum class NoteSort(val label: String) {
    PINNED_FIRST("Pinned first"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    AZ("A-Z")
}

// =========================================
// Drawer Destination
// =========================================

private enum class NotesDrawerDest(val label: String) {
    NOTES("Notes"),
    ARCHIVE("Archive"),
    TRASH("Trash")
}

// =========================================
// Notes Root Screen
// =========================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesAdaptiveScreen(viewModel: DayCastViewModel, onSettingsClick: () -> Unit = {}) {
    val notes by viewModel.allNotes.collectAsState()
    val archivedNotes by viewModel.archivedNotes.collectAsState()
    val trashedNotes by viewModel.trashedNotes.collectAsState()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(NoteSort.PINNED_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    var drawerDest by remember { mutableStateOf(NotesDrawerDest.NOTES) }
    var showNewLabelDialog by remember { mutableStateOf(false) }

    // Unique folder list derived from all notes
    val folders = remember(notes) {
        listOf("All") + notes
            .map { it.folder }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    // Ensure selected folder still exists after a delete
    LaunchedEffect(folders) {
        if (selectedFolder !in folders) selectedFolder = "All"
    }

    // Filter then sort
    val filteredNotes = remember(notes, searchQuery, selectedFolder, sortOrder) {
        val filtered = notes.filter { note ->
            (searchQuery.isBlank() ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    note.tags.contains(searchQuery, ignoreCase = true)) &&
                    (selectedFolder == "All" || note.folder == selectedFolder)
        }
        when (sortOrder) {
            NoteSort.PINNED_FIRST -> filtered.sortedWith(
                compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp }
            )
            NoteSort.NEWEST -> filtered.sortedByDescending { it.timestamp }
            NoteSort.OLDEST -> filtered.sortedBy { it.timestamp }
            NoteSort.AZ -> filtered.sortedBy { it.content.lowercase() }
        }
    }

    // Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NotesDrawerContent(
                folders = folders,
                selectedFolder = selectedFolder,
                drawerDest = drawerDest,
                onDestSelected = { dest ->
                    drawerDest = dest
                    scope.launch { drawerState.close() }
                },
                onFolderSelect = { folder ->
                    selectedFolder = folder
                    drawerDest = NotesDrawerDest.NOTES
                    scope.launch { drawerState.close() }
                },
                onNewLabel = { showNewLabelDialog = true },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettingsClick()
                },
                onHelpFeedback = {
                    scope.launch { drawerState.close() }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("jcmappstudio@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "DayCast - Help & Feedback")
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        // Fallback: use ACTION_SEND if no mailto handler
                        val fallback = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("jcmappstudio@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "DayCast - Help & Feedback")
                        }
                        context.startActivity(Intent.createChooser(fallback, "Send feedback"))
                    }
                }
            )
        }
    ) {
        when (drawerDest) {
            NotesDrawerDest.NOTES -> {
                Scaffold(
                    topBar = {
                        NotesSearchBar(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            isGridView = isGridView,
                            onToggleView = { isGridView = !isGridView },
                            sortOrder = sortOrder,
                            showSortMenu = showSortMenu,
                            onShowSortMenu = { showSortMenu = it },
                            onSortChange = { sortOrder = it; showSortMenu = false }
                        )
                    },
                    floatingActionButton = {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = { viewModel.addNote(content = "", isChecklist = true) },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "New checklist note")
                            }
                            FloatingActionButton(
                                onClick = { viewModel.addNote(content = "") },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New text note")
                            }
                        }
                    }
                ) { innerPadding ->
                    NavigableListDetailPaneScaffold(
                        navigator = navigator,
                        modifier = Modifier.padding(innerPadding),
                        listPane = {
                            NoteListPane(
                                notes = filteredNotes,
                                folders = folders,
                                selectedFolder = selectedFolder,
                                isGridView = isGridView,
                                onFolderSelect = { selectedFolder = it },
                                onNoteClick = { note ->
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.noteId)
                                    }
                                },
                                onPinToggle = { viewModel.togglePin(it) }
                            )
                        },
                        detailPane = {
                            val selectedNoteId = navigator.currentDestination?.content as? Int
                            val selectedNote = notes.find { it.noteId == selectedNoteId }

                            if (selectedNote != null) {
                                NoteDetailPane(
                                    note = selectedNote,
                                    viewModel = viewModel,
                                    onBack = { scope.launch { navigator.navigateBack() } }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Outlined.EditNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Select a note to view it,\nor tap + to create one.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            NotesDrawerDest.ARCHIVE -> {
                ArchiveOrTrashScreen(
                    title = "Archive",
                    notes = archivedNotes,
                    emptyIcon = Icons.Outlined.Archive,
                    emptyMessage = "No archived notes.",
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onRestore = { viewModel.unarchiveNote(it) },
                    onDelete = { viewModel.permanentlyDeleteNote(it) },
                    restoreLabel = "Unarchive",
                    restoreIcon = Icons.Outlined.Unarchive
                )
            }

            NotesDrawerDest.TRASH -> {
                ArchiveOrTrashScreen(
                    title = "Trash",
                    notes = trashedNotes,
                    emptyIcon = Icons.Outlined.Delete,
                    emptyMessage = "No notes in trash.",
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onRestore = { viewModel.restoreNote(it) },
                    onDelete = { viewModel.permanentlyDeleteNote(it) },
                    restoreLabel = "Restore",
                    restoreIcon = Icons.Outlined.RestoreFromTrash
                )
            }
        }
    }

    // New label dialog
    if (showNewLabelDialog) {
        NewLabelDialog(
            onDismiss = { showNewLabelDialog = false },
            onConfirm = { label ->
                // Create a placeholder note with this folder, user can then add content
                viewModel.addNote(content = "", folder = label)
                showNewLabelDialog = false
            }
        )
    }
}

// =========================================
// Drawer Content (Google Keep style)
// =========================================

@Composable
private fun NotesDrawerContent(
    folders: List<String>,
    selectedFolder: String,
    drawerDest: NotesDrawerDest,
    onDestSelected: (NotesDrawerDest) -> Unit,
    onFolderSelect: (String) -> Unit,
    onNewLabel: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpFeedback: () -> Unit = {}
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier.width(300.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "DayCast Notes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))

        // Notes
        NavigationDrawerItem(
            icon = { Icon(if (drawerDest == NotesDrawerDest.NOTES && selectedFolder == "All") Icons.Default.Lightbulb else Icons.Outlined.Lightbulb, contentDescription = null) },
            label = { Text("Notes") },
            selected = drawerDest == NotesDrawerDest.NOTES && selectedFolder == "All",
            onClick = { onFolderSelect("All") },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Label items (folders)
        if (folders.size > 1) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))
            Text(
                "Labels",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )
            val labelFolders = remember(folders) { folders.filter { it != "All" } }
            labelFolders.forEach { folder ->
                NavigationDrawerItem(
                    icon = { Icon(Icons.Outlined.Label, contentDescription = null) },
                    label = { Text(folder) },
                    selected = selectedFolder == folder && drawerDest == NotesDrawerDest.NOTES,
                    onClick = { onFolderSelect(folder) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // Create new label
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("Create new label") },
            selected = false,
            onClick = onNewLabel,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))

        // Archive
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            label = { Text("Archive") },
            selected = drawerDest == NotesDrawerDest.ARCHIVE,
            onClick = { onDestSelected(NotesDrawerDest.ARCHIVE) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Trash
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            label = { Text("Trash") },
            selected = drawerDest == NotesDrawerDest.TRASH,
            onClick = { onDestSelected(NotesDrawerDest.TRASH) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp))

        // Settings
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClick,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Help & feedback
        NavigationDrawerItem(
            icon = { Icon(Icons.Outlined.HelpOutline, contentDescription = null) },
            label = { Text("Help & feedback") },
            selected = false,
            onClick = onHelpFeedback,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

// =========================================
// Archive / Trash Screen
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveOrTrashScreen(
    title: String,
    notes: List<Note>,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyMessage: String,
    onMenuClick: () -> Unit,
    onRestore: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    restoreLabel: String,
    restoreIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        emptyIcon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notes, key = { it.noteId }) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = note.content.ifBlank { "(empty note)" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (note.tags.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = note.tags,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = { onRestore(note) }) {
                                    Icon(
                                        restoreIcon,
                                        contentDescription = restoreLabel,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(restoreLabel)
                                }
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { onDelete(note) }) {
                                    Icon(
                                        Icons.Outlined.DeleteForever,
                                        contentDescription = "Delete forever",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "Delete forever",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================
// Google Keep-style Search Bar
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSearchBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    sortOrder: NoteSort,
    showSortMenu: Boolean,
    onShowSortMenu: (Boolean) -> Unit,
    onSortChange: (NoteSort) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Open drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Search your notes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
            // Sort button
            Box {
                IconButton(onClick = { onShowSortMenu(true) }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort notes")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenu(false) }
                ) {
                    NoteSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    sort.label,
                                    fontWeight = if (sortOrder == sort) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (sortOrder == sort) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = { onSortChange(sort) }
                        )
                    }
                }
            }
            // Grid / list toggle
            IconButton(onClick = onToggleView) {
                Icon(
                    if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                    contentDescription = if (isGridView) "List view" else "Grid view"
                )
            }
        }
    }
}

// =========================================
// Note List Pane
// =========================================

@Composable
private fun NoteListPane(
    notes: List<Note>,
    folders: List<String>,
    selectedFolder: String,
    isGridView: Boolean,
    onFolderSelect: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onPinToggle: (Note) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Folder filter chips (only show if multiple labels exist)
        if (folders.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders, key = { it }) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onFolderSelect(folder) },
                        label = { Text(folder) },
                        leadingIcon = if (folder != "All") {
                            { Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (notes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (selectedFolder != "All")
                            "No notes in this label."
                        else
                            "Notes you add appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        } else if (isGridView) {
            // Staggered grid (Google Keep style)
            val pinnedNotes = remember(notes) { notes.filter { it.isPinned } }
            val unpinnedNotes = remember(notes) { notes.filter { !it.isPinned } }

            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp
            ) {
                // Pinned section header
                if (pinnedNotes.isNotEmpty()) {
                    item(key = "pinned_header", span = StaggeredGridItemSpan.Companion.FullLine) {
                        Text(
                            "PINNED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    items(pinnedNotes, key = { "pinned_${it.noteId}" }) { note ->
                        NoteGridCard(
                            note = note,
                            onClick = { onNoteClick(note) },
                            onPinToggle = { onPinToggle(note) }
                        )
                    }
                }
                // Others section header
                if (pinnedNotes.isNotEmpty() && unpinnedNotes.isNotEmpty()) {
                    item(key = "others_header", span = StaggeredGridItemSpan.Companion.FullLine) {
                        Text(
                            "OTHERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                }
                items(unpinnedNotes, key = { "note_${it.noteId}" }) { note ->
                    NoteGridCard(
                        note = note,
                        onClick = { onNoteClick(note) },
                        onPinToggle = { onPinToggle(note) }
                    )
                }
            }
        } else {
            // List view
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(notes, key = { it.noteId }) { note ->
                    NoteListCard(
                        note = note,
                        onClick = { onNoteClick(note) },
                        onPinToggle = { onPinToggle(note) }
                    )
                }
            }
        }
    }
}

// =========================================
// Note Grid Card (Google Keep staggered style)
// =========================================

@Composable
private fun NoteGridCard(
    note: Note,
    onClick: () -> Unit,
    onPinToggle: () -> Unit
) {
    val tagList = remember(note.tags) {
        note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title / content preview
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content.lines().firstOrNull()?.take(50) ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (note.content.length > 50 || note.content.lines().size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isChecklist) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (note.isChecklist) "Checklist" else "Empty note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Tags
            if (tagList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagList.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (tagList.size > 3) {
                        Text(
                            "+${tagList.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Bottom row: folder + pin
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (note.folder.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Label,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            note.folder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// =========================================
// Note List Card (list view fallback)
// =========================================

@Composable
fun NoteListCard(
    note: Note,
    onClick: () -> Unit,
    onPinToggle: () -> Unit
) {
    val tagList = remember(note.tags) {
        note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.isChecklist) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Checklist",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = note.content.ifBlank { if (note.isChecklist) "Checklist" else "Empty note" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onPinToggle,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = if (note.isPinned)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (note.folder.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.Label,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        note.folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (tagList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    tagList.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================
// Note Detail Pane
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailPane(
    note: Note,
    viewModel: DayCastViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val checklistItems by remember(note.noteId) {
        viewModel.getChecklistItemsForNote(note.noteId)
    }.collectAsState(initial = emptyList())

    var isEditing by remember(note.noteId) { mutableStateOf(note.content.isBlank()) }
    var editedContent by remember(note.noteId) { mutableStateOf(note.content) }
    var editedTags by remember(note.noteId) { mutableStateOf(note.tags) }
    var editedFolder by remember(note.noteId) { mutableStateOf(note.folder) }
    var editedIsChecklist by remember(note.noteId) { mutableStateOf(note.isChecklist) }
    var newTagInput by remember { mutableStateOf("") }
    var showTagInput by remember(note.noteId) { mutableStateOf(false) }
    var newItemText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { },
                actions = {
                    // Pin toggle
                    IconButton(onClick = { viewModel.togglePin(note) }) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (note.isPinned) "Unpin" else "Pin",
                            tint = if (note.isPinned)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isEditing) {
                        // Mode toggle: text vs checklist
                        IconButton(onClick = { editedIsChecklist = !editedIsChecklist }) {
                            Icon(
                                imageVector = if (editedIsChecklist)
                                    Icons.Default.CheckCircle else Icons.Outlined.Notes,
                                contentDescription = "Toggle mode",
                                tint = if (editedIsChecklist)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Save
                        IconButton(onClick = {
                            viewModel.updateNote(
                                note.copy(
                                    content = editedContent,
                                    tags = editedTags,
                                    folder = editedFolder.trim(),
                                    isChecklist = editedIsChecklist
                                )
                            )
                            isEditing = false
                            showTagInput = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save note")
                        }
                        // Cancel
                        IconButton(onClick = {
                            editedContent = note.content
                            editedTags = note.tags
                            editedFolder = note.folder
                            editedIsChecklist = note.isChecklist
                            isEditing = false
                            showTagInput = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit note")
                        }
                        IconButton(onClick = {
                            viewModel.archiveNote(note)
                            scope.launch { onBack() }
                        }) {
                            Icon(Icons.Outlined.Archive, contentDescription = "Archive note")
                        }
                        IconButton(onClick = {
                            viewModel.deleteNote(note)
                            scope.launch { onBack() }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete note")
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Folder field
            if (isEditing) {
                item(key = "folder_edit") {
                    OutlinedTextField(
                        value = editedFolder,
                        onValueChange = { editedFolder = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        label = { Text("Label (optional)") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            } else if (note.folder.isNotBlank()) {
                item(key = "folder_display") {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Label,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Text(
                            note.folder,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Tags row
            item(key = "tags") {
                val tagList = editedTags
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (tagList.isNotEmpty() || isEditing) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tagList.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = if (isEditing) {
                                    {
                                        IconButton(
                                            onClick = {
                                                val updated = tagList
                                                    .filter { it != tag }
                                                    .joinToString(", ")
                                                editedTags = updated
                                            },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove tag",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                } else null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        if (isEditing) {
                            if (showTagInput) {
                                OutlinedTextField(
                                    value = newTagInput,
                                    onValueChange = { newTagInput = it },
                                    modifier = Modifier.width(120.dp),
                                    placeholder = { Text("Tag name") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val trimmed = newTagInput.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val updated = (tagList + trimmed).joinToString(", ")
                                                editedTags = updated
                                                newTagInput = ""
                                            }
                                            showTagInput = false
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Add tag")
                                        }
                                    }
                                )
                            } else {
                                AssistChip(
                                    onClick = { showTagInput = true },
                                    label = { Text("+ Tag") },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }

            // Content - text mode
            if (!editedIsChecklist) {
                item(key = "text_content") {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 200.dp)
                                .padding(horizontal = 16.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text("Note") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    } else {
                        Text(
                            text = note.content.ifBlank { "Tap the pencil to start writing." },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (note.content.isBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Content - checklist mode
            if (editedIsChecklist) {
                val activeItems = checklistItems.filter { !it.isChecked }
                val doneItems = checklistItems.filter { it.isChecked }

                itemsIndexed(activeItems, key = { _, item -> item.itemId }) { _, item ->
                    ChecklistItemRow(
                        item = item,
                        isEditing = isEditing,
                        onToggle = { viewModel.toggleChecklistItem(item) },
                        onDelete = { viewModel.deleteChecklistItem(item) },
                        onTextChange = { viewModel.updateChecklistItem(item.copy(text = it)) }
                    )
                }

                if (isEditing) {
                    item(key = "add_checklist_item") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Add item...") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    if (newItemText.isNotBlank()) {
                                        IconButton(onClick = {
                                            viewModel.addChecklistItem(
                                                noteId = note.noteId,
                                                text = newItemText.trim(),
                                                position = checklistItems.size
                                            )
                                            newItemText = ""
                                        }) {
                                            Icon(Icons.Default.Check, contentDescription = "Add item")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                if (doneItems.isNotEmpty()) {
                    item(key = "completed_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Text(
                                "Completed (${doneItems.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    itemsIndexed(doneItems, key = { _, item -> "done_${item.itemId}" }) { _, item ->
                        ChecklistItemRow(
                            item = item,
                            isEditing = isEditing,
                            onToggle = { viewModel.toggleChecklistItem(item) },
                            onDelete = { viewModel.deleteChecklistItem(item) },
                            onTextChange = { viewModel.updateChecklistItem(item.copy(text = it)) }
                        )
                    }
                }
            }
        }
    }
}

// =========================================
// Checklist Item Row
// =========================================

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    isEditing: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTextChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() }
        )
        if (isEditing) {
            OutlinedTextField(
                value = item.text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete item",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        } else {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                color = if (item.isChecked)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// =========================================
// New Label Dialog
// =========================================

@Composable
private fun NewLabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var labelName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create new label") },
        text = {
            OutlinedTextField(
                value = labelName,
                onValueChange = { labelName = it },
                label = { Text("Label name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (labelName.isNotBlank()) onConfirm(labelName.trim()) },
                enabled = labelName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}