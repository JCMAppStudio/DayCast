package com.example.daycast

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// =========================================
// Enums + Helpers
// =========================================

enum class ViewMode { MONTH, WEEK }

private val MONTH_NAMES = listOf(
    "January","February","March","April","May","June",
    "July","August","September","October","November","December"
)
private val DAY_HEADERS = listOf("Su","Mo","Tu","We","Th","Fr","Sa")
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val fullDateFormat = SimpleDateFormat("EEEE, MMMM d yyyy", Locale.getDefault())
private val weekDayLabelFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
private val weekRangeStartFmt = SimpleDateFormat("MMM d", Locale.getDefault())
private val weekRangeEndFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

// Event color palette (Google Calendar-style)
private val EventColors = listOf(
    Color(0xFF4285F4), // Blue
    Color(0xFFEA4335), // Red
    Color(0xFFFBBC04), // Yellow
    Color(0xFF34A853), // Green
    Color(0xFF8E24AA), // Purple
    Color(0xFFE67C73), // Flamingo
    Color(0xFF039BE5), // Cyan
    Color(0xFF616161), // Graphite
)

private fun eventColorForTitle(title: String): Color {
    val hash = title.hashCode().let { if (it < 0) -it else it }
    return EventColors[hash % EventColors.size]
}

private fun weekStartEnd(): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    return start to end
}

private fun getWeekStart(timestamp: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun isToday(timestamp: Long): Boolean {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
}

private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
    Calendar.getInstance().apply { set(year, month, day, 9, 0, 0); set(Calendar.MILLISECOND, 0) }

// =========================================
// Root Screen - Permission Gate
// =========================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PlannerAdaptiveScreen(viewModel: DayCastViewModel, onSettingsClick: () -> Unit = {}) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> hasPermission = perms.values.all { it } }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    if (!hasPermission) {
        CalendarPermissionRationale(onRequest = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        })
    } else {
        PlannerContent(viewModel = viewModel, onSettingsClick = onSettingsClick)
    }
}

@Composable
private fun CalendarPermissionRationale(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Calendar Access Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "DayCast needs access to your device calendar to display and create events that sync with Google Calendar.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onRequest,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Access")
            }
        }
    }
}

