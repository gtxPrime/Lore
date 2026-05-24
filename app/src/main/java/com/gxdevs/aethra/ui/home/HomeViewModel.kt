package com.gxdevs.aethra.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.aethra.AppDatabase
import com.gxdevs.aethra.JournalEntry
import com.gxdevs.aethra.JournalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.gxdevs.aethra.data.SettingsRepository

/**
 * ViewModel for the Aethra Home Screen.
 * Exposes real journal entries, search functionality, and user profile.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: JournalRepository

    private val _localUserName = MutableStateFlow("")
    val userName: StateFlow<String?>

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** All journal entries from the database, ordered newest first. */
    val allEntries: StateFlow<List<JournalEntry>>

    /** Entries filtered by the current search query. */
    val filteredEntries: StateFlow<List<JournalEntry>>

    private val settingsRepo: SettingsRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = JournalRepository(database)
        settingsRepo = SettingsRepository(application)
        
        userName = combine(
            settingsRepo.googleLoggedIn,
            settingsRepo.googleAccountName,
            settingsRepo.isDecoyMode,
            _localUserName
        ) { googleLoggedIn, googleName, isDecoy, localName ->
            if (isDecoy) {
                "Wanderer"
            } else if (googleLoggedIn && !googleName.isNullOrBlank()) {
                googleName
            } else if (localName.isNotBlank()) {
                localName
            } else {
                "User"
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "User")

        viewModelScope.launch {
            settingsRepo.isDecoyMode.collect { isDecoy ->
                if (!isDecoy) {
                    loadUserName()
                }
            }
        }

        allEntries = repository.allEntries
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        filteredEntries = combine(allEntries, _searchQuery, settingsRepo.isDecoyMode) { entries, query, isDecoy ->
            if (isDecoy) {
                emptyList()
            } else if (query.isBlank()) {
                entries
            } else {
                entries.filter { entry ->
                    val q = query.lowercase()
                    (entry.content?.lowercase()?.contains(q) == true) ||
                    (entry.tags?.lowercase()?.contains(q) == true) ||
                    (entry.emotions?.lowercase()?.contains(q) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadUserName() {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        _localUserName.value = prefs.getString("user_name", "") ?: ""
    }

    fun saveUserName(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString("user_name", name) }
        _localUserName.value = name
    }
}




