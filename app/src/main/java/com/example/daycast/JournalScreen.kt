package com.example.daycast

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.daycast.BuildConfig
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// =========================================
// Mood options
// =========================================

val MOOD_OPTIONS = listOf(
    "😊 Happy",
    "😌 Calm",
    "🙏 Grateful",
    "💪 Motivated",
    "😔 Sad",
    "😰 Anxious",
    "😤 Frustrated",
    "😴 Tired"
)

// =========================================
// Writing prompts (rotates by day of year)
// =========================================

private val WRITING_PROMPTS = listOf(
    "What made you smile today?",
    "Describe one challenge you faced and what you learned.",
    "What are you most grateful for right now?",
    "What is something you want to let go of?",
    "Write about a person who positively impacted you recently.",
    "What would your ideal day look like tomorrow?",
    "What emotion is strongest in you right now, and why?",
    "Describe a small win from this week.",
    "What is one habit you want to build?",
    "Write a letter to your future self one year from now.",
    "What fear is holding you back, and how can you face it?",
    "What does rest look like for you?",
    "Describe a moment today when you felt fully present.",
    "What do you want to remember about this season of life?",
    "What does 'enough' mean to you right now?"
)

fun todaysPrompt(): String {
    val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    return WRITING_PROMPTS[dayOfYear % WRITING_PROMPTS.size]
}

// =========================================
// Date helpers
// =========================================

private val entryDateFmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val displayDateFmt  = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
private val displayShortFmt = SimpleDateFormat("MMM d", Locale.getDefault())

fun Long.toDisplayDate(): String = displayDateFmt.format(Date(this))
fun Long.toShortDate(): String   = displayShortFmt.format(Date(this))

// =========================================
// Streak calculation
// =========================================

