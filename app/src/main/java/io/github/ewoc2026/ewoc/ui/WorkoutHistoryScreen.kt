package io.github.ewoc2026.ewoc.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ewoc2026.ewoc.session.SessionStorage
import io.github.ewoc2026.ewoc.session.StoredSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private val DurationChartColor = Color(0xFF2878D0)
private val CaloriesChartColor = Color(0xFFE05252)
private val DistanceChartColor = Color(0xFF269B69)

private data class DailyWorkoutTotals(
    val date: LocalDate,
    val durationMinutes: Double,
    val calories: Double,
    val distanceKm: Double,
)

@Composable
internal fun WorkoutHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sessions = remember { SessionStorage.loadAll(context) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val weekStart = remember(today) { today.minusDays((today.dayOfWeek.value - 1).toLong()) }
    val monthStart = remember(today) { today.withDayOfMonth(1) }
    var selectedRange by remember { mutableIntStateOf(30) }

    fun sessionDate(session: StoredSession): LocalDate =
        Instant.ofEpochMilli(session.timestampMillis).atZone(zone).toLocalDate()

    val todaySessions = sessions.filter { sessionDate(it) == today }
    val weekSessions = sessions.filter { !sessionDate(it).isBefore(weekStart) && !sessionDate(it).isAfter(today) }
    val monthSessions = sessions.filter { !sessionDate(it).isBefore(monthStart) && !sessionDate(it).isAfter(today) }

    val firstDate = sessions.minOfOrNull { sessionDate(it) } ?: today
    val chartStart = if (selectedRange == 0) firstDate else today.minusDays((selectedRange - 1).toLong())
    val chartDates = (0L..java.time.temporal.ChronoUnit.DAYS.between(chartStart, today).coerceAtLeast(0L))
        .map { chartStart.plusDays(it) }
    val totalsByDate = sessions.groupBy(::sessionDate)
    val chartData = chartDates.map { date ->
        val daySessions = totalsByDate[date].orEmpty()
        DailyWorkoutTotals(
            date = date,
            durationMinutes = daySessions.sumOf { it.durationSeconds ?: 0 } / 60.0,
            calories = daySessions.sumOf { it.caloriesKcal ?: 0 }.toDouble(),
            distanceKm = daySessions.sumOf { it.distanceMeters ?: 0 } / 1000.0,
        )
    }

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

        Text("Napi statisztika", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30, 90, 0).forEach { days ->
                val label = if (days == 0) "Összes" else "$days nap"
                if (selectedRange == days) {
                    Button(
                        onClick = { selectedRange = days },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(label, fontSize = 11.sp, maxLines = 1) }
                } else {
                    OutlinedButton(
                        onClick = { selectedRange = days },
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text(label, fontSize = 11.sp, maxLines = 1) }
                }
            }
        }
        if (sessions.isEmpty()) {
            Text("A grafikon az első elmentett edzés után jelenik meg.")
        } else {
            CombinedWorkoutChart(values = chartData)
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
private fun CombinedWorkoutChart(values: List<DailyWorkoutTotals>) {
    val durationMax = max(1.0, values.maxOfOrNull { it.durationMinutes } ?: 0.0)
    val caloriesMax = max(1.0, values.maxOfOrNull { it.calories } ?: 0.0)
    val distanceMax = max(1.0, values.maxOfOrNull { it.distanceKm } ?: 0.0)
    val formatter = remember { DateTimeFormatter.ofPattern("MM.dd", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Teljesítmény – napi alakulás", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChartLegendItem(DurationChartColor, "Idő (perc)")
                ChartLegendItem(CaloriesChartColor, "Kalória (kcal)")
                ChartLegendItem(DistanceChartColor, "Távolság (km)")
            }
            Text(
                "A három vonal külön-külön, a saját legnagyobb értékéhez viszonyítva van skálázva.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                if (values.isEmpty()) return@Canvas
                val left = 38.dp.toPx()
                val right = size.width - 10.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height - 28.dp.toPx()
                val plotWidth = (right - left).coerceAtLeast(1f)
                val plotHeight = (bottom - top).coerceAtLeast(1f)
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 10.dp.toPx()
                }
                for (tick in 0..2) {
                    val y = bottom - plotHeight * tick / 2f
                    drawLine(
                        Color.LightGray.copy(alpha = 0.7f),
                        androidx.compose.ui.geometry.Offset(left, y),
                        androidx.compose.ui.geometry.Offset(right, y),
                        1.dp.toPx(),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "${tick * 50}%",
                        0f,
                        y + 4.dp.toPx(),
                        labelPaint,
                    )
                }

                fun drawSeries(
                    color: Color,
                    valueAt: (DailyWorkoutTotals) -> Double,
                    maximum: Double,
                    dashPattern: FloatArray? = null,
                ) {
                    val path = Path()
                    values.forEachIndexed { index, item ->
                        val x = if (values.size == 1) left + plotWidth / 2f
                            else left + plotWidth * index / (values.size - 1)
                        val normalized = (valueAt(item) / maximum).toFloat().coerceIn(0f, 1f)
                        val y = bottom - normalized * plotHeight
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val effect = dashPattern?.let { PathEffect.dashPathEffect(it, 0f) }
                    drawPath(
                        path,
                        color,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = effect),
                    )
                }

                // The series share the same active dates, so their normalized paths can overlap.
                // Distinct dash patterns keep all three metrics visible instead of letting the
                // last (green) series completely cover the blue and red ones.
                drawSeries(DurationChartColor, { it.durationMinutes }, durationMax, floatArrayOf(10f, 6f))
                drawSeries(CaloriesChartColor, { it.calories }, caloriesMax, floatArrayOf(4f, 4f))
                drawSeries(DistanceChartColor, { it.distanceKm }, distanceMax, floatArrayOf(2f, 4f))

                val labelIndexes = listOf(0, values.size / 2, values.lastIndex).distinct()
                labelIndexes.forEach { index ->
                    val x = if (values.size == 1) left + plotWidth / 2f
                        else left + plotWidth * index / (values.size - 1)
                    drawContext.canvas.nativeCanvas.drawText(
                        values[index].date.format(formatter),
                        x - 16.dp.toPx(),
                        size.height - 4.dp.toPx(),
                        labelPaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.padding(top = 7.dp).height(3.dp).width(16.dp)) {
            drawLine(color, androidx.compose.ui.geometry.Offset.Zero,
                androidx.compose.ui.geometry.Offset(size.width, 0f), 2.dp.toPx())
        }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
