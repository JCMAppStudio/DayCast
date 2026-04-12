package com.example.daycast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// =========================================
// ViewModel
// Every local write is mirrored to Firestore
// automatically (fire-and-forget) if the user
// is signed in. No writes are blocked by sync.
// =========================================

class DayCastViewModel(
    private val dao:  DayCastDao,
    private val sync: FirebaseSync = FirebaseSync()
) : ViewModel() {

    val allNotes: StateFlow<List<Note>> = dao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedNotes: StateFlow<List<Note>> = dao.getArchivedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashedNotes: StateFlow<List<Note>> = dao.getTrashedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allEvents: StateFlow<List<Event>> = dao.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allJournalEntries: StateFlow<List<JournalEntry>> = dao.getAllJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Expose auth state so the UI can show sign-in prompts
    val firebaseSync: FirebaseSync get() = sync

    // ---- Notes ----

    fun addNote(content: String, tags: String = "", folder: String = "", isChecklist: Boolean = false) {
        viewModelScope.launch {
            val note = Note(
                content     = content,
                tags        = tags,
                folder      = folder,
                isChecklist = isChecklist,
                timestamp   = System.currentTimeMillis()
            )
            dao.insertNote(note)
            // Re-fetch to get the generated noteId before syncing
            val saved = dao.getAllNotes().stateIn(viewModelScope).value
                .firstOrNull { it.content == content && it.timestamp == note.timestamp }
            saved?.let { sync.pushNote(it) }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            dao.updateNote(note)
            sync.pushNote(note)
        }
    }

    fun deleteNote(note: Note) {
        // Soft-delete: move to trash instead of permanent deletion
        trashNote(note)
    }

    fun togglePin(note: Note) {
        val updated = note.copy(isPinned = !note.isPinned)
        viewModelScope.launch {
            dao.updateNote(updated)
            sync.pushNote(updated)
        }
    }

    fun archiveNote(note: Note) {
        val updated = note.copy(isArchived = true, isPinned = false)
        viewModelScope.launch {
            dao.updateNote(updated)
            sync.pushNote(updated)
        }
    }

    fun unarchiveNote(note: Note) {
        val updated = note.copy(isArchived = false)
        viewModelScope.launch {
            dao.updateNote(updated)
            sync.pushNote(updated)
        }
    }

    fun trashNote(note: Note) {
        val updated = note.copy(isTrashed = true, isPinned = false)
        viewModelScope.launch {
            dao.updateNote(updated)
            sync.pushNote(updated)
        }
    }

    fun restoreNote(note: Note) {
        val updated = note.copy(isTrashed = false)
        viewModelScope.launch {
            dao.updateNote(updated)
            sync.pushNote(updated)
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch {
            dao.deleteAllChecklistItemsForNote(note.noteId)
            dao.deleteNote(note)
            sync.deleteNote(note.noteId)
        }
    }

    // ---- Checklist Items ----

    fun getChecklistItemsForNote(noteId: Int): Flow<List<ChecklistItem>> =
        dao.getChecklistItemsForNote(noteId)

    fun addChecklistItem(noteId: Int, text: String, position: Int) {
        viewModelScope.launch {
            dao.insertChecklistItem(ChecklistItem(noteId = noteId, text = text, position = position))
        }
    }

    fun updateChecklistItem(item: ChecklistItem) {
        viewModelScope.launch { dao.updateChecklistItem(item) }
    }

    fun deleteChecklistItem(item: ChecklistItem) {
        viewModelScope.launch { dao.deleteChecklistItem(item) }
    }

    fun toggleChecklistItem(item: ChecklistItem) {
        viewModelScope.launch {
            dao.updateChecklistItem(item.copy(isChecked = !item.isChecked))
        }
    }

    // ---- Tasks ----

    fun addTask(title: String, dueDate: Long? = null) {
        viewModelScope.launch {
            val task = Task(title = title, dueDate = dueDate)
            dao.insertTask(task)
            val saved = dao.getAllTasks().stateIn(viewModelScope).value
                .firstOrNull { it.title == title && it.dueDate == dueDate }
            saved?.let { sync.pushTask(it) }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            dao.updateTask(task)
            sync.pushTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            sync.deleteTask(task.taskId)
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val updated = task.copy(isCompleted = !task.isCompleted)
        viewModelScope.launch {
            dao.updateTask(updated)
            sync.pushTask(updated)
        }
    }

    // ---- Events ----

    fun addEvent(title: String, startDateTime: Long, endDateTime: Long, recurrenceRule: String? = null) {
        viewModelScope.launch {
            val event = Event(
                title          = title,
                startDateTime  = startDateTime,
                endDateTime    = endDateTime,
                recurrenceRule = recurrenceRule
            )
            dao.insertEvent(event)
            val saved = dao.getAllEvents().stateIn(viewModelScope).value
                .firstOrNull { it.title == title && it.startDateTime == startDateTime }
            saved?.let { sync.pushEvent(it) }
        }
    }

    fun updateEvent(event: Event) {
        viewModelScope.launch {
            dao.updateEvent(event)
            sync.pushEvent(event)
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            dao.deleteEvent(event)
            sync.deleteEvent(event.eventId)
        }
    }

    // ---- Journal ----

    fun addJournalEntry(
        content: String,
        mood: String = "",
        aiSummary: String = "",
        title: String = "",
        aiReflection: String = ""
    ) {
        viewModelScope.launch {
            val entry = JournalEntry(
                title         = title,
                content       = content,
                mood          = mood,
                aiSummary     = aiSummary,
                aiReflection  = aiReflection,
                timestamp     = System.currentTimeMillis()
            )
            dao.insertJournalEntry(entry)
            val saved = dao.getAllJournalEntries().stateIn(viewModelScope).value
                .firstOrNull { it.content == content && it.timestamp == entry.timestamp }
            saved?.let { sync.pushJournalEntry(it) }
        }
    }

    fun updateJournalEntry(entry: JournalEntry) {
        viewModelScope.launch {
            dao.updateJournalEntry(entry)
            sync.pushJournalEntry(entry)
        }
    }

    fun deleteJournalEntry(entry: JournalEntry) {
        viewModelScope.launch {
            dao.deleteJournalEntry(entry)
            sync.deleteJournalEntry(entry.entryId)
        }
    }

    fun toggleJournalBookmark(entry: JournalEntry) {
        viewModelScope.launch {
            val updated = entry.copy(isBookmarked = !entry.isBookmarked)
            dao.updateJournalEntry(updated)
            sync.pushJournalEntry(updated)
        }
    }
}

// =========================================
// ViewModel Factory
// =========================================

class DayCastViewModelFactory(private val dao: DayCastDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DayCastViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DayCastViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}