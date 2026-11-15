package com.aistudio.kineticmedia.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val currentTitle by viewModel.currentTitle.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isIncognito by viewModel.isIncognito.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val incognitoTabs by viewModel.incognitoTabs.collectAsState()

    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val webEngine = remember { WebViewEngine() }

    val totalTabsCount = if (isIncognito) incognitoTabs.size else tabs.size

    if (showTabSwitcher) {
        TabSwitcherScreen(
            tabs = if (isIncognito) incognitoTabs else tabs,
            isIncognito = isIncognito,
            onTabSelected = { tab ->
                viewModel.switchTab(tab.id, isIncognito)
                showTabSwitcher = false
            },
            onTabClosed = { tab ->
                viewModel.closeTab(tab.id, isIncognito)
            },
            onNewTab = {
                viewModel.addTab("https://example.com", isIncognito)
                showTabSwitcher = false
            },
            onDismiss = { showTabSwitcher = false }
        )
        return
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isIncognito) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.loadUrl(urlInput) }) {
                                        Icon(Icons.Default.Search, contentDescription = "Load URL")
                                    }
                                }
                            )

                            // Tab counter chip
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clickable { showTabSwitcher = true }
                                    .size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$totalTabsCount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }

                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add Bookmark") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.addBookmark(currentUrl, currentTitle)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isIncognito) "Exit Incognito" else "Incognito Mode") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleIncognito(!isIncognito)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    onClick = { showMenu = false; onOpenHistory() },
                                    leadingIcon = { Icon(Icons.Default.List, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    onClick = { showMenu = false; onOpenBookmarks() },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Downloads") },
                                    onClick = { showMenu = false; onOpenDownloads() },
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { showMenu = false; onOpenSettings() },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                )
                            }
                        }
                    }
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.height(56.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { webEngine.goBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { webEngine.goForward() }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }
                    IconButton(onClick = { webEngine.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = { viewModel.addTab("https://example.com", isIncognito) }) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                    IconButton(onClick = { showTabSwitcher = true }) {
                        Icon(Icons.Default.List, contentDescription = "Tabs")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            webEngine.Render(
                modifier = Modifier.fillMaxSize(),
                url = currentUrl,
                onUrlChange = { urlInput = it },
                onTitleChange = { viewModel.updateTitle(it) },
                onProgressChange = { viewModel.updateProgress(it) },
                onLoadFinished = {}
            )
        }
    }
}