fun calculateStreak(entries: List<JournalEntry>): Int {
    if (entries.isEmpty()) return 0

    val entryDays = entries
        .map { entryDateFmt.format(Date(it.timestamp)) }
        .toSet()

    val cal = Calendar.getInstance()
    var streak = 0

    val todayKey = entryDateFmt.format(cal.time)
    if (!entryDays.contains(todayKey)) {
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }

    while (true) {
        val key = entryDateFmt.format(cal.time)
        if (entryDays.contains(key)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }
    return streak
}

// =========================================
// Extractive summarization (local, no ML Kit)
// =========================================

fun extractSummary(text: String): String {
    val sentences = text.split(Regex("[.!?]+")).map { it.trim() }.filter { it.length > 20 }
    if (sentences.isEmpty()) return text.take(120)
    if (sentences.size == 1) return sentences.first()

    val words = text.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
    val freq  = words.groupingBy { it }.eachCount()

    val best = sentences.maxByOrNull { sentence ->
        sentence.lowercase().split(Regex("\\W+"))
            .filter { it.length > 3 }
            .sumOf { freq[it] ?: 0 }
    }
    return best ?: sentences.first()
}

// =========================================
// Journal Adaptive Screen
// =========================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun JournalAdaptiveScreen(viewModel: DayCastViewModel, onSettingsClick: () -> Unit = {}) {
    val entries by viewModel.allJournalEntries.collectAsState()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()

    var selectedEntry      by remember { mutableStateOf<JournalEntry?>(null) }
    var searchQuery        by remember { mutableStateOf("") }
    var selectedMoodFilter by remember { mutableStateOf<String?>(null) }

    val streak = remember(entries) { calculateStreak(entries) }

    val filteredEntries = remember(entries, searchQuery, selectedMoodFilter) {
        entries.filter { entry ->
            val matchesSearch = searchQuery.isBlank() ||
                    entry.content.contains(searchQuery, ignoreCase = true)
            val matchesMood = selectedMoodFilter == null ||
                    entry.mood == selectedMoodFilter
            matchesSearch && matchesMood
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane  = {
            AnimatedPane {
                JournalListPane(
                    entries            = filteredEntries,
                    allEntries         = entries,
                    streak             = streak,
                    searchQuery        = searchQuery,
                    onSearchChange     = { searchQuery = it },
                    selectedMoodFilter = selectedMoodFilter,
                    onMoodFilterChange = { selectedMoodFilter = it },
                    onEntryClick       = { entry ->
                        selectedEntry = entry
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, entry.entryId)
                    },
                    onNewEntry         = {
                        selectedEntry = null
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, -1)
                    },
                    onSettingsClick    = onSettingsClick
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val key = navigator.currentDestination?.content as? Int
                if (key != null) {
                    JournalEntryEditor(
                        entry    = selectedEntry,
                        onSave   = { content, mood, summary ->
                            if (selectedEntry == null) {
                                viewModel.addJournalEntry(content, mood, summary ?: "")
                            } else {
                                viewModel.updateJournalEntry(
                                    selectedEntry!!.copy(
                                        content   = content,
                                        mood      = mood,
                                        aiSummary = summary ?: ""
                                    )
                                )
                            }
                            navigator.navigateBack()
                        },
                        onDelete = {
                            selectedEntry?.let { viewModel.deleteJournalEntry(it) }
                            navigator.navigateBack()
                        },
                        onBack   = { navigator.navigateBack() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Select an entry or tap Write to start",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    )
}

// =========================================
// Journal List Pane
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListPane(
    entries: List<JournalEntry>,
    allEntries: List<JournalEntry>,
    streak: Int,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedMoodFilter: String?,
    onMoodFilterChange: (String?) -> Unit,
    onEntryClick: (JournalEntry) -> Unit,
    onNewEntry: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val thisMonthEntries = remember(allEntries) {
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH)
        val year  = cal.get(Calendar.YEAR)
        allEntries.filter { entry ->
            val c = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Journal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (streak > 0) {
                        StreakBadge(streak = streak)
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewEntry,
                icon    = { Icon(Icons.Default.Edit, contentDescription = "New entry") },
                text    = { Text("Write") },
                shape   = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier            = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding      = PaddingValues(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Search bar
            item(key = "search") {
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
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.padding(start = 12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = {
                                Text(
                                    "Search entries...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
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
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                }
            }

            // Mood filter chips
            item(key = "mood_filters") {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all_moods") {
                        FilterChip(
                            selected = selectedMoodFilter == null,
                            onClick  = { onMoodFilterChange(null) },
                            label    = { Text("All moods") },
                            leadingIcon = if (selectedMoodFilter == null) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    items(MOOD_OPTIONS, key = { it }) { mood ->
                        val emoji = mood.split(" ").firstOrNull() ?: mood
                        FilterChip(
                            selected = selectedMoodFilter == mood,
                            onClick  = {
                                onMoodFilterChange(if (selectedMoodFilter == mood) null else mood)
                            },
                            label = { Text(emoji) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Monthly reflection card
            if (thisMonthEntries.size >= 5) {
                item(key = "monthly_reflection") {
                    MonthlyReflectionCard(entries = thisMonthEntries)
                }
            }

            // Empty state
            if (entries.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text  = if (searchQuery.isNotEmpty() || selectedMoodFilter != null)
                                    "No entries match your filter"
                                else
                                    "No entries yet. Start writing!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(entries, key = { it.entryId }) { entry ->
                    JournalEntryCard(entry = entry, onClick = { onEntryClick(entry) })
                }
            }
        }
    }
}

// =========================================
// Streak Badge
// =========================================

@Composable
fun StreakBadge(streak: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("🔥", style = MaterialTheme.typography.labelMedium)
            Text(
                text       = "$streak day${if (streak != 1) "s" else ""}",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================
// Monthly Reflection Card
// =========================================

@Composable
fun MonthlyReflectionCard(entries: List<JournalEntry>) {
    val monthName  = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
    val moodCounts = remember(entries) { entries.groupingBy { it.mood }.eachCount() }
    val topMood    = remember(moodCounts) { moodCounts.filter { it.key.isNotBlank() }.maxByOrNull { it.value } }
    val writingDays = remember(entries) {
        entries.map { entryDateFmt.format(Date(it.timestamp)) }.toSet().size
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text       = "$monthName in Review",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text  = "${entries.size} entries across $writingDays day${if (writingDays != 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (topMood != null) {
                Text(
                    text  = "Most common mood: ${topMood.key} (${topMood.value}x)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// =========================================
// Journal Entry Card
// =========================================

@Composable
fun JournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit
) {
    val preview = remember(entry) {
        entry.aiSummary?.takeIf { it.isNotBlank() }
            ?: entry.content.take(120)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Mood emoji
            if (entry.mood.isNotBlank()) {
                Text(
                    text  = entry.mood.split(" ").firstOrNull() ?: "📝",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp)
                )
            }
            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Entry date
                Text(
                    text       = entry.timestamp.toDisplayDate(),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = preview,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Indicators row
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!entry.aiSummary.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    "Summary",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    // Bookmark indicator removed (field not available)
                }
            }
        }
    }
}

// =========================================
// Journal Entry Editor
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryEditor(
    entry: JournalEntry?,
    onSave: (content: String, mood: String, summary: String?) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var content          by remember(entry) { mutableStateOf(entry?.content ?: "") }
    var mood             by remember(entry) { mutableStateOf(entry?.mood ?: "") }
    var aiSummary        by remember(entry) { mutableStateOf(entry?.aiSummary) }
    var showPrompt       by remember { mutableStateOf(entry == null) }
    var showDelete       by remember { mutableStateOf(false) }
    var followUpQuestion by remember { mutableStateOf<String?>(null) }
    var isAiLoading      by remember { mutableStateOf(false) }

    val scope   = rememberCoroutineScope()
    val gemini  = remember { GeminiService(BuildConfig.GEMINI_API_KEY) }
    val prompt  = remember { todaysPrompt() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = if (entry == null) "New Entry" else entry.timestamp.toDisplayDate(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete entry")
                        }
                    }
                    FilledTonalButton(
                        onClick  = { onSave(content, mood, aiSummary) },
                        enabled  = content.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save")
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
            modifier            = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Daily prompt banner
            if (showPrompt) {
                item(key = "prompt") {
                    AnimatedVisibility(
                        visible = showPrompt,
                        enter   = fadeIn() + expandVertically(),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier              = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Today's prompt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text       = prompt,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(
                                    onClick = { showPrompt = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss prompt",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Mood picker
            item(key = "mood_picker") {
                Text(
                    "How are you feeling?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MOOD_OPTIONS, key = { it }) { moodOption ->
                        val selected = mood == moodOption
                        FilterChip(
                            selected = selected,
                            onClick  = { mood = if (selected) "" else moodOption },
                            label    = { Text(moodOption) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Text field
            item(key = "content_field") {
                OutlinedTextField(
                    value         = content,
                    onValueChange = { content = it },
                    label         = { Text("Write your thoughts...") },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 240.dp),
                    minLines      = 8,
                    shape         = RoundedCornerShape(12.dp)
                )
            }

            // Action buttons row
            item(key = "action_buttons") {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = { aiSummary = extractSummary(content) },
                        enabled  = content.length > 50,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (!aiSummary.isNullOrBlank()) "Re-summarize" else "Summarize")
                    }

                    FilledTonalButton(
                        onClick  = {
                            if (!isAiLoading && content.length > 80) {
                                isAiLoading      = true
                                followUpQuestion = null
                                scope.launch {
                                    followUpQuestion = gemini.generateFollowUpQuestion(content)
                                    isAiLoading      = false
                                }
                            }
                        },
                        enabled  = content.length > 80 && !isAiLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isAiLoading) {
                            CircularProgressIndicator(
                                modifier  = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color     = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Text("Ask AI")
                        }
                    }
                }
            }

            // AI Follow-up question card
            if (followUpQuestion != null) {
                item(key = "ai_followup") {
                    AnimatedVisibility(
                        visible = followUpQuestion != null,
                        enter   = fadeIn() + expandVertically(spring(Spring.DampingRatioMediumBouncy)),
                        exit    = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier            = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Gemini asks...",
                                            style      = MaterialTheme.typography.labelMedium,
                                            color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick  = { followUpQuestion = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint     = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text  = followUpQuestion ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedButton(
                                    onClick = {
                                        content += "\n\n${followUpQuestion}\n"
                                        followUpQuestion = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Add to entry to answer")
                                }
                            }
                        }
                    }
                }
            }

            // Summary result
            if (!aiSummary.isNullOrBlank()) {
                item(key = "summary") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        "Summary",
                                        style      = MaterialTheme.typography.labelMedium,
                                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick  = { aiSummary = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear summary",
                                        tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text  = aiSummary ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title            = { Text("Delete Entry") },
                text             = { Text("Are you sure you want to delete this entry? This cannot be undone.") },
                confirmButton    = {
                    TextButton(onClick = { showDelete = false; onDelete() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton    = {
                    TextButton(onClick = { showDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}