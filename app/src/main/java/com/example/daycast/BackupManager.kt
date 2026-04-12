package com.example.daycast

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =========================================
// Backup Manager
// Exports all Room data to a JSON file and
// imports it back. No external dependencies.
// =========================================

class BackupManager(
    private val dao: DayCastDao,
    private val context: Context
) {

    // ---- EXPORT ----

    /**
     * Serializes all data to JSON, writes it to cache, and returns
     * a share Intent so the user can send it anywhere (Files, Drive, email, etc.).
     */
    suspend fun createExportIntent(): Intent {
        val json = buildJson()
        val file = writeToCache(json)
        val uri  = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DayCast Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private suspend fun buildJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Notes
        val notesArray = JSONArray()
        dao.getAllNotes().first().forEach { note ->
            val noteObj = JSONObject().apply {
                put("noteId",      note.noteId)
                put("content",     note.content)
                put("tags",        note.tags)
                put("isPinned",    note.isPinned)
                put("folder",      note.folder)
                put("isChecklist", note.isChecklist)
                put("isArchived",  note.isArchived)
                put("isTrashed",   note.isTrashed)
                put("timestamp",   note.timestamp)
            }

            // Inline checklist items for each checklist note
            if (note.isChecklist) {
                val itemsArray = JSONArray()
                dao.getChecklistItemsForNote(note.noteId).first().forEach { item ->
                    itemsArray.put(JSONObject().apply {
                        put("text",      item.text)
                        put("isChecked", item.isChecked)
                        put("position",  item.position)
                    })
                }
                noteObj.put("checklistItems", itemsArray)
            }

            notesArray.put(noteObj)
        }
        root.put("notes", notesArray)

        // Tasks
        val tasksArray = JSONArray()
        dao.getAllTasks().first().forEach { task ->
            tasksArray.put(JSONObject().apply {
                put("title",       task.title)
                put("isCompleted", task.isCompleted)
                put("dueDate",     task.dueDate ?: JSONObject.NULL)
            })
        }
        root.put("tasks", tasksArray)

        // Events
        val eventsArray = JSONArray()
        dao.getAllEvents().first().forEach { event ->
            eventsArray.put(JSONObject().apply {
                put("title",          event.title)
                put("startDateTime",  event.startDateTime)
                put("endDateTime",    event.endDateTime)
                put("recurrenceRule", event.recurrenceRule ?: JSONObject.NULL)
            })
        }
        root.put("events", eventsArray)

        // Journal entries
        val journalArray = JSONArray()
        dao.getAllJournalEntries().first().forEach { entry ->
            journalArray.put(JSONObject().apply {
                put("title",        entry.title)
                put("content",      entry.content)
                put("mood",         entry.mood)
                put("aiSummary",    entry.aiSummary)
                put("aiReflection", entry.aiReflection)
                put("isBookmarked", entry.isBookmarked)
                put("timestamp",    entry.timestamp)
            })
        }
        root.put("journalEntries", journalArray)

        return root.toString(2) // pretty-print with 2-space indent
    }

    private fun writeToCache(json: String): File {
        val backupDir = File(context.cacheDir, "backup")
        backupDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(backupDir, "daycast_backup_$timestamp.json")
        file.writeText(json)
        return file
    }

    // ---- IMPORT ----

    /**
     * Reads a JSON backup file from the given URI and inserts all records
     * into the database. Existing data is kept — this is a merge, not a replace.
     */
    suspend fun importFromUri(uri: Uri): ImportResult {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return ImportResult.Failure("Could not read the file.")

            val root = JSONObject(text)

            var notesImported   = 0
            var tasksImported   = 0
            var eventsImported  = 0
            var journalImported = 0

            // Notes + checklist items
            root.optJSONArray("notes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj  = arr.getJSONObject(i)
                    val note = Note(
                        content     = obj.optString("content"),
                        tags        = obj.optString("tags"),
                        isPinned    = obj.optBoolean("isPinned"),
                        folder      = obj.optString("folder"),
                        isChecklist = obj.optBoolean("isChecklist"),
                        isArchived  = obj.optBoolean("isArchived"),
                        isTrashed   = obj.optBoolean("isTrashed"),
                        timestamp   = obj.optLong("timestamp")
                    )
                    dao.insertNote(note)

                    // Re-fetch the inserted note to get its generated noteId
                    val insertedNote = dao.getAllNotes().first()
                        .firstOrNull { it.timestamp == note.timestamp && it.content == note.content }

                    if (insertedNote != null) {
                        obj.optJSONArray("checklistItems")?.let { items ->
                            for (j in 0 until items.length()) {
                                val itemObj = items.getJSONObject(j)
                                dao.insertChecklistItem(
                                    ChecklistItem(
                                        noteId    = insertedNote.noteId,
                                        text      = itemObj.optString("text"),
                                        isChecked = itemObj.optBoolean("isChecked"),
                                        position  = itemObj.optInt("position", j)
                                    )
                                )
                            }
                        }
                    }
                    notesImported++
                }
            }

            // Tasks
            root.optJSONArray("tasks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    dao.insertTask(Task(
                        title       = obj.optString("title"),
                        isCompleted = obj.optBoolean("isCompleted"),
                        dueDate     = if (obj.isNull("dueDate")) null else obj.optLong("dueDate")
                    ))
                    tasksImported++
                }
            }

            // Events
            root.optJSONArray("events")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    dao.insertEvent(Event(
                        title          = obj.optString("title"),
                        startDateTime  = obj.optLong("startDateTime"),
                        endDateTime    = obj.optLong("endDateTime"),
                        recurrenceRule = if (obj.isNull("recurrenceRule")) null
                        else obj.optString("recurrenceRule")
                    ))
                    eventsImported++
                }
            }

            // Journal entries
            root.optJSONArray("journalEntries")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    dao.insertJournalEntry(JournalEntry(
                        title        = obj.optString("title"),
                        content      = obj.optString("content"),
                        mood         = obj.optString("mood"),
                        aiSummary    = obj.optString("aiSummary"),
                        aiReflection = obj.optString("aiReflection"),
                        isBookmarked = obj.optBoolean("isBookmarked"),
                        timestamp    = obj.optLong("timestamp")
                    ))
                    journalImported++
                }
            }

            ImportResult.Success(
                notes   = notesImported,
                tasks   = tasksImported,
                events  = eventsImported,
                journal = journalImported
            )
        } catch (e: Exception) {
            ImportResult.Failure(e.message ?: "Unknown error during import.")
        }
    }
}

// ---- Result types ----

sealed class ImportResult {
    data class Success(
        val notes: Int,
        val tasks: Int,
        val events: Int,
        val journal: Int
    ) : ImportResult()

    data class Failure(val message: String) : ImportResult()
}