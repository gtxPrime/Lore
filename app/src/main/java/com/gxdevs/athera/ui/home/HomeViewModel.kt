package com.gxdevs.athera.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.JournalEntry
import com.gxdevs.athera.JournalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.gxdevs.athera.data.SettingsRepository

/**
 * ViewModel for the Athera Home Screen.
 * Exposes real journal entries, search functionality, and user profile.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: JournalRepository

    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

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
        
        viewModelScope.launch {
            settingsRepo.isDecoyMode.collect { isDecoy ->
                if (isDecoy) {
                    _userName.value = "Wanderer"
                } else {
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
                    (entry.emotions?.lowercase()?.contains(q) == true) ||
                    (entry.location?.lowercase()?.contains(q) == true) ||
                    (entry.people?.lowercase()?.contains(q) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    }


    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadUserName() {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        _userName.value = prefs.getString("user_name", null)
    }

    fun saveUserName(name: String) {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit { putString("user_name", name) }
        _userName.value = name
    }
}




