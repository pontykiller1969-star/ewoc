package io.github.ewoc2026.ewoc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.session.SessionStorage
import io.github.ewoc2026.ewoc.session.StoredSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WorkoutHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sessions = remember { SessionStorage.loadAll(context) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val weekStart = remember(today) { today.minusDays((today.dayOfWeek.value - 1).toLong()) }
    val monthStart = remember(today) { today.withDayOfMonth(1) }

    fun sessionDate(session: StoredSession): LocalDate =
        Instant.ofEpochMilli(session.timestampMillis).atZone(zone).toLocalDate()

    val todaySessions = sessions.filter { sessionDate(it) == today }
    val weekSessions = sessions.filter { !sessionDate(it).isBefore(weekStart) && !sessionDate(it).isAfter(today) }
    val monthSessions = sessions.filter { !sessionDate(it).isBefore(monthStart) && !sessionDate(it).isAfter(today) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Edzésnapló", style = MaterialTheme.typography.headlineMedium)
        Text("Az edzések adatai ezen a telefonon, helyben vannak tárolva.", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Ma", todaySessions, Modifier.weight(1f))
            SummaryCard("Ezen a héten", weekSessions, Modifier.weight(1f))
            SummaryCard("Ebben a hónapban", monthSessions, Modifier.weight(1f))
        }
        Text("Korábbi edzések", style = MaterialTheme.typography.titleLarge)
        if (sessions.isEmpty()) {
            Text("Még nincs elmentett edzés. Az első befejezett edzés után itt jelenik meg.")
        } else {
            sessions.forEach { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dateTime = Instant.ofEpochMilli(session.timestampMillis)
                            .atZone(zone).format(DateTimeFormatter.ofPattern("yyyy. MMM d. • HH:mm", Locale.getDefault()))
                        Text(dateTime, style = MaterialTheme.typography.titleMedium)
                        Text("Idő: ${session.durationSeconds?.let(::formatDuration) ?: "—"}")
                        Text("Kalória: ${session.caloriesKcal?.let { "$it kcal" } ?: "—"}")
                        Text("Távolság: ${session.distanceMeters?.let { String.format(Locale.getDefault(), "%.2f km", it / 1000.0) } ?: "—"}")
                        Text("Átlagteljesítmény: ${session.averagePowerWatts?.let { "$it W" } ?: "—"}")
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Vissza a főmenübe") }
    }
}

@Composable
private fun SummaryCard(title: String, sessions: List<StoredSession>, modifier: Modifier = Modifier) {
    val duration = sessions.sumOf { it.durationSeconds ?: 0 }
    val calories = sessions.sumOf { it.caloriesKcal ?: 0 }
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text("${sessions.size} edzés", style = MaterialTheme.typography.titleMedium)
            Text(formatDuration(duration), style = MaterialTheme.typography.bodyMedium)
            Text("$calories kcal", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}ó ${minutes}p" else "${minutes}p"
}
