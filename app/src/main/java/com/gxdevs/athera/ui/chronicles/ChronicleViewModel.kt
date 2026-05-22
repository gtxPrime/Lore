package com.gxdevs.athera.ui.chronicles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.JournalEntry
import com.gxdevs.athera.Relic
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
    val nowMs = System.currentTimeMillis()

    val uiState: StateFlow<ChronicleUiState> = combine(
        _echoEntry,
        relicDao.getSurfacedRelics(nowMs),
        relicDao.getLockedRelics(nowMs)
    ) { echo, surfaced, locked ->
        ChronicleUiState(
            echoEntry = echo,
            surfacedRelics = surfaced,
            lockedRelics = locked,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChronicleUiState())

    init {
        loadEcho()
    }

    private fun loadEcho() {
        viewModelScope.launch {
            val (start, end) = echoRange
            _echoEntry.value = journalDao.getEchoEntry(start, end)
        }
    }

    fun sealEntry(journalEntryId: Long, titleSnapshot: String?, contentSnapshot: String?, moodSnapshot: String?, unsealAfterDays: Int) {
        viewModelScope.launch {
            relicDao.insertRelic(
                Relic(
                    journalEntryId = journalEntryId,
                    sealedAtTimestamp = System.currentTimeMillis(),
                    unsealAfterDays = unsealAfterDays,
                    titleSnapshot = titleSnapshot,
                    contentSnapshot = contentSnapshot,
                    moodSnapshot = moodSnapshot
                )
            )
        }
    }

    fun markRelicUnsealed(relicId: Long) {
        viewModelScope.launch { relicDao.markUnsealed(relicId) }
    }

    /** Format a relic's unseal date for display, e.g. "APR 10, 2026" */
    fun relicUnsealDateStr(relic: Relic): String {
        val unsealMs = relic.sealedAtTimestamp + relic.unsealAfterDays.toLong() * 86_400_000L
        return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(unsealMs).uppercase()
    }

    /** Days remaining until a relic unlocks */
    fun daysRemaining(relic: Relic): String {
        val unsealMs = relic.sealedAtTimestamp + relic.unsealAfterDays.toLong() * 86_400_000L
        val days = ((unsealMs - nowMs) / 86_400_000L).toInt().coerceAtLeast(0)
        return "$days Days"
    }
}

