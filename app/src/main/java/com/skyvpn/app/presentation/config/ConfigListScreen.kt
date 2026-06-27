package com.skyvpn.app.presentation.config

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skyvpn.app.domain.model.VPNConfig
import com.skyvpn.app.util.ClipboardUtils
import com.skyvpn.app.util.ConfigParser
import com.skyvpn.app.util.QrCodeImportDecoder
import com.skyvpn.app.util.ShareUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigListScreen(
    viewModel: ConfigListViewModel,
    onNavigateBack: () -> Unit
) {
    val configs by viewModel.filteredConfigs.collectAsState()
    val allConfigs by viewModel.configs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val importMessage by viewModel.importMessage.collectAsState()
    val selectedConfigId by viewModel.selectedConfigId.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var isQrImporting by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<VPNConfig?>(null) }
    var exportingConfig by remember { mutableStateOf<VPNConfig?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val qrImportScope = rememberCoroutineScope()
    val selectedConfig = allConfigs.firstOrNull { it.id == selectedConfigId }
    val selectedConfigError = selectedConfig?.let { ConfigParser.getValidationError(it) }

    fun importDecodedQr(text: String) {
        importText = ""
        showImportDialog = false
        viewModel.importFromText(text)
    }

    fun showQrImportError(error: Throwable) {
        viewModel.showMessage("QR import failed: ${error.message ?: "unable to read QR"}")
    }

    val qrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        qrImportScope.launch {
            isQrImporting = true
            val result = try {
                QrCodeImportDecoder.decodeFromUri(context, uri)
            } finally {
                isQrImporting = false
            }

            result
                .onSuccess { text -> importDecodedQr(text) }
                .onFailure { error -> showQrImportError(error) }
        }
    }

    val qrCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            viewModel.showMessage("QR scan canceled")
            return@rememberLauncherForActivityResult
        }

        qrImportScope.launch {
            isQrImporting = true
            val result = try {
                QrCodeImportDecoder.decodeFromBitmap(bitmap)
            } finally {
                isQrImporting = false
            }

            result
                .onSuccess { text -> importDecodedQr(text) }
                .onFailure { error -> showQrImportError(error) }
        }
    }

    LaunchedEffect(importMessage) {
        val message = importMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearImportMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            ActiveConfigBanner(
                config = selectedConfig?.takeIf { selectedConfigError == null },
                error = selectedConfigError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

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
                    items(configs) { config ->
                        val validationError = ConfigParser.getValidationError(config)
                        ConfigCard(
                            config = config,
                            isSelected = config.id == selectedConfigId && validationError == null,
                            validationError = validationError,
                            onClick = {
                                viewModel.selectConfig(config.id)
                            },
                            onDelete = { viewModel.deleteConfig(config) },
                            onPin = { viewModel.togglePin(config.id, !config.isPinned) },
                            onEdit = { editingConfig = config },
                            onExport = {
                                exportingConfig = config
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
                        placeholder = { Text("Paste configs or subscription URL") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            importText = ClipboardUtils.getText(context).orEmpty()
                        },
                        enabled = !isLoading && !isQrImporting
                    ) {
                        Text("Paste Clipboard")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { qrImageLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && !isQrImporting
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isQrImporting) "Reading..." else "QR Image", maxLines = 1)
                        }
                        TextButton(
                            onClick = { qrCameraLauncher.launch(null) },
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading && !isQrImporting
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR", maxLines = 1)
                        }
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
                    enabled = importText.isNotBlank() && !isLoading && !isQrImporting
                ) {
                    Text(if (isLoading) "Importing..." else "Import")
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

    exportingConfig?.let { config ->
        ExportConfigDialog(
            config = config,
            viewModel = viewModel,
            onDismiss = { exportingConfig = null }
        )
    }
}

@Composable
private fun ConfigCard(
    config: VPNConfig,
    isSelected: Boolean,
    validationError: String?,
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
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (validationError != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
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
                tint = if (config.isLocked || validationError != null)
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
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    if (validationError != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "INVALID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (isSelected) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = when {
                        config.isLocked -> "Config locked"
                        validationError != null -> "Invalid config: $validationError"
                        else -> "${config.protocol.name} | ${config.address}:${config.port}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (!config.isLocked && validationError == null && config.transportType != com.skyvpn.app.domain.model.TransportType.TCP) {
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
private fun ActiveConfigBanner(
    config: VPNConfig?,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Config",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = when {
                        config != null -> config.name.ifBlank { "Unnamed" }
                        error != null -> "Selected config is invalid: $error"
                        else -> "No config selected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ExportConfigDialog(
    config: VPNConfig,
    viewModel: ConfigListViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var protectedMode by remember(config.id) { mutableStateOf(true) }
    val exported = remember(config, protectedMode) {
        viewModel.exportConfig(config, protectedMode)
    }
    val qrBitmap = remember(exported) {
        exported?.let { createQrBitmap(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Config") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                qrBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Config QR",
                        modifier = Modifier.size(220.dp)
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = exported ?: "Export unavailable",
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        IconButton(
                            onClick = {
                                exported?.let {
                                    ClipboardUtils.setText(context, "SkynetVPN Config", it)
                                    viewModel.showMessage("Copied export link")
                                }
                            },
                            enabled = exported != null
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ExportModeButton(
                        text = "Protect",
                        selected = protectedMode,
                        onClick = { protectedMode = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ExportModeButton(
                        text = "Standard",
                        selected = !protectedMode,
                        enabled = !config.isLocked,
                        onClick = { protectedMode = false }
                    )
                }

                if (config.isLocked && !protectedMode) {
                    Text(
                        text = "Standard export is disabled for locked configs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    exported?.let {
                        ShareUtils.shareText(context, it, "Export Config")
                    }
                },
                enabled = exported != null
            ) {
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ExportModeButton(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text)
        }
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled
        ) {
            Text(text)
        }
    }
}

private fun createQrBitmap(text: String): Bitmap? {
    return runCatching {
        val size = 512
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(
                        x,
                        y,
                        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
        }
    }.getOrNull()
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