// =========================================
// Main Planner Content
// =========================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PlannerContent(viewModel: DayCastViewModel, onSettingsClick: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { CalendarRepository(context) }
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>()

    val todayCal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }
    var viewMode by remember { mutableStateOf(ViewMode.MONTH) }
    var currentWeekStart by remember { mutableStateOf(getWeekStart(System.currentTimeMillis())) }

    var monthEvents by remember { mutableStateOf<Map<Int, List<CalendarEvent>>>(emptyMap()) }
    var dayEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var weekEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isLoadingMonth by remember { mutableStateOf(false) }

    val allTasks by viewModel.allTasks.collectAsState()
    val (weekStart, weekEnd) = remember { weekStartEnd() }
    val weeklyTasks = remember(allTasks, weekStart, weekEnd) {
        allTasks.filter { t -> t.dueDate != null && t.dueDate in weekStart..weekEnd }
    }
    val activeTasks = remember(weeklyTasks) { weeklyTasks.filter { !it.isCompleted } }
    val completedTasks = remember(weeklyTasks) { weeklyTasks.filter { it.isCompleted } }

    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToReschedule by remember { mutableStateOf<Task?>(null) }
    val selectedDay = navigator.currentDestination?.content as? Int

    LaunchedEffect(currentYear, currentMonth) {
        isLoadingMonth = true
        monthEvents = withContext(Dispatchers.IO) { repo.getEventsForMonth(currentYear, currentMonth) }
        isLoadingMonth = false
    }

    LaunchedEffect(currentYear, currentMonth, selectedDay) {
        if (selectedDay != null) {
            dayEvents = withContext(Dispatchers.IO) {
                repo.getEventsForDay(currentYear, currentMonth, selectedDay)
            }
        }
    }

    LaunchedEffect(currentWeekStart, viewMode) {
        if (viewMode == ViewMode.WEEK) {
            weekEvents = withContext(Dispatchers.IO) {
                repo.getEventsForDateRange(
                    currentWeekStart,
                    currentWeekStart + 7 * 24 * 60 * 60 * 1000L
                )
            }
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp)
            ) {
                // Row 1: Title + Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Planner",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
                // Row 2: Today + Month/Week toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        val today = Calendar.getInstance()
                        currentYear = today.get(Calendar.YEAR)
                        currentMonth = today.get(Calendar.MONTH)
                        currentWeekStart = getWeekStart(System.currentTimeMillis())
                    }) {
                        Text(
                            "Today",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = viewMode == ViewMode.MONTH,
                            onClick = { viewMode = ViewMode.MONTH },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Month") }
                        SegmentedButton(
                            selected = viewMode == ViewMode.WEEK,
                            onClick = { viewMode = ViewMode.WEEK },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Week") }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.CheckCircle, contentDescription = "Add Task") }
                FloatingActionButton(
                    onClick = { showAddEventDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Add, contentDescription = "Add Event") }
            }
        }
    ) { innerPadding ->
        when (viewMode) {
            ViewMode.MONTH -> NavigableListDetailPaneScaffold(
                navigator = navigator,
                modifier = Modifier.padding(innerPadding),
                listPane = {
                    CalendarListPane(
                        year = currentYear, month = currentMonth,
                        todayYear = todayCal.get(Calendar.YEAR),
                        todayMonth = todayCal.get(Calendar.MONTH),
                        todayDay = todayCal.get(Calendar.DAY_OF_MONTH),
                        selectedDay = selectedDay,
                        monthEvents = monthEvents,
                        isLoading = isLoadingMonth,
                        activeTasks = activeTasks,
                        completedTasks = completedTasks,
                        onDaySelected = { day ->
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, day) }
                        },
                        onPreviousMonth = {
                            if (currentMonth == 0) { currentMonth = 11; currentYear-- } else currentMonth--
                        },
                        onNextMonth = {
                            if (currentMonth == 11) { currentMonth = 0; currentYear++ } else currentMonth++
                        },
                        onToggleTask = { viewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onRescheduleTask = { taskToReschedule = it }
                    )
                },
                detailPane = {
                    AgendaDetailPane(
                        year = currentYear, month = currentMonth, day = selectedDay,
                        events = dayEvents, allTasks = allTasks,
                        onDeleteEvent = { eventId ->
                            scope.launch(Dispatchers.IO) { repo.deleteEvent(eventId) }
                            dayEvents = dayEvents.filter { it.id != eventId }
                        },
                        onToggleTask = { viewModel.toggleTaskCompletion(it) },
                        onDeleteTask = { viewModel.deleteTask(it) }
                    )
                }
            )

            ViewMode.WEEK -> WeekViewPane(
                modifier = Modifier.padding(innerPadding),
                weekStart = currentWeekStart,
                allTasks = allTasks,
                weekEvents = weekEvents,
                onPrevWeek = { currentWeekStart -= 7 * 24 * 60 * 60 * 1000L },
                onNextWeek = { currentWeekStart += 7 * 24 * 60 * 60 * 1000L },
                onToggleTask = { viewModel.toggleTaskCompletion(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                onRescheduleTask = { taskToReschedule = it }
            )
        }
    }

    // Add Event Dialog
    if (showAddEventDialog) {
        AddEventDialog(
            preselectedYear = currentYear,
            preselectedMonth = currentMonth,
            preselectedDay = selectedDay ?: todayCal.get(Calendar.DAY_OF_MONTH),
            onDismiss = { showAddEventDialog = false },
            onConfirm = { title, description, startTime, endTime, allDay, recurrence ->
                scope.launch(Dispatchers.IO) {
                    repo.insertEvent(title, description, startTime, endTime, allDay, recurrence)
                    monthEvents = repo.getEventsForMonth(currentYear, currentMonth)
                    if (selectedDay != null) {
                        dayEvents = repo.getEventsForDay(currentYear, currentMonth, selectedDay)
                    }
                    if (viewMode == ViewMode.WEEK) {
                        weekEvents = repo.getEventsForDateRange(
                            currentWeekStart,
                            currentWeekStart + 7 * 24 * 60 * 60 * 1000L
                        )
                    }
                }
                showAddEventDialog = false
            }
        )
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            onDismiss = { showAddTaskDialog = false },
            onConfirm = { title ->
                val endOfWeek = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                }.timeInMillis
                viewModel.addTask(title = title, dueDate = endOfWeek)
                showAddTaskDialog = false
            }
        )
    }

    taskToReschedule?.let { task ->
        RescheduleDateDialog(
            task = task,
            onDismiss = { taskToReschedule = null },
            onConfirm = { newDate ->
                viewModel.updateTask(task.copy(dueDate = newDate))
                taskToReschedule = null
            }
        )
    }
}

