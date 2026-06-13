package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val isBookmarked: Boolean = false,
    val isHistory: Boolean = false,
    val url: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val importantVocab: String? = null,
    val chatHistoryJson: String? = null,
    val bilingualJson: String? = null
)
