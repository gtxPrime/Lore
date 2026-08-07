package com.gxdevs.lore.ui.chronicles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.relic.Relic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ChronicleUiState(
    val echoEntry: JournalEntry? = null,        // entry from same day 1 year ago
    val surfacedRelics: List<Relic> = emptyList(),
    val lockedRelics: List<Relic> = emptyList(),
    val isLoading: Boolean = true
)

class ChronicleViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val journalDao = db.journalDao()
    private val relicDao = db.relicDao()

    // Epoch bounds for "today one year ago"
    private val echoRange: Pair<Long, Long> get() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return Pair(start, cal.timeInMillis)
    }

    private val _echoEntry = MutableStateFlow<JournalEntry?>(null)
    private val _currentTime = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<ChronicleUiState> = combine(
        _echoEntry,
        relicDao.getAllRelics(),
        com.gxdevs.lore.data.SettingsRepository(application).isDecoyMode,
        _currentTime
    ) { echo, allRelics, isDecoy, now ->
        if (isDecoy) {
            ChronicleUiState(isLoading = false)
        } else {
            val surfaced = allRelics.filter { !it.isUnsealed && (it.sealedAtTimestamp + it.unsealAfterDays.toLong() * 86_400_000L) <= now }
            val locked = allRelics.filter { !it.isUnsealed && (it.sealedAtTimestamp + it.unsealAfterDays.toLong() * 86_400_000L) > now }
            ChronicleUiState(
                echoEntry = echo,
                surfacedRelics = surfaced,
                lockedRelics = locked,
                isLoading = false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChronicleUiState())

    init {
        loadEcho()
        viewModelScope.launch {
            while (true) {
                _currentTime.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    private fun loadEcho() {
        viewModelScope.launch {
            val (start, end) = echoRange
            _echoEntry.value = journalDao.getEchoEntry(start, end)
        }
    }

    /** Format a relic's unseal date for display, e.g. "APR 10, 2026" */
    fun relicUnsealDateStr(relic: Relic): String {
        val unsealMs = relic.sealedAtTimestamp + relic.unsealAfterDays.toLong() * 86_400_000L
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(unsealMs).uppercase()
    }

    /** Format remaining time until a relic unlocks */
    fun formatRemainingTime(relic: Relic, nowMs: Long = System.currentTimeMillis()): String {
        val unsealMs = relic.sealedAtTimestamp + relic.unsealAfterDays.toLong() * 86_400_000L
        val diffMs = (unsealMs - nowMs).coerceAtLeast(0)

        val calUnseal = Calendar.getInstance().apply { timeInMillis = unsealMs }
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMs }
        val isToday = calUnseal.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calUnseal.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        return if (isToday || diffMs < 86_400_000L) {
            val totalSec = diffMs / 1000L
            val hours = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            val secs = totalSec % 60L

            if (hours > 0) {
                "${hours}h ${mins}m"
            } else {
                "${mins}m ${secs}s"
            }
        } else {
            val days = (diffMs / 86_400_000L).toInt().coerceAtLeast(1)
            if (days == 1) "1 Day" else "$days Days"
        }
    }

    fun daysRemaining(relic: Relic): String = formatRemainingTime(relic)
}

