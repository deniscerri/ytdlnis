package com.deniscerri.ytdl.ui.more.settings

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*

// Data class holding all relevant information about a preference for search purposes
data class PreferenceSearchData(
    val key: String,
    val title: String,
    val summary: String,
    val categoryKey: String,
    val categoryTitle: String,
    val xmlRes: Int,
    val isParent: Boolean,
    val depth: Int,
    val parentKey: String?,
    val originalPreference: Preference
)

// Result of a search match, including the preference data, a relevance score, and which fields matched
data class SearchMatch(
    val data: PreferenceSearchData,
    val score: Float,
    val matchedFields: Set<MatchField>
)

// Fields that can contribute to a match, each with a weight for scoring
enum class MatchField(val weight: Float) {
    TITLE_EXACT(10f),
    TITLE_START(8f),
    TITLE_CONTAINS(5f),
    KEY_EXACT(9f),
    KEY_CONTAINS(6f),
    SUMMARY_EXACT(7f),
    SUMMARY_CONTAINS(4f),
    CATEGORY_MATCH(3f),
    FUZZY_MATCH(2f)
}

// Manages the search functionality for settings: caches all preferences,
// performs searches with debouncing, and scores results using smart matching.
class SettingsSearchManager(
    private val fragment: MainSettingsFragment,
    private val categoryFragmentMap: Map<String, Int>,
    private val categoryTitles: Map<String, Int>
) {
    
    private val scope = (fragment as LifecycleOwner).lifecycleScope
    private val searchDispatcher = Dispatchers.Default
    
    // Lazy cache of all preferences from all categories, built once
    private val preferenceCache: Deferred<List<PreferenceSearchData>> by lazy {
        scope.async(searchDispatcher) {
            buildPreferenceCache()
        }
    }
    
    private var searchJob: Job? = null
    
    companion object {
        private const val TAG = "SearchManager"
        private const val DEBOUNCE_DELAY_MS = 300L
        private const val MIN_QUERY_LENGTH = 2
        private const val FUZZY_MATCH_THRESHOLD = 0.45f
        private const val SIMILARITY_THRESHOLD = 0.70f
    }
    
    // Start building the cache as soon as the manager is created
    fun initializeCache() {
        scope.launch {
            try {
                val cache = preferenceCache.await()
                Log.d(TAG, "Cache initialized with ${cache.size} preferences")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing preference cache", e)
            }
        }
    }
    
    // Build the cache by iterating through all category XMLs and collecting preferences
    private suspend fun buildPreferenceCache(): List<PreferenceSearchData> = withContext(searchDispatcher) {
        try {
            val allPreferences = categoryFragmentMap.map { (categoryKey, xmlRes) ->
                async {
                    try {
                        val categoryTitle = withContext(Dispatchers.Main) {
                            fragment.getString(
                                categoryTitles[categoryKey] ?: com.deniscerri.ytdl.R.string.settings
                            )
                        }
                        
                        val preferences = withContext(Dispatchers.Main) {
                            val preferenceManager = PreferenceManager(fragment.requireContext())
                            val tempScreen = preferenceManager.inflateFromResource(
                                fragment.requireContext(),
                                xmlRes,
                                null
                            )
                            
                            collectPreferencesForCache(
                                tempScreen,
                                categoryKey,
                                categoryTitle,
                                xmlRes
                            )
                        }
                        
                        preferences
                    } catch (e: Exception) {
                        Log.e(TAG, "Error caching preferences from $categoryKey", e)
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
            
            allPreferences
        } catch (e: Exception) {
            Log.e(TAG, "Error building preference cache", e)
            emptyList()
        }
    }
    
    // Recursively collect all preferences from a PreferenceGroup, preserving hierarchy info
    private fun collectPreferencesForCache(
        group: PreferenceGroup,
        categoryKey: String,
        categoryTitle: String,
        xmlRes: Int,
        parentKey: String? = null,
        depth: Int = 0
    ): List<PreferenceSearchData> {
        val results = mutableListOf<PreferenceSearchData>()
        
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            
            val data = PreferenceSearchData(
                key = pref.key ?: "",
                title = pref.title?.toString() ?: "",
                summary = pref.summary?.toString() ?: "",
                categoryKey = categoryKey,
                categoryTitle = categoryTitle,
                xmlRes = xmlRes,
                isParent = pref is PreferenceGroup,
                depth = depth,
                parentKey = parentKey,
                originalPreference = pref
            )
            
            results.add(data)
            
            if (pref is PreferenceGroup) {
                results.addAll(
                    collectPreferencesForCache(
                        pref,
                        categoryKey,
                        categoryTitle,
                        xmlRes,
                        pref.key,
                        depth + 1
                    )
                )
            }
        }
        
        return results
    }
    
    // Public method to start a debounced search; cancels any pending search job
    fun searchWithDebounce(query: String, callback: (List<SearchMatch>) -> Unit) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            try {
                val results = performSearch(query)
                withContext(Dispatchers.Main) {
                    callback(results)
                }
            } catch (e: CancellationException) {
                // Expected when search is cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error performing search", e)
                withContext(Dispatchers.Main) {
                    callback(emptyList())
                }
            }
        }
    }
    
    // Internal suspend function that does the actual search
    private suspend fun performSearch(query: String): List<SearchMatch> = withContext(searchDispatcher) {
        if (query.isBlank() || query.length < MIN_QUERY_LENGTH) {
            return@withContext emptyList()
        }
        
        try {
            val cache = preferenceCache.await()
            searchPreferences(query, cache)
        } catch (e: Exception) {
            Log.e(TAG, "Error in search", e)
            emptyList()
        }
    }
    
    // Core search logic: for each cached preference, calculate a match score
    private fun searchPreferences(
        query: String,
        cache: List<PreferenceSearchData>
    ): List<SearchMatch> {
        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split(" ", "-", "_").filter { it.length >= 2 }
        
        val matches = cache.mapNotNull { data ->
            if (data.key.isBlank() && data.title.isBlank()) return@mapNotNull null
            
            val matchResult = calculateMatch(data, queryLower, queryWords)
            if (matchResult.score > 0f) matchResult else null
        }
        
        return matches.sortedByDescending { it.score }
    }
    
    // Calculate the relevance score for a single preference against the query
    private fun calculateMatch(
        data: PreferenceSearchData,
        queryLower: String,
        queryWords: List<String>
    ): SearchMatch {
        val matchedFields = mutableSetOf<MatchField>()
        var score = 0f
        
        val titleLower = data.title.lowercase()
        val summaryLower = data.summary.lowercase()
        val keyLower = data.key.lowercase()
        val categoryLower = data.categoryTitle.lowercase()
        
        // Title matching
        score += matchField(
            titleLower, queryLower, queryWords,
            MatchField.TITLE_EXACT,
            MatchField.TITLE_START,
            MatchField.TITLE_CONTAINS,
            matchedFields
        )
        
        // Key matching
        score += matchField(
            keyLower, queryLower, queryWords,
            MatchField.KEY_EXACT,
            null,
            MatchField.KEY_CONTAINS,
            matchedFields
        )
        
        // Summary matching
        score += matchField(
            summaryLower, queryLower, queryWords,
            MatchField.SUMMARY_EXACT,
            null,
            MatchField.SUMMARY_CONTAINS,
            matchedFields
        )
        
        // Category matching
        if (categoryLower.contains(queryLower)) {
            matchedFields.add(MatchField.CATEGORY_MATCH)
            score += MatchField.CATEGORY_MATCH.weight
        }
        
        // Fuzzy matching as fallback
        if (matchedFields.isEmpty() && queryLower.length >= 3) {
            val fuzzyScore = calculateBestFuzzyMatch(titleLower, queryLower)
            if (fuzzyScore > FUZZY_MATCH_THRESHOLD) {
                matchedFields.add(MatchField.FUZZY_MATCH)
                score += MatchField.FUZZY_MATCH.weight * fuzzyScore
            }
        }
        
        // Apply bonuses
        if (matchedFields.isNotEmpty()) {
            // Bonus for concise titles
            if (data.title.length < 50) {
                score *= (1f + (50f - data.title.length) / 100f)
            }
            
            // Bonus for top-level preferences
            if (data.depth == 0) {
                score *= 1.2f
            }
            
            // Bonus for multi-word query matches
            if (queryWords.size > 1) {
                val allWordsMatch = queryWords.all { word ->
                    titleLower.contains(word) || summaryLower.contains(word)
                }
                if (allWordsMatch) {
                    score *= 1.3f
                }
            }
        }
        
        return SearchMatch(data, score, matchedFields)
    }
    
    // Helper to score a specific field (title, key, summary) against the query
    private fun matchField(
        field: String,
        query: String,
        queryWords: List<String>,
        exactField: MatchField,
        startField: MatchField?,
        containsField: MatchField,
        matchedFields: MutableSet<MatchField>
    ): Float {
        var score = 0f
        
        when {
            field == query -> {
                matchedFields.add(exactField)
                score += exactField.weight
            }
            startField != null && field.startsWith(query) -> {
                matchedFields.add(startField)
                score += startField.weight
            }
            field.contains(query) -> {
                matchedFields.add(containsField)
                score += containsField.weight
            }
            checkPluralMatch(field, query) -> {
                matchedFields.add(containsField)
                score += containsField.weight * 0.95f
            }
            else -> {
                val fieldWords = field.split(" ", "-", "_", "(", ")", "[", "]")
                queryWords.forEach { queryWord ->
                    fieldWords.forEach { fieldWord ->
                        if (fieldWord.length >= 3 && queryWord.length >= 3) {
                            if (isCloseMatch(fieldWord, queryWord)) {
                                matchedFields.add(containsField)
                                score += containsField.weight * 0.8f
                            }
                        }
                    }
                }
            }
        }
        
        return score
    }
    
    // Check if the query matches a plural form of a word
    private fun checkPluralMatch(text: String, query: String): Boolean {
        if (text.contains("${query}s") || text.contains("${query}es")) return true
        if (query.endsWith("s") && text.contains(query.dropLast(1))) return true
        if (query.endsWith("es") && text.contains(query.dropLast(2))) return true
        
        return false
    }
    
    // Determine if two words are close matches (e.g., one character different)
    private fun isCloseMatch(text: String, query: String): Boolean {
        if (text.contains(query) || query.contains(text)) {
            return Math.abs(text.length - query.length) <= 2
        }
        
        if (text.length == query.length && text.length >= 3) {
            var differences = 0
            for (i in text.indices) {
                if (text[i] != query[i]) {
                    if (++differences > 1) return false
                }
            }
            return differences == 1
        }
        
        if (text.length >= 4 && query.length >= 4) {
            val maxLength = maxOf(text.length, query.length)
            val maxDistance = (maxLength * (1f - SIMILARITY_THRESHOLD)).toInt()
            
            val distance = levenshteinDistanceOptimized(text, query, maxDistance)
            if (distance < 0) return false
            
            val similarity = 1f - (distance.toFloat() / maxLength)
            return similarity >= SIMILARITY_THRESHOLD
        }
        
        return false
    }
    
    // Find the best fuzzy match score between the query and any word in the text
    private fun calculateBestFuzzyMatch(text: String, query: String): Float {
        if (text.isEmpty() || query.isEmpty()) return 0f
        
        val words = text.split(" ", "-", "_")
        var bestScore = 0f
        
        words.forEach { word ->
            if (word.length >= 3) {
                val score = calculateFuzzyScore(word, query)
                if (score > bestScore) {
                    bestScore = score
                }
            }
        }
        
        return bestScore
    }
    
    // Calculate a fuzzy similarity score between two strings (0-1)
    private fun calculateFuzzyScore(target: String, query: String): Float {
        if (target.isEmpty() || query.isEmpty()) return 0f
        
        val maxLength = maxOf(target.length, query.length)
        val maxDistance = (maxLength * 0.6f).toInt()
        
        val distance = levenshteinDistanceOptimized(target, query, maxDistance)
        if (distance < 0) return 0f
        
        return 1f - (distance.toFloat() / maxLength)
    }
    
    // Optimized Levenshtein distance with early exit when exceeding threshold
    private fun levenshteinDistanceOptimized(s1: String, s2: String, threshold: Int = Int.MAX_VALUE): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        if (len1 == 0) return if (len2 <= threshold) len2 else -1
        if (len2 == 0) return if (len1 <= threshold) len1 else -1
        
        val (shorter, longer) = if (len1 <= len2) s1 to s2 else s2 to s1
        val shortLen = shorter.length
        val longLen = longer.length
        
        var prevRow = IntArray(shortLen + 1) { it }
        var currRow = IntArray(shortLen + 1)
        
        for (i in 1..longLen) {
            currRow[0] = i
            var minInRow = i
            
            for (j in 1..shortLen) {
                val cost = if (shorter[j - 1] == longer[i - 1]) 0 else 1
                
                currRow[j] = minOf(
                    prevRow[j] + 1,
                    currRow[j - 1] + 1,
                    prevRow[j - 1] + cost
                )
                
                if (currRow[j] < minInRow) {
                    minInRow = currRow[j]
                }
            }
            
            if (minInRow > threshold) {
                return -1
            }
            
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        
        return if (prevRow[shortLen] <= threshold) prevRow[shortLen] else -1
    }
    
    // Cancel any ongoing search job
    fun cancelPendingSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    // Clear the search job (called when fragment is destroyed)
    fun clearCache() {
        searchJob?.cancel()
        searchJob = null
    }
}