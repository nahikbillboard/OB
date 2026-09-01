package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AddDocumentDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.KnowledgeBaseScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VectorSearchScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddDocDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    // Document Picker Launcher (PDF, DOCX, TXT, etc.)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri)
            val mimeType = context.contentResolver.getType(uri)
            viewModel.importDocument(uri, fileName, mimeType)
        }
    }

    // Observe States
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isIndexing by viewModel.isIndexing.collectAsStateWithLifecycle()
    val apiKeyTestState by viewModel.apiKeyTestState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val vectorSearchQuery by viewModel.vectorSearchQuery.collectAsStateWithLifecycle()
    val vectorSearchResults by viewModel.vectorSearchResults.collectAsStateWithLifecycle()
    val isSearchingVectors by viewModel.isSearchingVectors.collectAsStateWithLifecycle()

    val hasApiKey = settings.customApiKey.isNotBlank()

    if (showAddDocDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDocDialog = false },
            onPickFileClick = {
                documentPickerLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword",
                        "text/plain",
                        "text/*",
                        "*/*"
                    )
                )
            },
            onAddManualNote = { title, content, type ->
                viewModel.createManualNote(title, content, type)
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Chat else Icons.Outlined.Chat,
                            contentDescription = "Chatbot"
                        )
                    },
                    label = { Text("Chatbot") },
                    modifier = Modifier.testTag("nav_chat_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = "Knowledge Base"
                        )
                    },
                    label = { Text("Knowledge") },
                    modifier = Modifier.testTag("nav_knowledge_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.Hub else Icons.Outlined.Hub,
                            contentDescription = "Vectors"
                        )
                    },
                    label = { Text("Vectors") },
                    modifier = Modifier.testTag("nav_vector_tab")
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            if (selectedTab == 3) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings_tab")
                )
            }
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatScreen(
                    messages = chatMessages,
                    isGenerating = isGenerating,
                    settings = settings,
                    hasApiKey = hasApiKey,
                    onSendMessage = { q -> viewModel.askQuestion(q) },
                    onClearChat = { viewModel.clearChat() },
                    onOpenAddDocument = { showAddDocDialog = true },
                    onOpenSettings = { selectedTab = 3 },
                    parseCitations = { json -> viewModel.parseCitations(json) }
                )
                1 -> KnowledgeBaseScreen(
                    documents = documents,
                    storageStats = storageStats,
                    isIndexing = isIndexing,
                    onOpenAddDocument = { showAddDocDialog = true },
                    onReindexDocument = { id -> viewModel.reindexDocument(id) },
                    onDeleteDocument = { id -> viewModel.deleteDocument(id) },
                    onRebuildAllIndexes = { viewModel.rebuildAllIndexes() },
                    onLoadSampleData = { viewModel.loadSampleKnowledgeBase() }
                )
                2 -> VectorSearchScreen(
                    searchQuery = vectorSearchQuery,
                    searchResults = vectorSearchResults,
                    isSearching = isSearchingVectors,
                    onQueryChange = { q -> viewModel.setVectorSearchQuery(q) }
                )
                3 -> SettingsScreen(
                    currentSettings = settings,
                    apiKeyTestState = apiKeyTestState,
                    onSaveSettings = { s -> viewModel.updateSettings(s) },
                    onTestApiKey = { k -> viewModel.testApiKey(k) },
                    onRebuildIndexes = { viewModel.rebuildAllIndexes() },
                    onClearAllData = { viewModel.clearAllData() },
                    onLoadSampleData = { viewModel.loadSampleKnowledgeBase() }
                )
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "document.txt"
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: "document.txt"
                }
            }
        }
    } catch (e: Exception) {
        val path = uri.path
        if (path != null) {
            val cut = path.lastIndexOf('/')
            if (cut != -1) {
                name = path.substring(cut + 1)
            }
        }
    }
    return name
}