// =========================================
// Week View (Google Calendar style)
// =========================================

@Composable
private fun WeekViewPane(
    modifier: Modifier = Modifier,
    weekStart: Long,
    allTasks: List<Task>,
    weekEvents: List<CalendarEvent>,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRescheduleTask: (Task) -> Unit
) {
    val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000L
    val startLabel = weekRangeStartFmt.format(Date(weekStart))
    val endLabel = weekRangeEndFmt.format(Date(weekEnd - 1))

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Week navigation header
        item(key = "week_header") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevWeek) {
                        Icon(Icons.Default.ChevronLeft, "Previous week")
                    }
                    Text(
                        "$startLabel - $endLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.Default.ChevronRight, "Next week")
                    }
                }
            }
        }

        // Day cards
        items(7, key = { "week_day_$it" }) { dayOffset ->
            val dayStart = weekStart + dayOffset * 24 * 60 * 60 * 1000L
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
            val todayFlag = isToday(dayStart)
            val dayEventsForDay = remember(weekEvents, dayStart) { weekEvents.filter { it.startTime in dayStart..dayEnd } }
            val dayTasksForDay = remember(allTasks, dayStart) { allTasks.filter { t -> t.dueDate != null && t.dueDate in dayStart..dayEnd } }

            WeekDayCard(
                timestamp = dayStart,
                isToday = todayFlag,
                events = dayEventsForDay,
                tasks = dayTasksForDay,
                onToggleTask = onToggleTask,
                onDeleteTask = onDeleteTask,
                onRescheduleTask = onRescheduleTask
            )
        }
    }
}

