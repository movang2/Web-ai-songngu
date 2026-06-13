package com.example.data

import kotlinx.coroutines.flow.Flow

class ArticleRepository(private val articleDao: ArticleDao) {
    val bookmarks: Flow<List<ArticleEntity>> = articleDao.getBookmarks()
    val history: Flow<List<ArticleEntity>> = articleDao.getHistory()

    suspend fun insertArticle(article: ArticleEntity): Long {
        return articleDao.insert(article)
    }

    suspend fun updateArticle(article: ArticleEntity) {
        articleDao.update(article)
    }

    suspend fun deleteArticle(article: ArticleEntity) {
        articleDao.delete(article)
    }

    suspend fun deleteById(id: Int) {
        articleDao.deleteById(id)
    }

    suspend fun getByContent(title: String, content: String): ArticleEntity? {
        return articleDao.getByContent(title, content)
    }

    suspend fun getByUrl(url: String): ArticleEntity? {
        return articleDao.getByUrl(url)
    }

    suspend fun getById(id: Int): ArticleEntity? {
        return articleDao.getById(id)
    }

    suspend fun clearAllHistory() {
        articleDao.clearHistoryOnly()
        articleDao.clearHistoryStatusFromBookmarks()
    }

    suspend fun clearAllBookmarks() {
        articleDao.clearBookmarksOnly()
        articleDao.clearBookmarkStatusFromHistory()
    }

    // Web Bookmarks
    val webBookmarks: Flow<List<WebBookmarkEntity>> = articleDao.getAllWebBookmarks()

    suspend fun insertWebBookmark(bookmark: WebBookmarkEntity): Long {
        return articleDao.insertWebBookmark(bookmark)
    }

    suspend fun deleteWebBookmarkById(id: Int) {
        articleDao.deleteWebBookmarkById(id)
    }

    suspend fun getWebBookmarkByUrl(url: String): WebBookmarkEntity? {
        return articleDao.getWebBookmarkByUrl(url)
    }
}
