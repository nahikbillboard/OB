package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RagSettings
import com.example.ui.theme.DocuCyan
import com.example.ui.theme.DocuEmerald
import com.example.ui.theme.DocuRose
import com.example.ui.viewmodel.ApiKeyTestState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSettings: RagSettings,
    apiKeyTestState: ApiKeyTestState,
    onSaveSettings: (RagSettings) -> Unit,
    onTestApiKey: (String) -> Unit,
    onRebuildIndexes: () -> Unit,
    onClearAllData: () -> Unit,
    onLoadSampleData: () -> Unit
) {
    var apiKey by remember(currentSettings.customApiKey) { mutableStateOf(currentSettings.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var offlineMode by remember(currentSettings.offlineModeOnly) { mutableStateOf(currentSettings.offlineModeOnly) }
    var autoIndex by remember(currentSettings.autoIndexOnUpload) { mutableStateOf(currentSettings.autoIndexOnUpload) }
    var chunkSize by remember(currentSettings.chunkSizeWords) { mutableFloatStateOf(currentSettings.chunkSizeWords.toFloat()) }
    var chunkOverlap by remember(currentSettings.chunkOverlapWords) { mutableFloatStateOf(currentSettings.chunkOverlapWords.toFloat()) }
    var topK by remember(currentSettings.topK) { mutableFloatStateOf(currentSettings.topK.toFloat()) }
    var threshold by remember(currentSettings.similarityThreshold) { mutableFloatStateOf(currentSettings.similarityThreshold) }
    var selectedModel by remember(currentSettings.geminiModel) { mutableStateOf(currentSettings.geminiModel) }

    fun commitSettings() {
        onSaveSettings(
            currentSettings.copy(
                customApiKey = apiKey.trim(),
                offlineModeOnly = offlineMode,
                autoIndexOnUpload = autoIndex,
                chunkSizeWords = chunkSize.toInt(),
                chunkOverlapWords = chunkOverlap.toInt(),
                topK = topK.toInt(),
                similarityThreshold = threshold,
                geminiModel = selectedModel
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Sync",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Button(
                        onClick = { commitSettings() },
                        modifier = Modifier.testTag("save_settings_top_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. API Key Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Gemini AI API Key",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Provide your Gemini API key to enable grounded conversational answers across your personal database.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input_field"),
                            singleLine = true,
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Key Visibility"
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onTestApiKey(apiKey.trim()) },
                                enabled = apiKey.isNotBlank() && apiKeyTestState !is ApiKeyTestState.Testing,
                                modifier = Modifier.testTag("test_api_key_button")
                            ) {
                                if (apiKeyTestState is ApiKeyTestState.Testing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Validating...")
                                } else {
                                    Text("Test Key")
                                }
                            }

                            when (apiKeyTestState) {
                                is ApiKeyTestState.Success -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DocuEmerald, modifier = Modifier.size(16.dp))
                                        Text("Active & Ready", color = DocuEmerald, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                is ApiKeyTestState.Error -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = DocuRose, modifier = Modifier.size(16.dp))
                                        Text("Invalid Key", color = DocuRose, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            // 2. Privacy & Offline Processing Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = DocuCyan)
                            Text(
                                text = "Privacy & Offline Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline-Only Mode", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("100% on-device vector search and extractive synthesis. No cloud network calls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = offlineMode,
                                onCheckedChange = { offlineMode = it },
                                modifier = Modifier.testTag("offline_mode_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automated Indexing", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Instantly chunk and index uploaded documents in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoIndex,
                                onCheckedChange = { autoIndex = it },
                                modifier = Modifier.testTag("auto_index_toggle")
                            )
                        }
                    }
                }
            }

            // 3. Model & Vector Index Tuning
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Vector & Retrieval Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Model selection
                        Column {
                            Text("Gemini Model Architecture", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedModel == "gemini-3.5-flash",
                                    onClick = { selectedModel = "gemini-3.5-flash" },
                                    label = { Text("gemini-3.5-flash (Fast)") }
                                )
                                FilterChip(
                                    selected = selectedModel == "gemini-3.1-pro-preview",
                                    onClick = { selectedModel = "gemini-3.1-pro-preview" },
                                    label = { Text("gemini-3.1-pro-preview (Deep)") }
                                )
                            }
                        }

                        // Chunk size slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Chunk Size", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${chunkSize.toInt()} words", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = chunkSize,
                                onValueChange = { chunkSize = it },
                                valueRange = 100f..600f,
                                steps = 9
                            )
                        }

                        // Chunk overlap slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Chunk Overlap", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${chunkOverlap.toInt()} words", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = chunkOverlap,
                                onValueChange = { chunkOverlap = it },
                                valueRange = 0f..120f,
                                steps = 5
                            )
                        }

                        // Top-K retrieved chunks
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Top-K Retrieved Context", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${topK.toInt()} chunks", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = topK,
                                onValueChange = { topK = it },
                                valueRange = 2f..8f,
                                steps = 5
                            )
                        }

                        // Similarity threshold
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Similarity Threshold", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${(threshold * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = threshold,
                                onValueChange = { threshold = it },
                                valueRange = 0.05f..0.60f,
                                steps = 10
                            )
                        }
                    }
                }
            }

            // 4. Data Management & Sync Actions
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Storage & Database Maintenance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = onRebuildIndexes,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Re-index All Documents")
                        }

                        OutlinedButton(
                            onClick = onLoadSampleData,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Sample Knowledge Base")
                        }

                        Button(
                            onClick = onClearAllData,
                            colors = ButtonDefaults.buttonColors(containerColor = DocuRose),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wipe All Data & History")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