@Composable
private fun WeekDayCard(
    timestamp: Long,
    isToday: Boolean,
    events: List<CalendarEvent>,
    tasks: List<Task>,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRescheduleTask: (Task) -> Unit
) {
    val label = weekDayLabelFmt.format(Date(timestamp))
    val hasContent = events.isNotEmpty() || tasks.isNotEmpty()
    var expanded by remember { mutableStateOf(isToday || hasContent) }

    val containerColor by animateColorAsState(
        targetValue = if (isToday)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "dayCardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isToday)
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (isToday) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasContent) {
                        Text(
                            "${events.size + tasks.size} item${if (events.size + tasks.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
            ) {
                Column {
                    if (!hasContent) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Nothing scheduled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                    } else {
                        if (events.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            events.forEach { event ->
                                AgendaEventCard(event = event, onDelete = null)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        if (tasks.isNotEmpty()) {
                            if (events.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            } else {
                                Spacer(Modifier.height(6.dp))
                            }
                            tasks.forEach { task ->
                                TaskRow(
                                    task = task,
                                    onToggle = { onToggleTask(task) },
                                    onDelete = { onDeleteTask(task) },
                                    onReschedule = { onRescheduleTask(task) }
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
// Calendar List Pane (Month View)
// =========================================

@Composable
private fun CalendarListPane(
    year: Int, month: Int,
    todayYear: Int, todayMonth: Int, todayDay: Int,
    selectedDay: Int?,
    monthEvents: Map<Int, List<CalendarEvent>>,
    isLoading: Boolean,
    activeTasks: List<Task>,
    completedTasks: List<Task>,
    onDaySelected: (Int) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRescheduleTask: (Task) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
        item(key = "month_header") {
            MonthHeader(year = year, month = month, onPrevious = onPreviousMonth, onNext = onNextMonth)
        }
        item(key = "month_grid") {
            MonthGrid(
                year = year, month = month,
                todayYear = todayYear, todayMonth = todayMonth, todayDay = todayDay,
                selectedDay = selectedDay, eventsByDay = monthEvents,
                isLoading = isLoading, onDaySelected = onDaySelected
            )
        }
        item(key = "weekly_goals_section") {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            WeeklyGoalsHeader(
                totalTasks = activeTasks.size + completedTasks.size,
                completedCount = completedTasks.size
            )
        }
        if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
            item(key = "no_tasks") {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No tasks this week. Tap + to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        items(activeTasks, key = { it.taskId }) { task ->
            TaskRow(
                task = task,
                onToggle = { onToggleTask(task) },
                onDelete = { onDeleteTask(task) },
                onReschedule = { onRescheduleTask(task) }
            )
        }
        if (completedTasks.isNotEmpty()) {
            item(key = "completed_header") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Completed (${completedTasks.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            items(completedTasks, key = { "done_${it.taskId}" }) { task ->
                TaskRow(task = task, onToggle = { onToggleTask(task) }, onDelete = { onDeleteTask(task) })
            }
        }
    }
}

// =========================================
// Month Grid Components
// =========================================

@Composable
private fun MonthHeader(year: Int, month: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, "Previous month")
        }
        Text(
            "${MONTH_NAMES[month]} $year",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, "Next month")
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int, month: Int,
    todayYear: Int, todayMonth: Int, todayDay: Int,
    selectedDay: Int?,
    eventsByDay: Map<Int, List<CalendarEvent>>,
    isLoading: Boolean,
    onDaySelected: (Int) -> Unit
) {
    val firstDayCal = remember(year, month) { Calendar.getInstance().apply { set(year, month, 1) } }
    val firstDayOfWeek = remember(firstDayCal) { firstDayCal.get(Calendar.DAY_OF_WEEK) - 1 }
    val daysInMonth = remember(firstDayCal) { firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH) }

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_HEADERS.forEach { header ->
                Text(
                    header,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            return@Column
        }

        val cells = remember(firstDayOfWeek, daysInMonth) {
            (0 until 42).map { index ->
                val dayNumber = index - firstDayOfWeek + 1
                if (dayNumber in 1..daysInMonth) dayNumber else null
            }
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                isToday = day == todayDay && month == todayMonth && year == todayYear,
                                isSelected = day == selectedDay,
                                events = eventsByDay[day] ?: emptyList(),
                                onClick = { onDaySelected(day) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int, isToday: Boolean, isSelected: Boolean,
    events: List<CalendarEvent>, onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        label = "dayCellBg"
    )
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() }
            .size(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (events.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                events.take(3).forEach { event ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else eventColorForTitle(event.title)
                            )
                    )
                }
            }
        }
    }
}

// =========================================
// Weekly Goals Section
// =========================================

@Composable
private fun WeeklyGoalsHeader(totalTasks: Int, completedCount: Int) {
    val progress = if (totalTasks > 0) completedCount.toFloat() / totalTasks else 0f

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Weekly Goals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (totalTasks > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "$completedCount / $totalTasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        if (totalTasks > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onReschedule: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Text(
            text = task.title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            color = if (task.isCompleted)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (onReschedule != null) {
            IconButton(onClick = onReschedule, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = "Reschedule",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete task",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// =========================================
// Agenda Detail Pane
// =========================================

@Composable
private fun AgendaDetailPane(
    year: Int, month: Int, day: Int?,
    events: List<CalendarEvent>, allTasks: List<Task>,
    onDeleteEvent: (Long) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit
) {
    if (day == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tap a day to see its agenda.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    val dayStart = Calendar.getInstance().apply {
        set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayEnd = Calendar.getInstance().apply {
        set(year, month, day, 23, 59, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis
    val dayTasks = remember(allTasks, day) {
        allTasks.filter { t -> t.dueDate != null && t.dueDate in dayStart..dayEnd }
    }
    val dateLabel = remember(year, month, day) {
        fullDateFormat.format(calendarOf(year, month, day).time)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "agenda_header") {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
            item(key = "events_label") {
                Text(
                    "Events",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (events.isEmpty()) {
                item(key = "no_events") {
                    Text(
                        "No events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                items(events, key = { it.id }) { event ->
                    AgendaEventCard(event = event, onDelete = { onDeleteEvent(event.id) })
                }
            }
            item(key = "tasks_label") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Text(
                    "Tasks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (dayTasks.isEmpty()) {
                item(key = "no_tasks_agenda") {
                    Text(
                        "No tasks due today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                items(dayTasks, key = { "agenda_task_${it.taskId}" }) { task ->
                    TaskRow(task = task, onToggle = { onToggleTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }
        }
    }
}

@Composable
private fun AgendaEventCard(event: CalendarEvent, onDelete: (() -> Unit)?) {
    val eventColor = remember(event.title) { eventColorForTitle(event.title) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(eventColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.allDay) {
                    Text(
                        "All day",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${timeFormat.format(Date(event.startTime))} - ${timeFormat.format(Date(event.endTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete event",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// =========================================
// Reschedule Date Dialog
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDateDialog(
    task: Task,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = task.dueDate ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = millis
                        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }
                    onConfirm(cal.timeInMillis)
                }
            }) { Text("Move Task") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState, title = {
            Text(
                "Reschedule: ${task.title}",
                modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                style = MaterialTheme.typography.titleSmall
            )
        })
    }
}

// =========================================
// Add Event Dialog
// =========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    preselectedYear: Int,
    preselectedMonth: Int,
    preselectedDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, startTime: Long, endTime: Long, allDay: Boolean, recurrence: RecurrenceRule) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf(RecurrenceRule.NONE) }
    var showRecurrenceMenu by remember { mutableStateOf(false) }
    val startState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = false)
    val endState = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("All day", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Repeat", style = MaterialTheme.typography.bodyMedium)
                    Box {
                        OutlinedButton(
                            onClick = { showRecurrenceMenu = true },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(recurrence.label, style = MaterialTheme.typography.bodySmall)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showRecurrenceMenu,
                            onDismissRequest = { showRecurrenceMenu = false }
                        ) {
                            RecurrenceRule.entries.forEach { rule ->
                                DropdownMenuItem(
                                    text = { Text(rule.label) },
                                    leadingIcon = if (recurrence == rule) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    onClick = { recurrence = rule; showRecurrenceMenu = false }
                                )
                            }
                        }
                    }
                }

                if (!allDay) {
                    Text("Start time", style = MaterialTheme.typography.labelMedium)
                    TimeInput(state = startState)
                    Text("End time", style = MaterialTheme.typography.labelMedium)
                    TimeInput(state = endState)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) return@TextButton
                val startCal = calendarOf(preselectedYear, preselectedMonth, preselectedDay).apply {
                    if (!allDay) { set(Calendar.HOUR_OF_DAY, startState.hour); set(Calendar.MINUTE, startState.minute) }
                    else { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
                }
                val endCal = calendarOf(preselectedYear, preselectedMonth, preselectedDay).apply {
                    if (!allDay) { set(Calendar.HOUR_OF_DAY, endState.hour); set(Calendar.MINUTE, endState.minute) }
                    else { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                }
                onConfirm(title, description, startCal.timeInMillis, endCal.timeInMillis, allDay, recurrence)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// =========================================
// Add Task Dialog
// =========================================

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (title: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Weekly Goal") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What do you want to accomplish?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (title.isBlank()) return@TextButton; onConfirm(title) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}