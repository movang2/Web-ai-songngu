package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_bookmarks")
data class WebBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val chatHistoryJson: String? = null
)
