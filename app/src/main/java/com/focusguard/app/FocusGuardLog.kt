package com.focusguard.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object FocusGuardLog {

    private const val TAG = "FocusGuard"
    private const val MAX_AGE_MS = 20 * 60 * 1000L
    private const val LOG_FILE_NAME = "focusguard_log.txt"

    private val entries = ConcurrentLinkedDeque<LogEntry>()
    private val dateFormat = SimpleDateFormat(
        "HH:mm:ss.SSS", Locale.US
    )

    private data class LogEntry(
        val timestamp: Long,
        val level: String,
        val message: String
    )

    fun w(message: String) {
        add("WARN", message)
        Log.w(TAG, message)
    }

    fun e(message: String) {
        add("ERROR", message)
        Log.e(TAG, message)
    }

    fun d(message: String) {
        Log.d(TAG, message)
    }

    private fun add(level: String, message: String) {
        val now = System.currentTimeMillis()
        entries.addLast(LogEntry(now, level, message))
        pruneOld(now)
    }

    private fun pruneOld(now: Long) {
        val cutoff = now - MAX_AGE_MS
        while (entries.peekFirst()?.let { it.timestamp < cutoff } == true) {
            entries.pollFirst()
        }
    }

    fun getLogText(): String {
        pruneOld(System.currentTimeMillis())
        val sb = StringBuilder()
        sb.appendLine("FocusGuard Log (last 20 min)")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine("─".repeat(40))
        for (entry in entries) {
            val time = dateFormat.format(Date(entry.timestamp))
            sb.appendLine("[$time] ${entry.level}: ${entry.message}")
        }
        if (entries.isEmpty()) {
            sb.appendLine("(no warnings or errors)")
        }
        return sb.toString()
    }

    fun shareLog(context: Context) {
        val logText = getLogText()
        val file = File(context.cacheDir, LOG_FILE_NAME)
        file.writeText(logText)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FocusGuard Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share FocusGuard Log")
        )
    }
}
