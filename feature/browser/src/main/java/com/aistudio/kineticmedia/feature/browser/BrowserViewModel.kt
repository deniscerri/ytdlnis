package com.aistudio.kineticmedia.feature.browser

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.aistudio.kineticmedia.core.database.KineticDatabase
import com.aistudio.kineticmedia.core.database.model.Bookmark
import com.aistudio.kineticmedia.core.database.model.DownloadItem
import com.aistudio.kineticmedia.core.database.model.HistoryItem
import com.aistudio.kineticmedia.core.database.model.TabEntity
import com.aistudio.kineticmedia.feature.downloads.DownloadWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = KineticDatabase.getInstance(application)
    private val tabDao = database.tabDao()
    private val historyDao = database.historyDao()
    private val bookmarkDao = database.bookmarkDao()
    private val downloadDao = database.downloadDao()

    val tabs: StateFlow<List<TabEntity>> = tabDao.getAllTabs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryItem>> = historyDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkDao.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentTabId = MutableStateFlow<Long?>(null)
    val currentTabId: StateFlow<Long?> = _currentTabId.asStateFlow()

    private val _currentUrl = MutableStateFlow("https://example.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow("Example Domain")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _incognitoTabs = MutableStateFlow<List<TabEntity>>(
        listOf(TabEntity(id = -1L, url = "https://example.com", title = "Incognito Tab", isIncognito = true))
    )
    val incognitoTabs: StateFlow<List<TabEntity>> = _incognitoTabs.asStateFlow()

    // Video candidates discovered on current page
    private val _candidates = MutableStateFlow<List<VideoCandidate>>(emptyList())
    val candidates: StateFlow<List<VideoCandidate>> = _candidates.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = tabDao.getAllTabs().first()
            if (existing.isEmpty()) {
                val id = tabDao.insertTab(TabEntity(url = "https://example.com", title = "Example Domain"))
                _currentTabId.value = id
            } else {
                _currentTabId.value = existing.first().id
                _currentUrl.value = existing.first().url
                _currentTitle.value = existing.first().title
            }
        }
    }

    fun loadUrl(inputUrl: String) {
        val formattedUrl = if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
            inputUrl
        } else if (inputUrl.contains(".") && !inputUrl.contains(" ")) {
            "https://$inputUrl"
        } else {
            "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(inputUrl, "UTF-8")}"
        }

        _currentUrl.value = formattedUrl
        // Clear previous page candidates on new url load
        _candidates.value = emptyList()

        // Record history if not incognito
        if (!_isIncognito.value) {
            viewModelScope.launch {
                historyDao.insertHistory(HistoryItem(url = formattedUrl, title = _currentTitle.value))
            }
        }
    }

    fun updateTitle(title: String) {
        _currentTitle.value = title
    }

    fun updateProgress(progress: Int) {
        _progress.value = progress
    }

    fun updateCandidates(newCandidates: List<VideoCandidate>) {
        _candidates.value = newCandidates
    }

    fun queueDownloads(context: Context, selectedCandidates: List<VideoCandidate>) {
        viewModelScope.launch {
            for (candidate in selectedCandidates) {
                // Insert into Room DB
                val downloadId = downloadDao.insertDownload(
                    DownloadItem(
                        url = candidate.url,
                        title = candidate.title,
                        filePath = "",
                        progress = 0f,
                        status = "QUEUED",
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Enqueue WorkManager job
                val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(
                            "download_id" to downloadId,
                            "url" to candidate.url,
                            "title" to candidate.title
                        )
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }

    fun addTab(url: String = "https://example.com", isIncognito: Boolean = false) {
        viewModelScope.launch {
            if (isIncognito) {
                val newTab = TabEntity(id = System.currentTimeMillis(), url = url, title = "New Tab", isIncognito = true)
                _incognitoTabs.value = _incognitoTabs.value + newTab
                _currentTabId.value = newTab.id
                _currentUrl.value = url
                _currentTitle.value = "New Tab"
            } else {
                val id = tabDao.insertTab(TabEntity(url = url, title = "New Tab", isIncognito = false))
                _currentTabId.value = id
                _currentUrl.value = url
                _currentTitle.value = "New Tab"
            }
            _candidates.value = emptyList()
        }
    }

    fun switchTab(tabId: Long, isIncognito: Boolean) {
        _isIncognito.value = isIncognito
        if (isIncognito) {
            val tab = _incognitoTabs.value.find { it.id == tabId }
            if (tab != null) {
                _currentTabId.value = tab.id
                _currentUrl.value = tab.url
                _currentTitle.value = tab.title
            }
        } else {
            viewModelScope.launch {
                val tab = tabs.value.find { it.id == tabId }
                if (tab != null) {
                    _currentTabId.value = tab.id
                    _currentUrl.value = tab.url
                    _currentTitle.value = tab.title
                }
            }
        }
        _candidates.value = emptyList()
    }

    fun closeTab(tabId: Long, isIncognito: Boolean) {
        viewModelScope.launch {
            if (isIncognito) {
                val list = _incognitoTabs.value.filter { it.id != tabId }
                if (list.isEmpty()) {
                    _incognitoTabs.value = listOf(TabEntity(id = -1L, url = "https://example.com", title = "Incognito Tab", isIncognito = true))
                } else {
                    _incognitoTabs.value = list
                }
                if (_currentTabId.value == tabId) {
                    _currentTabId.value = _incognitoTabs.value.first().id
                    _currentUrl.value = _incognitoTabs.value.first().url
                }
            } else {
                tabDao.deleteTabById(tabId)
                val remaining = tabDao.getAllTabs().first()
                if (remaining.isEmpty()) {
                    val newId = tabDao.insertTab(TabEntity(url = "https://example.com", title = "Example Domain"))
                    _currentTabId.value = newId
                    _currentUrl.value = "https://example.com"
                } else {
                    if (_currentTabId.value == tabId) {
                        _currentTabId.value = remaining.first().id
                        _currentUrl.value = remaining.first().url
                    }
                }
            }
        }
    }

    fun toggleIncognito(incognito: Boolean) {
        _isIncognito.value = incognito
        if (incognito) {
            val first = _incognitoTabs.value.first()
            _currentTabId.value = first.id
            _currentUrl.value = first.url
            _currentTitle.value = first.title
        } else {
            viewModelScope.launch {
                val first = tabs.value.firstOrNull() ?: TabEntity(url = "https://example.com", title = "Example")
                _currentTabId.value = first.id
                _currentUrl.value = first.url
                _currentTitle.value = first.title
            }
        }
    }

    fun addBookmark(url: String, title: String) {
        viewModelScope.launch {
            bookmarkDao.insertBookmark(Bookmark(url = url, title = title))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearHistory()
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyDao.deleteHistoryById(id)
        }
    }
}
