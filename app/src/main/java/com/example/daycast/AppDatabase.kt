package com.example.daycast

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// =========================================
// Entities
// =========================================

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val noteId: Int = 0,
    val content: String,
    val tags: String = "",
    val isPinned: Boolean = false,
    val folder: String = "",
    val isChecklist: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    // Use 0L here — KSP can't process method call defaults like System.currentTimeMillis().
    // The real timestamp is set explicitly in DayCastViewModel.addNote().
    val timestamp: Long = 0L
)

@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val itemId: Int = 0,
    val noteId: Int,
    val text: String,
    val isChecked: Boolean = false,
    val position: Int = 0
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val taskId: Int = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val dueDate: Long? = null
)

@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val eventId: Int = 0,
    val title: String,
    val startDateTime: Long,
    val endDateTime: Long,
    val recurrenceRule: String? = null
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val entryId: Int = 0,
    val title: String = "",
    val content: String,
    val mood: String = "",
    val aiSummary: String = "",
    val aiReflection: String = "",
    val isBookmarked: Boolean = false,
    val timestamp: Long = 0L
)

// =========================================
// DAO
// =========================================

@Dao
interface DayCastDao {

    // Notes — active (not archived, not trashed)
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrashed = 0 ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    // Archived notes
    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY timestamp DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    // Trashed notes
    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY timestamp DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    // Checklist Items
    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId ORDER BY position ASC")
    fun getChecklistItemsForNote(noteId: Int): Flow<List<ChecklistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItem)

    @Update
    suspend fun updateChecklistItem(item: ChecklistItem)

    @Delete
    suspend fun deleteChecklistItem(item: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE noteId = :noteId")
    suspend fun deleteAllChecklistItemsForNote(noteId: Int)

    // Tasks
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Events
    @Query("SELECT * FROM events ORDER BY startDateTime ASC")
    fun getAllEvents(): Flow<List<Event>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event)

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    // Journal
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry)

    @Update
    suspend fun updateJournalEntry(entry: JournalEntry)

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntry)

    @Query("SELECT * FROM journal_entries WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedJournalEntries(): Flow<List<JournalEntry>>

    @Query("UPDATE journal_entries SET isBookmarked = :bookmarked WHERE entryId = :entryId")
    suspend fun setJournalBookmark(entryId: Int, bookmarked: Boolean)
}

// =========================================
// Database
// =========================================

@Database(
    entities = [Note::class, ChecklistItem::class, Task::class, Event::class, JournalEntry::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dayCastDao(): DayCastDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daycast_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}