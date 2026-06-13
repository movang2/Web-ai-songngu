package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ReadingPlayMode { KO_ONLY, VI_ONLY, KO_THEN_VI, VI_THEN_KO }

data class ReadingParagraph(
    val id: Int,
    val korean: String,
    val vietnamese: String
)

data class TtsSegment(val text: String, val isKorean: Boolean)

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var segmentsToPlay = listOf<TtsSegment>()
    private var currentSegmentIndex = -1
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var isReadingModeActive = false
    private var readingParagraphs = listOf<ReadingParagraph>()
    private var currentParaIndex = -1
    private var currentParaPhase = 0 // 0 = first lang, 1 = second lang

    private val _currentPlayingParagraphIndex = MutableStateFlow<Int?>(null)
    val currentPlayingParagraphIndex: StateFlow<Int?> = _currentPlayingParagraphIndex.asStateFlow()

    // Persistent TTS configuration state flows
    private val prefs = context.applicationContext.getSharedPreferences("tts_settings", Context.MODE_PRIVATE)

    private val _readingPlayMode = MutableStateFlow<ReadingPlayMode>(
        try {
            ReadingPlayMode.valueOf(prefs.getString("reading_play_mode", ReadingPlayMode.KO_THEN_VI.name) ?: ReadingPlayMode.KO_THEN_VI.name)
        } catch (e: Exception) {
            ReadingPlayMode.KO_THEN_VI
        }
    )
    val readingPlayMode: StateFlow<ReadingPlayMode> = _readingPlayMode.asStateFlow()

    fun setReadingPlayMode(mode: ReadingPlayMode) {
        _readingPlayMode.value = mode
        prefs.edit().putString("reading_play_mode", mode.name).apply()
    }

    private val _selectedKoVoice = MutableStateFlow<String?>(prefs.getString("ko_voice", null))
    val selectedKoVoice: StateFlow<String?> = _selectedKoVoice.asStateFlow()

    private val _selectedViVoice = MutableStateFlow<String?>(prefs.getString("vi_voice", null))
    val selectedViVoice: StateFlow<String?> = _selectedViVoice.asStateFlow()

    private val _speechRate = MutableStateFlow<Float>(prefs.getFloat("speech_rate", 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _availableKoVoices = MutableStateFlow<List<String>>(emptyList())
    val availableKoVoices: StateFlow<List<String>> = _availableKoVoices.asStateFlow()

    private val _availableViVoices = MutableStateFlow<List<String>>(emptyList())
    val availableViVoices: StateFlow<List<String>> = _availableViVoices.asStateFlow()

    private val _excludeCharacters = MutableStateFlow<String>(prefs.getString("exclude_characters", "") ?: "")
    val excludeCharacters: StateFlow<String> = _excludeCharacters.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupProgressListener()
            updateAvailableVoices()
        } else {
            Log.e("TtsManager", "Failed to initialize TextToSpeech.")
        }
    }

    private fun updateAvailableVoices() {
        try {
            val voicesList = tts?.voices?.toList() ?: emptyList()
            val ko = voicesList
                .filter { it.locale.language == "ko" }
                .map { it.name }
                .sorted()
            val vi = voicesList
                .filter { it.locale.language == "vi" }
                .map { it.name }
                .sorted()
            _availableKoVoices.value = ko
            _availableViVoices.value = vi
        } catch (e: Exception) {
            Log.e("TtsManager", "Error fetching voices", e)
        }
    }

    fun setKoVoice(name: String?) {
        _selectedKoVoice.value = name
        prefs.edit().putString("ko_voice", name).apply()
    }

    fun setViVoice(name: String?) {
        _selectedViVoice.value = name
        prefs.edit().putString("vi_voice", name).apply()
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        prefs.edit().putFloat("speech_rate", rate).apply()
        if (isInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    fun setExcludeCharacters(value: String) {
        _excludeCharacters.value = value
        prefs.edit().putString("exclude_characters", value).apply()
    }

    fun filterExcludedCharacters(text: String): String {
        val excludedList = _excludeCharacters.value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (excludedList.isEmpty()) return text
        
        var filtered = text
        for (ex in excludedList) {
            filtered = filtered.replace(ex, "")
        }
        return filtered
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (isReadingModeActive) {
                    playNextReadingSegment()
                } else {
                    playNextSegment()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("TtsManager", "TTS error on utterance: $utteranceId")
                stop()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e("TtsManager", "TTS error code $errorCode on utterance $utteranceId")
                stop()
            }
        })
    }

    private fun isKoreanChar(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
               block == Character.UnicodeBlock.HANGUL_JAMO ||
               block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
               c.code in 0xAC00..0xD7AF || c.code in 0x1100..0x11FF || c.code in 0x3130..0x318F
    }

    private fun splitTextForTts(text: String): List<TtsSegment> {
        val segments = mutableListOf<TtsSegment>()
        if (text.isEmpty()) return segments

        var currentSegment = StringBuilder()
        var currentIsKorean = isKoreanChar(text[0])

        for (c in text) {
            val isKorean = isKoreanChar(c)
            val isWordSeparator = c == ' ' || c == '\n' || c == '\t' || c == '.' || c == ',' || c == '?' || c == '!' || c == ':' || c == '*' || c == '•' || c == '-' || c == '(' || c == ')' || c == '[' || c == ']'
            
            if (isWordSeparator) {
                currentSegment.append(c)
            } else {
                if (isKorean != currentIsKorean) {
                    if (currentSegment.isNotEmpty()) {
                        segments.add(TtsSegment(currentSegment.toString(), currentIsKorean))
                    }
                    currentSegment = StringBuilder()
                    currentIsKorean = isKorean
                }
                currentSegment.append(c)
            }
        }
        if (currentSegment.isNotEmpty()) {
            segments.add(TtsSegment(currentSegment.toString(), currentIsKorean))
        }
        
        val merged = mutableListOf<TtsSegment>()
        for (seg in segments) {
            val trimmed = seg.text.replace(Regex("[\\s\\p{Punct}]+"), "")
            if (trimmed.isEmpty()) {
                if (merged.isNotEmpty()) {
                    val last = merged.last()
                    merged[merged.size - 1] = last.copy(text = last.text + seg.text)
                } else {
                    merged.add(seg)
                }
            } else {
                if (merged.isNotEmpty() && merged.last().isKorean == seg.isKorean) {
                    val last = merged.last()
                    merged[merged.size - 1] = last.copy(text = last.text + seg.text)
                } else {
                    merged.add(seg)
                }
            }
        }
        return merged.filter { it.text.trim().isNotEmpty() }
    }

    fun speak(messageId: String, text: String) {
        if (!isInitialized || tts == null) {
            Log.e("TtsManager", "TTS is not initialized yet.")
            return
        }

        stop() // Stop any ongoing playback

        _speakingMessageId.value = messageId
        val filteredText = filterExcludedCharacters(text)
        segmentsToPlay = splitTextForTts(filteredText)
        currentSegmentIndex = -1
        _isPlaying.value = true

        playNextSegment()
    }

    @Synchronized
    private fun playNextSegment() {
        if (tts == null || !isInitialized) return
        
        currentSegmentIndex++
        if (currentSegmentIndex >= segmentsToPlay.size) {
            _isPlaying.value = false
            _speakingMessageId.value = null
            return
        }

        val segment = segmentsToPlay[currentSegmentIndex]
        val cleanText = segment.text.trim()
        if (cleanText.isEmpty()) {
            playNextSegment()
            return
        }

        // Apply dynamic speaking rate
        tts?.setSpeechRate(_speechRate.value)

        val locale = if (segment.isKorean) Locale.KOREAN else Locale("vi", "VN")
        val voiceName = if (segment.isKorean) _selectedKoVoice.value else _selectedViVoice.value
        val voiceObj = if (voiceName != null) tts?.voices?.find { it.name == voiceName } else null
        if (voiceObj != null) {
            tts?.setVoice(voiceObj)
        } else {
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsManager", "Language not supported/missing: ${locale.displayLanguage}. Fallback...")
                tts?.setLanguage(Locale.US)
            }
        }

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "seg_$currentSegmentIndex")
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "seg_$currentSegmentIndex")
    }

    fun startReading(paragraphs: List<ReadingParagraph>, mode: ReadingPlayMode, startIndex: Int = 0) {
        if (!isInitialized || tts == null) {
            Log.e("TtsManager", "TTS is not initialized yet.")
            return
        }
        stop() // Stops everything, resets states
        isReadingModeActive = true
        readingParagraphs = paragraphs
        setReadingPlayMode(mode)
        currentParaIndex = startIndex - 1 // so increment results in startIndex
        currentParaPhase = 0
        _isPlaying.value = true
        
        playNextReadingSegment()
    }

    fun stopReading() {
        stop()
    }

    @Synchronized
    private fun playNextReadingSegment() {
        if (tts == null || !isInitialized || !isReadingModeActive) return

        // Choose what to play next based on currentParaIndex and currentParaPhase
        if (currentParaIndex == -1) {
            // Start of playback
            currentParaIndex = 0
            currentParaPhase = 0
        } else {
            // Advance phase or index
            when (_readingPlayMode.value) {
                ReadingPlayMode.KO_ONLY, ReadingPlayMode.VI_ONLY -> {
                    // Single phase modes: move to next paragraph directly
                    currentParaIndex++
                    currentParaPhase = 0
                }
                ReadingPlayMode.KO_THEN_VI, ReadingPlayMode.VI_THEN_KO -> {
                    if (currentParaPhase == 0) {
                        // Move to second phase of the same paragraph
                        currentParaPhase = 1
                    } else {
                        // Both phases complete, move to next paragraph
                        currentParaIndex++
                        currentParaPhase = 0
                    }
                }
            }
        }

        // Check if we finished all paragraphs
        if (currentParaIndex >= readingParagraphs.size || currentParaIndex < 0) {
            // Stop playback
            isReadingModeActive = false
            _isPlaying.value = false
            _currentPlayingParagraphIndex.value = null
            return
        }

        _currentPlayingParagraphIndex.value = currentParaIndex
        val para = readingParagraphs[currentParaIndex]

        // Determine content and language to play
        val (textToSpeak, isKorean) = when (_readingPlayMode.value) {
            ReadingPlayMode.KO_ONLY -> Pair(para.korean, true)
            ReadingPlayMode.VI_ONLY -> Pair(para.vietnamese, false)
            ReadingPlayMode.KO_THEN_VI -> {
                if (currentParaPhase == 0) Pair(para.korean, true) else Pair(para.vietnamese, false)
            }
            ReadingPlayMode.VI_THEN_KO -> {
                if (currentParaPhase == 0) Pair(para.vietnamese, false) else Pair(para.korean, true)
            }
        }

        val filteredText = filterExcludedCharacters(textToSpeak)
        val cleanText = filteredText.replace(Regex("[*•\\-#()\\[\\]]+"), "").trim()
        if (cleanText.isEmpty()) {
            // If empty text (e.g. translation failed or source empty), skip to next immediately
            playNextReadingSegment()
            return
        }

        // Apply dynamic speaking rate
        tts?.setSpeechRate(_speechRate.value)

        val locale = if (isKorean) Locale.KOREAN else Locale("vi", "VN")
        val voiceName = if (isKorean) _selectedKoVoice.value else _selectedViVoice.value
        val voiceObj = if (voiceName != null) tts?.voices?.find { it.name == voiceName } else null
        if (voiceObj != null) {
            tts?.setVoice(voiceObj)
        } else {
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsManager", "Language not supported/missing: ${locale.displayLanguage}. Fallback...")
                tts?.setLanguage(Locale.US)
            }
        }

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "read_${currentParaIndex}_${currentParaPhase}")
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "read_${currentParaIndex}_${currentParaPhase}")
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
        _isPlaying.value = false
        _speakingMessageId.value = null
        currentSegmentIndex = -1
        segmentsToPlay = emptyList()
        
        isReadingModeActive = false
        _currentPlayingParagraphIndex.value = null
        currentParaIndex = -1
        currentParaPhase = 0
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
