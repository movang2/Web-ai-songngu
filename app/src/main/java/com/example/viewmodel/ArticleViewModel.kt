package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ArticleEntity
import com.example.data.ArticleRepository
import com.example.data.WebBookmarkEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import android.util.Log
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class BilingualParagraph(
    val korean: String,
    val vietnamese: String,
    val isTranslating: Boolean = false,
    val error: String? = null
)

enum class DictionaryType(val label: String, val baseUrl: String) {
    HAN_VIET("Hàn-Việt", "https://korean.dict.naver.com/kovidict/#/search?query="),
    HAN_ENG("Hàn-Anh", "https://dict.naver.com/koendict/#/search?query="),
    HAN_HAN("Hàn-Hàn", "https://kr.dict.naver.com/#/search?query=")
}

class ArticleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ArticleRepository

    val bookmarks: StateFlow<List<ArticleEntity>>
    val history: StateFlow<List<ArticleEntity>>
    val webBookmarks: StateFlow<List<WebBookmarkEntity>>

    data class AiChatMessage(
        val id: String = java.util.UUID.randomUUID().toString(),
        val text: String,
        val isUser: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun serializeChatHistory(history: List<AiChatMessage>): String {
        val array = org.json.JSONArray()
        for (msg in history) {
            val obj = org.json.JSONObject()
            obj.put("id", msg.id)
            obj.put("text", msg.text)
            obj.put("isUser", msg.isUser)
            obj.put("timestamp", msg.timestamp)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeChatHistory(jsonStr: String?): List<AiChatMessage> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<AiChatMessage>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AiChatMessage(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("isUser"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ArticleViewModel", "Error deserializing chat history", e)
        }
        return list
    }

    private fun saveChatHistoryToDatabase(updatedChat: List<AiChatMessage>) {
        val chatJson = serializeChatHistory(updatedChat)
        val article = _currentArticle.value
        if (article != null && article.id > 0) {
            val updated = article.copy(chatHistoryJson = chatJson)
            _currentArticle.value = updated
            viewModelScope.launch {
                repository.updateArticle(updated)
            }
        }
        
        val url = article?.url
        if (!url.isNullOrEmpty()) {
            viewModelScope.launch {
                val existingWebBookmark = repository.getWebBookmarkByUrl(url)
                if (existingWebBookmark != null) {
                    val updatedWeb = existingWebBookmark.copy(chatHistoryJson = chatJson)
                    repository.insertWebBookmark(updatedWeb)
                }
            }
        }
    }

    private val _aiChatHistory = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiChatHistory: StateFlow<List<AiChatMessage>> = _aiChatHistory.asStateFlow()

    private val _isAnsweringQuestion = MutableStateFlow(false)
    val isAnsweringQuestion: StateFlow<Boolean> = _isAnsweringQuestion.asStateFlow()

    private val _currentArticle = MutableStateFlow<ArticleEntity?>(null)
    val currentArticle: StateFlow<ArticleEntity?> = _currentArticle.asStateFlow()

    private val _selectedWord = MutableStateFlow<String?>(null)
    val selectedWord: StateFlow<String?> = _selectedWord.asStateFlow()

    private val _dictionaryType = MutableStateFlow(DictionaryType.HAN_VIET)
    val dictionaryType: StateFlow<DictionaryType> = _dictionaryType.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _bilingualParagraphs = MutableStateFlow<List<BilingualParagraph>>(emptyList())
    val bilingualParagraphs: StateFlow<List<BilingualParagraph>> = _bilingualParagraphs.asStateFlow()

    private val prefs = application.getSharedPreferences("reader_prefs", android.content.Context.MODE_PRIVATE)

    private val _koFontSize = MutableStateFlow(prefs.getFloat("ko_font_size", 19f))
    val koFontSize: StateFlow<Float> = _koFontSize.asStateFlow()

    private val _viFontSize = MutableStateFlow(prefs.getFloat("vi_font_size", 16f))
    val viFontSize: StateFlow<Float> = _viFontSize.asStateFlow()

    fun updateKoFontSize(size: Float) {
        val newSize = size.coerceIn(10f, 36f)
        _koFontSize.value = newSize
        prefs.edit().putFloat("ko_font_size", newSize).apply()
    }

    fun updateViFontSize(size: Float) {
        val newSize = size.coerceIn(8f, 32f)
        _viFontSize.value = newSize
        prefs.edit().putFloat("vi_font_size", newSize).apply()
    }

    private var lastArticleId: Int = -2
    private var lastArticleUrl: String? = "__init__"

    fun serializeBilingualParagraphs(paragraphs: List<BilingualParagraph>): String {
        val array = org.json.JSONArray()
        for (p in paragraphs) {
            val obj = org.json.JSONObject()
            obj.put("korean", p.korean)
            obj.put("vietnamese", p.vietnamese)
            obj.put("error", p.error ?: "")
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeBilingualParagraphs(jsonStr: String?): List<BilingualParagraph> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<BilingualParagraph>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BilingualParagraph(
                        korean = obj.getString("korean"),
                        vietnamese = obj.optString("vietnamese", ""),
                        isTranslating = false,
                        error = obj.optString("error", "").ifEmpty { null }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ArticleViewModel", "Error deserializing bilingual paragraphs", e)
        }
        return list
    }

    private fun saveBilingualParagraphsToDatabase(paragraphs: List<BilingualParagraph>) {
        val bilingualJson = serializeBilingualParagraphs(paragraphs)
        val article = _currentArticle.value
        if (article != null && article.id > 0) {
            val updated = article.copy(bilingualJson = bilingualJson)
            _currentArticle.value = updated
            viewModelScope.launch {
                repository.updateArticle(updated)
            }
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ArticleRepository(database.articleDao())

        bookmarks = repository.bookmarks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        history = repository.history
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        webBookmarks = repository.webBookmarks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            _currentArticle.collectLatest { article ->
                if (article == null) {
                    _bilingualParagraphs.value = emptyList()
                    lastArticleId = -2
                    lastArticleUrl = "__init__"
                } else {
                    val isSameArticle = (article.id > 0 && article.id == lastArticleId) || 
                                        (!article.url.isNullOrEmpty() && article.url == lastArticleUrl)
                    if (isSameArticle) {
                        return@collectLatest
                    }
                    
                    lastArticleId = article.id
                    lastArticleUrl = article.url

                    if (!article.bilingualJson.isNullOrEmpty()) {
                        val stored = deserializeBilingualParagraphs(article.bilingualJson)
                        if (stored.isNotEmpty()) {
                            _bilingualParagraphs.value = stored
                            return@collectLatest
                        }
                    }

                    val sentences = splitIntoKoreanSentences(article.content)
                    
                    _bilingualParagraphs.value = sentences.map { contentStr ->
                        BilingualParagraph(
                            korean = contentStr,
                            vietnamese = ""
                        )
                    }
                    
                    // Automatically trigger Google Translate for all paragraphs
                    translateAllParagraphs()
                }
            }
        }
    }

    fun splitIntoKoreanSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        
        val paragraphs = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        val allSentences = mutableListOf<String>()
        
        for (paragraph in paragraphs) {
            val sentences = mutableListOf<String>()
            val currentSentence = StringBuilder()
            var i = 0
            val len = paragraph.length
            
            while (i < len) {
                val char = paragraph[i]
                currentSentence.append(char)
                
                // Check if we hit a potential sentence terminator: ., ?, !
                if (char == '.' || char == '?' || char == '!') {
                    // Check for ellipsis: e.g., "..." or "...."
                    var j = i + 1
                    while (j < len && paragraph[j] == char) {
                        currentSentence.append(paragraph[j])
                        i = j
                        j++
                    }
                    
                    // Check if it's a decimal number, date, URL or email domain.
                    var isSpecialDelimiter = false
                    if (char == '.') {
                        val prevChar = if (i - 1 >= 0) paragraph[i - 1] else ' '
                        val nextChar = if (i + 1 < len) paragraph[i + 1] else ' '
                        if (prevChar.isDigit() && nextChar.isDigit()) {
                            isSpecialDelimiter = true
                        }
                        // Keep domain names, emails like mbc.co.kr intact:
                        // If the characters on both sides of the dot are letters, digits, @, or _,
                        // and at least one of them is an English letter, we treat it as an email/URL/abbreviation.
                        val isEnglishOrSpecialAround = (prevChar.isLetterOrDigit() || prevChar == '@' || prevChar == '_') && 
                                (nextChar.isLetterOrDigit() || nextChar == '@' || nextChar == '_') &&
                                (prevChar in 'a'..'z' || prevChar in 'A'..'Z' || nextChar in 'a'..'z' || nextChar in 'A'..'Z' || prevChar == '@' || nextChar == '@')
                        if (isEnglishOrSpecialAround) {
                            isSpecialDelimiter = true
                        }
                    }
                    
                    if (!isSpecialDelimiter) {
                        // Now, consume any trailing spaces, closing parentheses, brackets, or quotes.
                        val closingChars = setOf(')', ']', '}', '"', '\'', '”', '』', '」', '>')
                        var k = i + 1
                        while (k < len && (paragraph[k] in closingChars || paragraph[k] == ' ')) {
                            currentSentence.append(paragraph[k])
                            i = k
                            k++
                        }
                        
                        val sentenceStr = currentSentence.toString().trim()
                        if (sentenceStr.isNotEmpty()) {
                            sentences.add(sentenceStr)
                        }
                        currentSentence.setLength(0)
                    }
                }
                i++
            }
            
            if (currentSentence.isNotEmpty()) {
                val sentenceStr = currentSentence.toString().trim()
                if (sentenceStr.isNotEmpty()) {
                    sentences.add(sentenceStr)
                }
            }
            
            // Merge sentences that are too short (less than 10 characters)
            val mergedSentences = mutableListOf<String>()
            for (s in sentences) {
                if (mergedSentences.isEmpty()) {
                    mergedSentences.add(s)
                } else {
                    val last = mergedSentences.last()
                    // If the previous or current sentence is less than 10 characters, merge them
                    if (last.length < 10 || s.length < 10) {
                        val delimiter = if (last.endsWith(" ") || s.startsWith(" ")) "" else " "
                        mergedSentences[mergedSentences.size - 1] = last + delimiter + s
                    } else {
                        mergedSentences.add(s)
                    }
                }
            }
            
            allSentences.addAll(mergedSentences)
        }
        
        return allSentences
    }

    // Helper to secure URL encoding for searching Korean words in Naver View
    fun getDictionaryUrl(): String? {
        val word = _selectedWord.value
        if (word == null) {
            return when (_dictionaryType.value) {
                DictionaryType.HAN_VIET -> "https://korean.dict.naver.com/kovidict/#/main"
                DictionaryType.HAN_ENG -> "https://dict.naver.com/koendict/#/main"
                DictionaryType.HAN_HAN -> "https://kr.dict.naver.com/#/main"
            }
        }
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            _dictionaryType.value.baseUrl + encoded
        } catch (e: Exception) {
            _dictionaryType.value.baseUrl + word
        }
    }

    fun loadCustomArticle(title: String, content: String, url: String? = null) {
        val trimmedTitle = title.trim().ifEmpty { "Bài học không tên" }
        val trimmedContent = content.trim()

        if (trimmedContent.isEmpty()) {
            viewModelScope.launch {
                _toastMessage.emit("Vui lòng nhập nội dung tiếng Hàn!")
            }
            return
        }

        viewModelScope.launch {
            var restoredChatHistory: List<AiChatMessage> = emptyList()
            if (url != null) {
                val cleanUrl = url.trim()
                val existing = repository.getByUrl(cleanUrl)
                var restoredChatHistoryJson = existing?.chatHistoryJson
                if (restoredChatHistoryJson.isNullOrEmpty()) {
                    val webBookmark = repository.getWebBookmarkByUrl(cleanUrl)
                    restoredChatHistoryJson = webBookmark?.chatHistoryJson
                }
                restoredChatHistory = deserializeChatHistory(restoredChatHistoryJson)

                if (existing != null) {
                    val updatedDb = existing.copy(
                        isHistory = true,
                        content = if (existing.isBookmarked) existing.content else "",
                        timestamp = System.currentTimeMillis()
                    )
                    repository.updateArticle(updatedDb)
                    _currentArticle.value = updatedDb.copy(content = trimmedContent)
                } else {
                    val newArticleDb = ArticleEntity(
                        title = trimmedTitle,
                        content = "", // save with empty content for history, as requested
                        isHistory = true,
                        isBookmarked = false,
                        url = cleanUrl,
                        timestamp = System.currentTimeMillis(),
                        chatHistoryJson = restoredChatHistoryJson
                    )
                    val id = repository.insertArticle(newArticleDb)
                    _currentArticle.value = newArticleDb.copy(id = id.toInt(), content = trimmedContent)
                }
            } else {
                val existing = repository.getByContent(trimmedTitle, trimmedContent)
                restoredChatHistory = deserializeChatHistory(existing?.chatHistoryJson)
                if (existing != null) {
                    val updated = existing.copy(
                        isHistory = true,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.updateArticle(updated)
                    _currentArticle.value = updated
                } else {
                    val newArticle = ArticleEntity(
                        title = trimmedTitle,
                        content = trimmedContent,
                        isHistory = true,
                        timestamp = System.currentTimeMillis()
                    )
                    val id = repository.insertArticle(newArticle)
                    _currentArticle.value = newArticle.copy(id = id.toInt())
                }
            }
            _selectedWord.value = null
            _aiChatHistory.value = restoredChatHistory
        }
    }

    fun toggleBookmark() {
        val article = _currentArticle.value
        if (article == null) {
            viewModelScope.launch {
                _toastMessage.emit("Hãy mở hoặc tạo một bài viết trước khi đánh dấu!")
            }
            return
        }

        viewModelScope.launch {
            val newlyBookmarked = !article.isBookmarked
            val updated = article.copy(
                isBookmarked = newlyBookmarked,
                content = if (newlyBookmarked) article.content else (if (article.url != null) "" else article.content),
                timestamp = System.currentTimeMillis(),
                chatHistoryJson = if (newlyBookmarked) serializeChatHistory(_aiChatHistory.value) else article.chatHistoryJson,
                bilingualJson = serializeBilingualParagraphs(_bilingualParagraphs.value)
            )
            repository.updateArticle(updated)
            _currentArticle.value = updated.copy(content = article.content)
            
            val msg = if (newlyBookmarked) "Đã đánh dấu bài viết này! ⭐" else "Đã xóa khỏi danh sách đánh dấu"
            _toastMessage.emit(msg)
        }
    }

    fun loadArticle(article: ArticleEntity) {
        viewModelScope.launch {
            // Update timestamp & ensure history is active
            val updated = article.copy(
                isHistory = true,
                timestamp = System.currentTimeMillis()
            )
            repository.updateArticle(updated)
            _currentArticle.value = updated
            _selectedWord.value = null
            _aiChatHistory.value = deserializeChatHistory(article.chatHistoryJson)
        }
    }

    fun goHome() {
        _currentArticle.value = null
        _selectedWord.value = null
        _aiChatHistory.value = emptyList()
    }

    fun deleteBookmark(article: ArticleEntity) {
        viewModelScope.launch {
            val updated = article.copy(
                isBookmarked = false,
                content = if (article.url != null) "" else article.content
            )
            if (!updated.isBookmarked && !updated.isHistory) {
                repository.deleteArticle(updated)
            } else {
                repository.updateArticle(updated)
            }
            if (_currentArticle.value?.id == article.id) {
                _currentArticle.value = _currentArticle.value?.copy(isBookmarked = false)
            }
            _toastMessage.emit("Đã bỏ đánh dấu bài báo.")
        }
    }

    fun deleteHistory(article: ArticleEntity) {
        viewModelScope.launch {
            val updated = article.copy(isHistory = false)
            if (!updated.isBookmarked && !updated.isHistory) {
                repository.deleteArticle(updated)
            } else {
                repository.updateArticle(updated)
            }
            if (_currentArticle.value?.id == article.id) {
                _currentArticle.value = _currentArticle.value?.copy(isHistory = false)
            }
            _toastMessage.emit("Đã xóa khỏi lịch sử.")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _toastMessage.emit("Đã xóa toàn bộ lịch sử đọc bài!")
        }
    }

    fun clearAllBookmarks() {
        viewModelScope.launch {
            repository.clearAllBookmarks()
            _toastMessage.emit("Đã xóa toàn bộ các đánh dấu bài đọc!")
        }
    }

    fun selectWord(word: String) {
        _selectedWord.value = word
    }

    fun setDictionaryType(type: DictionaryType) {
        _dictionaryType.value = type
    }

    fun loadUrl(webUrl: String) {
        var cleanUrl = webUrl.trim()
        if (cleanUrl.isEmpty()) return

        val urlToFetch = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fetchedArticle = withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url(urlToFetch)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("Mã lỗi: ${response.code}")
                        val html = response.body?.string() ?: ""
                        val doc = org.jsoup.Jsoup.parse(html, urlToFetch)
                        
                        // Extract title from HTML, fallback to hostname/url if empty
                        val rawTitle = doc.title().trim()
                        val title = if (rawTitle.isEmpty()) {
                            try {
                                java.net.URL(urlToFetch).host
                            } catch (e: Exception) {
                                urlToFetch
                            }
                        } else {
                            rawTitle
                        }
                        
                        // Extract paragraphs or main text
                        val body = doc.body()
                        val contentBuilder = java.lang.StringBuilder()
                        
                        if (body != null) {
                            // Strip script and style tags completely
                            body.select("script, style, iframe, footer, nav, header").remove()
                            
                            // Select paragraph-like text tags
                            val textElements = body.select("p, h1, h2, h3, h4, li, div.article_body, div.article p, .news_end")
                            if (textElements.isNotEmpty()) {
                                var count = 0
                                for (el in textElements) {
                                    val text = el.text().trim()
                                    // Filter out short menu items, social links, or empty lines
                                    if (text.isNotEmpty() && text.length > 8) {
                                        contentBuilder.append(text).append("\n\n")
                                        count++
                                    }
                                }
                                if (count == 0) {
                                    contentBuilder.append(body.text())
                                }
                            } else {
                                contentBuilder.append(body.text())
                            }
                        }
                        
                        val contentText = contentBuilder.toString().trim()
                        if (contentText.isEmpty()) {
                            throw java.io.IOException("Không tìm thấy nội dung văn bản đọc được trên trang web.")
                        }

                        ArticleEntity(
                            title = title,
                            content = contentText,
                            isHistory = true,
                            url = urlToFetch,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }

                // Check if existing
                val existing = repository.getByUrl(urlToFetch)
                if (existing != null) {
                    val updated = existing.copy(
                        title = fetchedArticle.title,
                        content = fetchedArticle.content,
                        isHistory = true,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.updateArticle(updated)
                    _currentArticle.value = updated
                } else {
                    val id = repository.insertArticle(fetchedArticle)
                    _currentArticle.value = fetchedArticle.copy(id = id.toInt())
                }
                _selectedWord.value = null
                _toastMessage.emit("Tải trang thành công!")
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi tải trang web: ${e.localizedMessage ?: "Vui lòng kiểm tra lại URL"}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _isExtractingVocab = MutableStateFlow(false)
    val isExtractingVocab: StateFlow<Boolean> = _isExtractingVocab.asStateFlow()

    private suspend fun executeGeminiRequest(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Thiếu Gemini API Key! Hãy cấu hình trong mục Secrets tại AI Studio.")
        }

        // List of models and API versions to try in fallback sequence
        val attempts = listOf(
            Pair("gemini-3.5-flash", "v1beta"),
            Pair("gemini-3.5-flash", "v1"),
            Pair("gemini-2.5-flash", "v1beta"),
            Pair("gemini-2.5-flash", "v1"),
            Pair("gemini-flash-latest", "v1beta"),
            Pair("gemini-flash-latest", "v1"),
            Pair("gemini-2.0-flash", "v1beta"),
            Pair("gemini-2.0-flash", "v1"),
            Pair("gemini-1.5-flash", "v1beta"),
            Pair("gemini-1.5-flash", "v1")
        )

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonRequest = org.json.JSONObject().apply {
            val contentsArray = org.json.JSONArray().apply {
                val contentObj = org.json.JSONObject().apply {
                    val partsArray = org.json.JSONArray().apply {
                        val partObj = org.json.JSONObject().apply {
                            put("text", prompt)
                        }
                        put(partObj)
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonRequest.toString().toRequestBody(mediaType)
        val attemptErrors = mutableListOf<String>()

        for ((model, apiVersion) in attempts) {
            try {
                val url = "https://generativelanguage.googleapis.com/$apiVersion/models/$model:generateContent?key=$apiKey"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorContent = response.body?.string() ?: ""
                        throw Exception("HTTP ${response.code}: $errorContent")
                    }
                    val bodyString = response.body?.string() ?: throw Exception("Phản hồi trống từ server")
                    val jsonResponse = org.json.JSONObject(bodyString)
                    val candidates = jsonResponse.getJSONArray("candidates")
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentRes = firstCandidate.getJSONObject("content")
                    val parts = contentRes.getJSONArray("parts")
                    val firstPart = parts.getJSONObject(0)
                    return@withContext firstPart.getString("text")
                }
            } catch (e: Exception) {
                val errMsg = "$model ($apiVersion): ${e.localizedMessage ?: e.message}"
                Log.w("ArticleViewModel", errMsg)
                attemptErrors.add(errMsg)
                kotlinx.coroutines.delay(100) // Small delay before falling back
            }
        }
        val detailedErrors = attemptErrors.joinToString(separator = "\n")
        throw Exception("Kết nối Gemini thất bại cho toàn bộ mẫu thử:\n$detailedErrors")
    }

    fun extractVocabWithAI(article: ArticleEntity) {
        viewModelScope.launch {
            _isExtractingVocab.value = true
            try {
                val content = article.content
                if (content.trim().isEmpty()) {
                    _toastMessage.emit("Nội dung bài viết rỗng, không thể trích xuất!")
                    return@launch
                }

                val prompt = """
                    Bạn là một giảng viên tiếng Hàn chuyên nghiệp. Hãy trích xuất khoảng 15 đến 20 từ vựng quan trọng hoặc hữu ích nhất bằng tiếng Hàn từ văn bản bài học dưới đây để người học nâng cao từ vựng.
                    
                    Văn bản bài học:
                    "$content"
                    
                    Hãy định dạng kết quả bằng Tiếng Việt một cách trực quan sinh động dưới dạng danh sách markdown để hiển thị trực tiếp.
                    Yêu cầu cấu trúc hiển thị cực kỳ ngắn gọn cho từng từ như sau (không kèm phiên âm đọc, không kèm từ loại, không kèm ví dụ):
                    • **Từ tiếng Hàn** : Ý nghĩa Tiếng Việt chính xác.
                    Ví dụ: • **종전** : Sự kết thúc chiến tranh.
                    
                    Tuyệt đối không thêm phiên âm la-tinh, không thêm loại từ như [Danh từ], [Động từ], không thêm câu ví dụ hay bất cứ lời chào hỏi rườm rà nào khác. Chỉ hiển thị danh sách từ vựng tinh gọn theo đúng cấu trúc trên.
                """.trimIndent()

                val aiResult = executeGeminiRequest(prompt)

                // Update database
                val updated = article.copy(
                    importantVocab = aiResult,
                    timestamp = System.currentTimeMillis()
                )
                repository.updateArticle(updated)
                _currentArticle.value = updated
                _toastMessage.emit("Trích xuất thông minh AI thành công!")
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi trích xuất AI: ${e.localizedMessage}")
            } finally {
                _isExtractingVocab.value = false
            }
        }
    }

    fun askAIAboutArticle(question: String) {
        val article = _currentArticle.value
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty()) return
        if (article == null) {
            viewModelScope.launch {
                _toastMessage.emit("Không tìm thấy bài viết hiện tại để trao đổi!")
            }
            return
        }

        viewModelScope.launch {
            _isAnsweringQuestion.value = true
            // Append user question to history
            val userMsg = AiChatMessage(text = cleanQuestion, isUser = true)
            val updatedHistory = _aiChatHistory.value + userMsg
            _aiChatHistory.value = updatedHistory
            saveChatHistoryToDatabase(updatedHistory)

            try {
                val content = article.content
                val prompt = """
                    Bạn là một giáo viên tiếng Hàn và trợ lý giảng dạy nhiệt tình, chuyên nghiệp.
                    Hãy trả lời thắc mắc của học sinh dựa trên nội dung bài học dưới đây một cách chi tiết và chính xác.
                    
                    --- NỘI DUNG BÀI HỌC ---
                    $content
                    
                    --- CÂU HỎI CỦA HỌC SINH ---
                    $cleanQuestion
                    
                    Hãy trả lời bằng Tiếng Việt và phân tích từ vựng/ngữ pháp rõ ràng. Sử dụng Markdown đẹp mắt (bôi đậm từ Hàn ngữ quan trọng, dùng danh sách, cấu trúc gạch đầu dòng và đoạn chia rõ ràng để tăng tính thẩm mỹ).
                """.trimIndent()

                val aiResponseText = executeGeminiRequest(prompt)

                // Append AI reply to history
                val aiMsg = AiChatMessage(text = aiResponseText, isUser = false)
                val newHistory = _aiChatHistory.value + aiMsg
                _aiChatHistory.value = newHistory
                saveChatHistoryToDatabase(newHistory)
            } catch (e: Exception) {
                val errorMsg = AiChatMessage(text = "Lỗi phản hồi từ AI: ${e.localizedMessage}", isUser = false)
                val newHistory = _aiChatHistory.value + errorMsg
                _aiChatHistory.value = newHistory
                saveChatHistoryToDatabase(newHistory)
            } finally {
                _isAnsweringQuestion.value = false
            }
        }
    }

    fun toggleWebBookmark(title: String, url: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return

        viewModelScope.launch {
            val existing = repository.getWebBookmarkByUrl(cleanUrl)
            if (existing != null) {
                repository.deleteWebBookmarkById(existing.id)
                _toastMessage.emit("Đã bỏ đánh dấu trang web này!")
            } else {
                val bookmark = WebBookmarkEntity(
                    title = title.trim().ifEmpty { "Trang web lưu trữ" },
                    url = cleanUrl,
                    timestamp = System.currentTimeMillis(),
                    chatHistoryJson = serializeChatHistory(_aiChatHistory.value)
                )
                repository.insertWebBookmark(bookmark)
                _toastMessage.emit("Đã lưu trang web vào dấu trang!")
            }
        }
    }

    fun deleteWebBookmark(bookmark: WebBookmarkEntity) {
        viewModelScope.launch {
            repository.deleteWebBookmarkById(bookmark.id)
            _toastMessage.emit("Đã xóa dấu trang.")
        }
    }

    fun extractAndLoadArticleFromUrl(url: String, onFinished: () -> Unit = {}) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            viewModelScope.launch {
                _toastMessage.emit("Đường dẫn không hợp lệ!")
                onFinished()
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val doc = org.jsoup.Jsoup.connect(cleanUrl)
                        .userAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                        .timeout(15000)
                        .get()

                    // Remove noise tags first to avoid their contents contaminating fallback content
                    doc.select("script, style, nav, footer, aside, ads, iframe, .comment, .reply, header, noscript, button, a.btn, .social_share").remove()

                    // 1. Title Extraction
                    // Primary target class for MBC News: h3.art_title
                    var title = doc.select("h3.art_title").text().trim()
                    if (title.isEmpty()) {
                        // Try other header selectors
                        val h1Text = doc.select("h1").firstOrNull()?.text()?.trim() ?: ""
                        if (h1Text.isNotEmpty()) {
                            title = h1Text
                        } else {
                            val h2Text = doc.select("h2").firstOrNull()?.text()?.trim() ?: ""
                            if (h2Text.isNotEmpty()) {
                                title = h2Text
                            }
                        }
                    }
                    if (title.isEmpty()) {
                        title = doc.title().trim()
                    }
                    if (title.isEmpty()) {
                        title = "Bài học không tên"
                    }

                    // 2. Content Extraction
                    // Primary target for MBC: div.news_txt
                    var content = doc.select("div.news_txt").text().trim()

                    // Fallbacks if not found or text is too short
                    if (content.isEmpty() || content.length < 50) {
                        val articleSelectors = listOf(
                            "article", "main", ".article_body", ".article", ".news_end",
                            "#articleBody", "#article_body", "#newsct_article", "#articleBodyContents",
                            ".article-body", ".post-content", ".content", "div[itemprop='articleBody']",
                            ".news_body", ".news_content", ".view_content", ".view_content_text"
                        )
                        for (selector in articleSelectors) {
                            val elementText = doc.select(selector).text().trim()
                            if (elementText.length > content.length) {
                                content = elementText
                            }
                        }
                    }

                    // If content is still empty, fall back to extracting paragraphs
                    if (content.isEmpty() || content.length < 50) {
                        val pTexts = doc.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
                        if (pTexts.isNotEmpty()) {
                            // Filter out small nav-like or garbage paragraphs if possible, otherwise join them
                            content = pTexts.joinToString("\n\n")
                        }
                    }

                    // Ultimate fallback to entire text of body
                    if (content.isEmpty()) {
                        content = doc.body().text().trim()
                    }

                    Pair(title, content)
                }

                val extractedTitle = result.first
                val extractedContent = result.second

                if (extractedContent.isNotBlank() && extractedContent.length > 10) {
                    loadCustomArticle(extractedTitle, extractedContent, cleanUrl)
                    _toastMessage.emit("Đã trích xuất nội dung bài viết thành công! ✨")
                } else {
                    _toastMessage.emit("Không tìm thấy nội dung bài báo chính để trích xuất.")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Lỗi trích xuất trang web: ${e.localizedMessage}")
            } finally {
                _isLoading.value = false
                onFinished()
            }
        }
    }

    private suspend fun translateGtx(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=ko&tl=vi&dt=t&dj=1&q=$encodedText"

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Google Translate API error: Lỗi ${response.code}")
            }
            val responseBody = response.body?.string() ?: ""
            if (responseBody.isBlank()) return@withContext ""
            
            val json = org.json.JSONObject(responseBody)
            if (!json.has("sentences")) return@withContext ""
            
            val sentences = json.getJSONArray("sentences")
            val sb = java.lang.StringBuilder()
            for (i in 0 until sentences.length()) {
                val sentence = sentences.getJSONObject(i)
                if (sentence.has("trans")) {
                    sb.append(sentence.getString("trans"))
                }
            }
            sb.toString()
        }
    }

    fun translateAllParagraphs() {
        val currentList = _bilingualParagraphs.value
        if (currentList.isEmpty()) return

        viewModelScope.launch {
            // Set all to translating state immediately so progress bars are visible
            _bilingualParagraphs.value = currentList.map { it.copy(isTranslating = true, error = null) }

            try {
                for (idx in currentList.indices) {
                    val paragraph = _bilingualParagraphs.value.getOrNull(idx) ?: continue
                    
                    var translatedText = ""
                    try {
                        translatedText = translateGtx(paragraph.korean)
                    } catch (e: Exception) {
                        Log.e("ArticleViewModel", "Translate error for paragraph at index $idx", e)
                    }

                    val updatedList = _bilingualParagraphs.value.toMutableList()
                    if (idx < updatedList.size) {
                        updatedList[idx] = updatedList[idx].copy(
                            vietnamese = translatedText.trim(),
                            isTranslating = false,
                            error = if (translatedText.isEmpty()) "Không dịch được đoạn này. Vui lòng thử lại hoặc dịch đơn lẻ." else null
                        )
                        val newList = updatedList.toList()
                        _bilingualParagraphs.value = newList
                        saveBilingualParagraphsToDatabase(newList)
                    }

                    // Leave a 500ms pause after each call to prevent being blocked by the Google Translate API
                    if (idx < currentList.size - 1) {
                        kotlinx.coroutines.delay(500)
                    }
                }
            } catch (e: Exception) {
                val finalRestoredList = _bilingualParagraphs.value.map {
                    if (it.isTranslating) {
                        it.copy(isTranslating = false, error = e.localizedMessage ?: "Lỗi dịch")
                    } else {
                        it
                    }
                }
                _bilingualParagraphs.value = finalRestoredList
                _toastMessage.emit("Lỗi dịch hàng loạt: ${e.localizedMessage}")
            }
        }
    }

    fun translateSingleParagraph(index: Int) {
        val currentList = _bilingualParagraphs.value.toMutableList()
        val p = currentList.getOrNull(index) ?: return

        viewModelScope.launch {
            currentList[index] = p.copy(isTranslating = true, error = null)
            _bilingualParagraphs.value = currentList.toList()

            try {
                val translatedText = translateGtx(p.korean)

                val updatedList = _bilingualParagraphs.value.toMutableList()
                if (index < updatedList.size) {
                    updatedList[index] = p.copy(
                        vietnamese = translatedText.trim(),
                        isTranslating = false,
                        error = if (translatedText.isEmpty()) "Không có kết quả dịch" else null
                    )
                    val newList = updatedList.toList()
                    _bilingualParagraphs.value = newList
                    saveBilingualParagraphsToDatabase(newList)
                }
            } catch (e: Exception) {
                val updatedList = _bilingualParagraphs.value.toMutableList()
                if (index < updatedList.size) {
                    updatedList[index] = p.copy(
                        isTranslating = false,
                        error = e.localizedMessage ?: "Lỗi"
                    )
                    val newList = updatedList.toList()
                    _bilingualParagraphs.value = newList
                    saveBilingualParagraphsToDatabase(newList)
                }
            }
        }
    }
}
