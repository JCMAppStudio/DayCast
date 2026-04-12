package com.example.daycast

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

// =========================================
// Gemini API Service
// Wraps the Google AI SDK for journal
// follow-up question generation.
// =========================================

class GeminiService(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",   // fast, free-tier friendly
        apiKey    = apiKey,
        generationConfig = generationConfig {
            maxOutputTokens = 120
            temperature     = 0.85f
        }
    )

    /**
     * Given a journal entry, returns a single thoughtful follow-up question
     * to help the user reflect more deeply. Falls back to a local question
     * if the API call fails (no internet, quota exceeded, etc.).
     */
    suspend fun generateFollowUpQuestion(journalEntry: String): String {
        if (journalEntry.isBlank()) return fallback()

        val prompt = """
            You are a warm, compassionate journaling companion.

            Read this journal entry carefully and ask ONE thoughtful follow-up question
            that gently encourages the person to reflect deeper on what they shared.

            Guidelines:
            - Ask exactly ONE question, nothing else
            - Be warm and curious, not clinical or analytical
            - Focus on a specific emotion, moment, or detail they mentioned
            - Never give advice or evaluate their feelings
            - Keep it under 25 words
            - Return only the question text — no quotes, no label, no preamble

            Journal entry:
            $journalEntry
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            response.text?.trim()?.trimEnd('.')?.plus("?")
                ?.takeIf { it.length > 10 }
                ?: fallback()
        } catch (e: Exception) {
            fallback()
        }
    }

    /**
     * Generates a short opening prompt to help the user start a new entry
     * based on their most recent previous entry.
     */
    suspend fun generateContinuationPrompt(previousEntry: String): String {
        if (previousEntry.isBlank()) return fallback()

        val prompt = """
            You are a gentle journaling companion. Based on this previous journal entry,
            write ONE short sentence to invite the person to continue reflecting today.

            Keep it warm, open-ended, and under 20 words.
            Return only the sentence — no quotes, no label.

            Previous entry:
            $previousEntry
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            response.text?.trim() ?: fallback()
        } catch (e: Exception) {
            fallback()
        }
    }

    private fun fallback(): String = listOf(
        "What aspect of this would you like to sit with longer?",
        "How did this make you feel in the moment?",
        "What do you think you'll carry forward from this?",
        "Is there something you wish had gone differently?",
        "What does this experience reveal about what matters most to you?",
        "What felt hardest about this, and why?",
        "If you could tell someone one thing about today, what would it be?"
    ).random()
}