package com.example.daycast

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

// =========================================
// FirebaseSync
// Handles Firebase Auth (email/password + Google Sign-In)
// and Firestore sync for all Room entities.
//
// Data structure in Firestore:
//   users/{uid}/notes/{noteId}
//   users/{uid}/tasks/{taskId}
//   users/{uid}/events/{eventId}
//   users/{uid}/journal/{entryId}
// =========================================

class FirebaseSync {

    private val auth = Firebase.auth
    private val db   = Firebase.firestore

    // ---- Auth state ----

    val currentUser: FirebaseUser? get() = auth.currentUser
    val isSignedIn: Boolean        get() = auth.currentUser != null
    val userEmail: String?         get() = auth.currentUser?.email

    // ---- Email / Password Auth ----

    suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createAccount(email: String, password: String): Result<Unit> {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---- Google Sign-In ----
    // Takes a Google ID token obtained via Credential Manager
    // and exchanges it for a Firebase credential.

    suspend fun signInWithGoogleToken(idToken: String): Result<Unit> {
        return try {
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = auth.signOut()

    // ---- Helpers ----

    private fun userRoot() = db.collection("users").document(currentUser!!.uid)

    // ---- Individual push operations (fire-and-forget, called after each local write) ----

    fun pushNote(note: Note) {
        if (!isSignedIn) return
        userRoot().collection("notes").document(note.noteId.toString())
            .set(mapOf(
                "noteId"      to note.noteId,
                "content"     to note.content,
                "tags"        to note.tags,
                "isPinned"    to note.isPinned,
                "folder"      to note.folder,
                "isChecklist" to note.isChecklist,
                "isArchived"  to note.isArchived,
                "isTrashed"   to note.isTrashed,
                "timestamp"   to note.timestamp
            ))
    }

    fun deleteNote(noteId: Int) {
        if (!isSignedIn) return
        userRoot().collection("notes").document(noteId.toString()).delete()
    }

    fun pushTask(task: Task) {
        if (!isSignedIn) return
        userRoot().collection("tasks").document(task.taskId.toString())
            .set(mapOf(
                "taskId"      to task.taskId,
                "title"       to task.title,
                "isCompleted" to task.isCompleted,
                "dueDate"     to task.dueDate
            ))
    }

    fun deleteTask(taskId: Int) {
        if (!isSignedIn) return
        userRoot().collection("tasks").document(taskId.toString()).delete()
    }

    fun pushEvent(event: Event) {
        if (!isSignedIn) return
        userRoot().collection("events").document(event.eventId.toString())
            .set(mapOf(
                "eventId"        to event.eventId,
                "title"          to event.title,
                "startDateTime"  to event.startDateTime,
                "endDateTime"    to event.endDateTime,
                "recurrenceRule" to event.recurrenceRule
            ))
    }

    fun deleteEvent(eventId: Int) {
        if (!isSignedIn) return
        userRoot().collection("events").document(eventId.toString()).delete()
    }

    fun pushJournalEntry(entry: JournalEntry) {
        if (!isSignedIn) return
        userRoot().collection("journal").document(entry.entryId.toString())
            .set(mapOf(
                "entryId"       to entry.entryId,
                "title"         to entry.title,
                "content"       to entry.content,
                "mood"          to entry.mood,
                "aiSummary"     to entry.aiSummary,
                "aiReflection"  to entry.aiReflection,
                "isBookmarked"  to entry.isBookmarked,
                "timestamp"     to entry.timestamp
            ))
    }

    fun deleteJournalEntry(entryId: Int) {
        if (!isSignedIn) return
        userRoot().collection("journal").document(entryId.toString()).delete()
    }

    // ---- Full upload: push all local Room data to Firestore ----

    suspend fun uploadAll(dao: DayCastDao): SyncResult {
        if (!isSignedIn) return SyncResult.NotSignedIn
        return try {
            val notes   = dao.getAllNotes().first()
            val tasks   = dao.getAllTasks().first()
            val events  = dao.getAllEvents().first()
            val journal = dao.getAllJournalEntries().first()

            notes.forEach   { pushNote(it) }
            tasks.forEach   { pushTask(it) }
            events.forEach  { pushEvent(it) }
            journal.forEach { pushJournalEntry(it) }

            SyncResult.Success(
                notes   = notes.size,
                tasks   = tasks.size,
                events  = events.size,
                journal = journal.size
            )
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Upload failed")
        }
    }

    // ---- Full download: pull all Firestore data into Room ----

    suspend fun downloadAll(dao: DayCastDao): SyncResult {
        if (!isSignedIn) return SyncResult.NotSignedIn
        return try {
            var notes   = 0
            var tasks   = 0
            var events  = 0
            var journal = 0

            // Notes
            val noteDocs = userRoot().collection("notes").get().await()
            for (doc in noteDocs) {
                dao.insertNote(Note(
                    noteId      = (doc.getLong("noteId") ?: 0).toInt(),
                    content     = doc.getString("content") ?: "",
                    tags        = doc.getString("tags") ?: "",
                    isPinned    = doc.getBoolean("isPinned") ?: false,
                    folder      = doc.getString("folder") ?: "",
                    isChecklist = doc.getBoolean("isChecklist") ?: false,
                    isArchived  = doc.getBoolean("isArchived") ?: false,
                    isTrashed   = doc.getBoolean("isTrashed") ?: false,
                    timestamp   = doc.getLong("timestamp") ?: 0L
                ))
                notes++
            }

            // Tasks
            val taskDocs = userRoot().collection("tasks").get().await()
            for (doc in taskDocs) {
                dao.insertTask(Task(
                    taskId      = (doc.getLong("taskId") ?: 0).toInt(),
                    title       = doc.getString("title") ?: "",
                    isCompleted = doc.getBoolean("isCompleted") ?: false,
                    dueDate     = doc.getLong("dueDate")
                ))
                tasks++
            }

            // Events
            val eventDocs = userRoot().collection("events").get().await()
            for (doc in eventDocs) {
                dao.insertEvent(Event(
                    eventId        = (doc.getLong("eventId") ?: 0).toInt(),
                    title          = doc.getString("title") ?: "",
                    startDateTime  = doc.getLong("startDateTime") ?: 0L,
                    endDateTime    = doc.getLong("endDateTime") ?: 0L,
                    recurrenceRule = doc.getString("recurrenceRule")
                ))
                events++
            }

            // Journal
            val journalDocs = userRoot().collection("journal").get().await()
            for (doc in journalDocs) {
                dao.insertJournalEntry(JournalEntry(
                    entryId      = (doc.getLong("entryId") ?: 0).toInt(),
                    title        = doc.getString("title") ?: "",
                    content      = doc.getString("content") ?: "",
                    mood         = doc.getString("mood") ?: "",
                    aiSummary    = doc.getString("aiSummary") ?: "",
                    aiReflection = doc.getString("aiReflection") ?: "",
                    isBookmarked = doc.getBoolean("isBookmarked") ?: false,
                    timestamp    = doc.getLong("timestamp") ?: 0L
                ))
                journal++
            }

            SyncResult.Success(notes, tasks, events, journal)
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "Download failed")
        }
    }
}

// ---- Result types ----

sealed class SyncResult {
    data class Success(
        val notes: Int,
        val tasks: Int,
        val events: Int,
        val journal: Int
    ) : SyncResult()
    data class Failure(val message: String) : SyncResult()
    object NotSignedIn : SyncResult()
}