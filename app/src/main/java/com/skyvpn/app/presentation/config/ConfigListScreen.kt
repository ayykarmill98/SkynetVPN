package com.skyvpn.app.presentation.config

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.util.ClipboardUtils
import com.skyvpn.app.util.ShareUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListScreen(
    viewModel: ConfigListViewModel,
    onNavigateBack: () -> Unit,
    onConfigSelected: (Long) -> Unit
) {
    val configs by viewModel.filteredConfigs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var editingConfig by remember { mutableStateOf<VPNConfig?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configs (${configs.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Add, "Add Config")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.search(it) },
                    onSearch = { isSearchActive = false },
                    active = false,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Search configs...") },
                    leadingIcon = { Icon(Icons.Default.Search, "Search") },
                    modifier = Modifier.fillMaxWidth()
                ) {}
            }

            if (configs.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No configs yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Import a config to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(configs, key = { it.id }) { config ->
                        ConfigCard(
                            config = config,
                            onClick = { onConfigSelected(config.id) },
                            onDelete = { viewModel.deleteConfig(config) },
                            onPin = { viewModel.togglePin(config.id, !config.isPinned) },
                            onEdit = { editingConfig = config },
                            onExport = {
                                viewModel.exportConfig(config)?.let { exported ->
                                    ShareUtils.shareText(context, exported, "Export Config")
                                }
                            },
                            onToggleLock = { viewModel.lockConfig(config) }
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Config") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        placeholder = { Text("Paste vmess://, vless://, trojan://, ss://, socks://, or http:// config") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            importText = ClipboardUtils.getText(context).orEmpty()
                        }
                    ) {
                        Text("Paste Clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importFromText(importText)
                        importText = ""
                        showImportDialog = false
                    },
                    enabled = importText.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingConfig?.let { config ->
        EditConfigDialog(
            config = config,
            onDismiss = { editingConfig = null },
            onSave = { updated ->
                viewModel.updateConfig(updated)
                editingConfig = null
            }
        )
    }
}

@Composable
private fun ConfigCard(
    config: VPNConfig,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onToggleLock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = if (config.isLocked)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.name.ifEmpty { "Unnamed" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (config.isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (config.isLocked) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    text = if (config.isLocked) "Config locked" else "${config.protocol.name} | ${config.address}:${config.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!config.isLocked && config.transportType != com.skyvpn.app.domain.model.TransportType.TCP) {
                    Text(
                        text = "${config.transportType.name} + ${config.security.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (config.isLocked) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Row {
                        IconButton(onClick = onPin, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.PushPin,
                                "Pin",
                                tint = if (config.isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = onToggleLock, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.LockOpen,
                                "Lock",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Share, "Export", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditConfigDialog(
    config: VPNConfig,
    onDismiss: () -> Unit,
    onSave: (VPNConfig) -> Unit
) {
    var name by remember(config.id) { mutableStateOf(config.name) }
    var address by remember(config.id) { mutableStateOf(config.address) }
    var port by remember(config.id) { mutableStateOf(config.port.toString()) }
    var uuid by remember(config.id) { mutableStateOf(config.uuid) }
    var password by remember(config.id) { mutableStateOf(config.password) }
    var host by remember(config.id) { mutableStateOf(config.host) }
    var path by remember(config.id) { mutableStateOf(config.path) }
    var sni by remember(config.id) { mutableStateOf(config.sni) }
    var isLocked by remember(config.id) { mutableStateOf(config.isLocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Server Address") }
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Port") }
                )
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("UUID / User ID") }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") }
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Host") }
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Path") }
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("SNI") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Config Lock")
                        Text(
                            if (isLocked) "Locked" else "Unlocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = isLocked,
                        onCheckedChange = { isLocked = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            name = name.trim(),
                            address = address.trim(),
                            port = port.toIntOrNull()?.coerceIn(1, 65535) ?: config.port,
                            uuid = uuid.trim(),
                            password = password.trim(),
                            host = host.trim(),
                            path = path.trim(),
                            sni = sni.trim(),
                            isLocked = isLocked
                        )
                    )
                },
                enabled = name.isNotBlank() && address.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
