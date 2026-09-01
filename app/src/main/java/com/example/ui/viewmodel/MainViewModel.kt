package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.DocumentEntity
import com.example.data.model.DocumentType
import com.example.data.model.RagSettings
import com.example.data.model.RetrievedChunk
import com.example.data.model.SourceCitation
import com.example.data.model.StorageStats
import com.example.data.preferences.RagSettingsRepository
import com.example.data.remote.GeminiRagService
import com.example.data.repository.KnowledgeBaseRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ApiKeyTestState {
    data object Idle : ApiKeyTestState
    data object Testing : ApiKeyTestState
    data class Success(val message: String) : ApiKeyTestState
    data class Error(val error: String) : ApiKeyTestState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val settingsRepo = RagSettingsRepository(application)
    private val repository = KnowledgeBaseRepository(
        context = application,
        database = database,
        settingsRepository = settingsRepo
    )
    private val geminiService = GeminiRagService()

    private val moshi = Moshi.Builder().build()
    private val citationListType = Types.newParameterizedType(List::class.java, SourceCitation::class.java)
    private val citationAdapter = moshi.adapter<List<SourceCitation>>(citationListType)

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.getChatMessages("default")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<RagSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RagSettings())

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    private val _apiKeyTestState = MutableStateFlow<ApiKeyTestState>(ApiKeyTestState.Idle)
    val apiKeyTestState: StateFlow<ApiKeyTestState> = _apiKeyTestState.asStateFlow()

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    private val _vectorSearchQuery = MutableStateFlow("")
    val vectorSearchQuery: StateFlow<String> = _vectorSearchQuery.asStateFlow()

    private val _vectorSearchResults = MutableStateFlow<List<RetrievedChunk>>(emptyList())
    val vectorSearchResults: StateFlow<List<RetrievedChunk>> = _vectorSearchResults.asStateFlow()

    private val _isSearchingVectors = MutableStateFlow(false)
    val isSearchingVectors: StateFlow<Boolean> = _isSearchingVectors.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialDataLoaded()
            refreshStorageStats()
        }
    }

    fun parseCitations(json: String?): List<SourceCitation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            citationAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = repository.getStorageStats()
        }
    }

    fun askQuestion(question: String) {
        if (question.isBlank() || _isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            val result = repository.askQuestion(question)
            _isGenerating.value = false
            if (result.isFailure) {
                _snackbarMessage.value = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun importDocument(uri: Uri, fileName: String, mimeType: String?) {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = repository.importDocument(uri, fileName, mimeType)
            _isIndexing.value = false
            if (result.isSuccess) {
                _snackbarMessage.value = "Imported and indexed \"$fileName\""
                refreshStorageStats()
            } else {
                _snackbarMessage.value = "Import failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun createManualNote(title: String, content: String, type: DocumentType = DocumentType.MANUAL) {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = repository.importRawText(title, content, type)
            _isIndexing.value = false
            if (result.isSuccess) {
                _snackbarMessage.value = "Note \"$title\" added and indexed."
                refreshStorageStats()
            } else {
                _snackbarMessage.value = "Failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            _snackbarMessage.value = "Document removed from knowledge base."
            refreshStorageStats()
        }
    }

    fun reindexDocument(id: Long) {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = repository.reindexDocument(id)
            _isIndexing.value = false
            if (result.isSuccess) {
                _snackbarMessage.value = "Re-indexed (${result.getOrNull()} chunks generated)."
                refreshStorageStats()
            } else {
                _snackbarMessage.value = "Re-index failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun rebuildAllIndexes() {
        viewModelScope.launch {
            _isIndexing.value = true
            val result = repository.rebuildAllIndexes()
            _isIndexing.value = false
            if (result.isSuccess) {
                _snackbarMessage.value = "All indexes rebuilt successfully (${result.getOrNull()} chunks)."
                refreshStorageStats()
            } else {
                _snackbarMessage.value = "Rebuild failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat("default")
            _snackbarMessage.value = "Chat history cleared."
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _snackbarMessage.value = "All documents and conversations cleared."
            refreshStorageStats()
        }
    }

    fun loadSampleKnowledgeBase() {
        viewModelScope.launch {
            _isIndexing.value = true
            repository.loadSampleKnowledgeBase()
            _isIndexing.value = false
            _snackbarMessage.value = "Sample knowledge base loaded."
            refreshStorageStats()
        }
    }

    fun updateSettings(newSettings: RagSettings) {
        settingsRepo.updateSettings(newSettings)
        _snackbarMessage.value = "Settings saved."
    }

    fun testApiKey(apiKey: String) {
        viewModelScope.launch {
            _apiKeyTestState.value = ApiKeyTestState.Testing
            val res = geminiService.testApiKey(apiKey)
            if (res.isSuccess) {
                _apiKeyTestState.value = ApiKeyTestState.Success("Connection successful! Gemini API is active.")
            } else {
                val err = res.exceptionOrNull()?.message ?: "Validation failed"
                _apiKeyTestState.value = ApiKeyTestState.Error(err)
            }
        }
    }

    fun setVectorSearchQuery(query: String) {
        _vectorSearchQuery.value = query
        if (query.isBlank()) {
            _vectorSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearchingVectors.value = true
            val s = settings.value
            val results = repository.searchVectorIndex(
                query = query,
                topK = s.topK.coerceAtLeast(6),
                similarityThreshold = 0.05f
            )
            _vectorSearchResults.value = results
            _isSearchingVectors.value = false
        }
    }
}
