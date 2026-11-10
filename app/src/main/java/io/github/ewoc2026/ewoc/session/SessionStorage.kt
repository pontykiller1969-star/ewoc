package io.github.ewoc2026.ewoc.session

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A lightweight view of a persisted workout. The original text files remain
 * readable and are parsed on demand, so existing workout history is retained.
 */
data class StoredSession(
    val timestampMillis: Long,
    val durationSeconds: Int?,
    val caloriesKcal: Int?,
    val distanceMeters: Int?,
    val averagePowerWatts: Int?,
)

object SessionStorage {

    fun save(context: Context, summary: SessionSummary) {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
        val filename = "session_${formatter.format(Date(summary.stopTimestampMillis))}.txt"
        val content = buildString {
            appendLine("Duration: ${formatTime(summary.durationSeconds)}")
            appendLine("Actual TSS: ${formatOneDecimal(summary.actualTss)}")
            appendLine("Avg power: ${summary.avgPower ?: "--"} W")
            appendLine("Max HR: ${summary.maxHeartRate ?: "--"} bpm")
            appendLine("Distance: ${summary.distanceMeters ?: "--"} m")
            appendLine("Total calories: ${summary.totalEnergyKcal ?: "--"} kcal")
        }
        context.openFileOutput(filename, Context.MODE_PRIVATE)
            .use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Writes every saved workout file into a portable ZIP backup.
     * The original session text files are preserved without conversion.
     */
    fun exportBackup(context: Context, output: OutputStream): Int {
        val files = context.fileList()
            .filter { SESSION_FILE_PATTERN.matches(it) }
            .sorted()
        ZipOutputStream(output.buffered()).use { zip ->
            files.forEach { filename ->
                zip.putNextEntry(ZipEntry(filename))
                context.openFileInput(filename).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return files.size
    }

    /**
     * Restores workout text files from a backup ZIP. Only valid session filenames
     * are accepted; existing files are left untouched to avoid overwriting data.
     */
    fun importBackup(context: Context, input: InputStream): Int {
        var imported = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && SESSION_FILE_PATTERN.matches(entry.name) &&
                    !context.fileList().contains(entry.name)
                ) {
                    val bytes = zip.readBytes()
                    if (bytes.size <= MAX_SESSION_FILE_BYTES &&
                        bytes.toString(Charsets.UTF_8).contains("Duration:")
                    ) {
                        context.openFileOutput(entry.name, Context.MODE_PRIVATE).use { it.write(bytes) }
                        imported++
                    }
                }
                zip.closeEntry()
            }
        }
        return imported
    }

    private val SESSION_FILE_PATTERN =
        Regex("session_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-\\d{3}\\.txt")
    private const val MAX_SESSION_FILE_BYTES = 1024 * 1024

    /** Returns all saved sessions, newest first, including files from older app versions. */
    fun loadAll(context: Context): List<StoredSession> {
        val filenameParser = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)
        return context.fileList()
            .filter { it.startsWith("session_") && it.endsWith(".txt") }
            .mapNotNull { filename ->
                val timestamp = runCatching {
                    filenameParser.parse(filename.removePrefix("session_").removeSuffix(".txt"))?.time
                }.getOrNull() ?: return@mapNotNull null
                val text = runCatching {
                    context.openFileInput(filename).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull() ?: return@mapNotNull null
                fun number(label: String): Int? {
                    val value = Regex("(?m)^" + Regex.escape(label) + ":\\s*(\\d+)")
                        .find(text)?.groupValues?.getOrNull(1)
                    return value?.toIntOrNull()
                }
                val duration = Regex("(?m)^Duration:\\s*(\\d+):(\\d{2})")
                    .find(text)?.let { it.groupValues[1].toIntOrNull()?.times(60)?.plus(it.groupValues[2].toInt()) }
                StoredSession(
                    timestampMillis = timestamp,
                    durationSeconds = duration,
                    caloriesKcal = number("Total calories"),
                    distanceMeters = number("Distance"),
                    averagePowerWatts = number("Avg power"),
                )
            }
            .sortedByDescending { it.timestampMillis }
    }

    private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun formatOneDecimal(value: Double?): String =
        if (value == null) "--" else String.format(Locale.US, "%.1f", value)
}
