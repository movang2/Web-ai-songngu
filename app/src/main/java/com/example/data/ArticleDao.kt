package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarks(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isHistory = 1 ORDER BY timestamp DESC")
    fun getHistory(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: ArticleEntity): Long

    @Update
    suspend fun update(article: ArticleEntity)

    @Query("SELECT * FROM articles WHERE title = :title AND content = :content LIMIT 1")
    suspend fun getByContent(title: String, content: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): ArticleEntity?

    @Delete
    suspend fun delete(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM articles WHERE isHistory = 1 AND isBookmarked = 0")
    suspend fun clearHistoryOnly()

    @Query("UPDATE articles SET isHistory = 0 WHERE isHistory = 1 AND isBookmarked = 1")
    suspend fun clearHistoryStatusFromBookmarks()

    @Query("DELETE FROM articles WHERE isBookmarked = 1 AND isHistory = 0")
    suspend fun clearBookmarksOnly()

    @Query("UPDATE articles SET isBookmarked = 0, content = '' WHERE isBookmarked = 1 AND isHistory = 1")
    suspend fun clearBookmarkStatusFromHistory()

    // Web Bookmarks Queries
    @Query("SELECT * FROM web_bookmarks ORDER BY timestamp DESC")
    fun getAllWebBookmarks(): Flow<List<WebBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebBookmark(bookmark: WebBookmarkEntity): Long

    @Query("DELETE FROM web_bookmarks WHERE id = :id")
    suspend fun deleteWebBookmarkById(id: Int)

    @Query("SELECT * FROM web_bookmarks WHERE url = :url LIMIT 1")
    suspend fun getWebBookmarkByUrl(url: String): WebBookmarkEntity?
}
