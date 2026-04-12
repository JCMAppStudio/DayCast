package com.example.daycast

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * Manages app-lock security: PIN, password, and biometric toggle.
 * Credentials are stored as SHA-256 hashes in SharedPreferences.
 *
 * NOTE: Add this dependency to your app-level build.gradle.kts:
 *   implementation("androidx.biometric:biometric:1.2.0-alpha05")
 */
class AppLockManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("daycast_security", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LOCK_TYPE       = "lock_type"       // "none", "pin", "password"
        private const val KEY_CREDENTIAL_HASH = "credential_hash"
        private const val KEY_BIOMETRIC_ON    = "biometric_enabled"
    }

    // ---- Lock type ----

    enum class LockType { NONE, PIN, PASSWORD }

    var lockType: LockType
        get() = when (prefs.getString(KEY_LOCK_TYPE, "none")) {
            "pin"      -> LockType.PIN
            "password" -> LockType.PASSWORD
            else       -> LockType.NONE
        }
        private set(value) {
            prefs.edit().putString(KEY_LOCK_TYPE, when (value) {
                LockType.PIN      -> "pin"
                LockType.PASSWORD -> "password"
                LockType.NONE     -> "none"
            }).apply()
        }

    val isLockEnabled: Boolean get() = lockType != LockType.NONE

    // ---- Biometric toggle ----

    var isBiometricEnabled: Boolean
        get()  = prefs.getBoolean(KEY_BIOMETRIC_ON, false)
        set(v) { prefs.edit().putBoolean(KEY_BIOMETRIC_ON, v).apply() }

    // ---- Credential management ----

    fun setPin(pin: String) {
        lockType = LockType.PIN
        prefs.edit().putString(KEY_CREDENTIAL_HASH, hash(pin)).apply()
    }

    fun setPassword(password: String) {
        lockType = LockType.PASSWORD
        prefs.edit().putString(KEY_CREDENTIAL_HASH, hash(password)).apply()
    }

    fun verifyCredential(input: String): Boolean {
        val stored = prefs.getString(KEY_CREDENTIAL_HASH, null) ?: return false
        return hash(input) == stored
    }

    fun removeLock() {
        prefs.edit()
            .putString(KEY_LOCK_TYPE, "none")
            .remove(KEY_CREDENTIAL_HASH)
            .putBoolean(KEY_BIOMETRIC_ON, false)
            .apply()
    }

    // ---- Hashing ----

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}