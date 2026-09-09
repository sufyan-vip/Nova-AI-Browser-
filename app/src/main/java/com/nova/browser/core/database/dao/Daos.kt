package com.nova.browser.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.nova.browser.core.database.entities.AIMemoryEntity
import com.nova.browser.core.database.entities.AIMessageEntity
import com.nova.browser.core.database.entities.AutomationEntity
import com.nova.browser.core.database.entities.BookmarkEntity
import com.nova.browser.core.database.entities.DownloadEntity
import com.nova.browser.core.database.entities.HistoryEntity
import com.nova.browser.core.database.entities.NoteEntity
import com.nova.browser.core.database.entities.PasswordEntity
import com.nova.browser.core.database.entities.SiteSettingsEntity
import com.nova.browser.core.database.entities.TabEntity
import com.nova.browser.core.database.entities.TrackerStatEntity
import com.nova.browser.core.database.entities.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY isFolder DESC, position ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE folderId IS :folderId ORDER BY position ASC, createdAt DESC")
    fun observeInFolder(folderId: Long?): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isFolder = 1 ORDER BY title ASC")
    fun observeFolders(): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 50): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE url = :url AND isFolder = 0 LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url AND isFolder = 0)")
    fun observeIsBookmarked(url: String): Flow<Boolean>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE isFolder = 0")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url AND isFolder = 0")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE folderId = :folderId")
    suspend fun deleteFolderContents(folderId: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    @Query("UPDATE bookmarks SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<HistoryEntity>>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY visitCount DESC, visitedAt DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 50): List<HistoryEntity>

    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' " +
            "ORDER BY visitCount DESC, visitedAt DESC LIMIT :limit"
    )
    fun observeSearch(query: String, limit: Int = 100): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY visitCount DESC, visitedAt DESC LIMIT :limit")
    fun observeMostVisited(limit: Int = 8): Flow<List<HistoryEntity>>

    @Query("SELECT COUNT(*) FROM history")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Update
    suspend fun update(entry: HistoryEntity)

    @Transaction
    suspend fun record(title: String, url: String, favicon: String?) {
        val existing = findByUrl(url)
        if (existing == null) {
            insert(HistoryEntity(title = title, url = url, favicon = favicon))
        } else {
            update(
                existing.copy(
                    title = title.ifBlank { existing.title },
                    favicon = favicon ?: existing.favicon,
                    visitedAt = System.currentTimeMillis(),
                    visitCount = existing.visitCount + 1
                )
            )
        }
    }

    @Delete
    suspend fun delete(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC, createdAt ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE isPrivate = :isPrivate ORDER BY position ASC")
    fun observeByPrivacy(isPrivate: Boolean): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC, createdAt ASC")
    suspend fun getAll(): List<TabEntity>

    @Query("SELECT * FROM tabs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TabEntity?

    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): TabEntity?

    @Query("SELECT COUNT(*) FROM tabs")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(tab: TabEntity)

    @Upsert
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("UPDATE tabs SET isActive = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun setActive(id: String) {
        clearActive()
        markActive(id, System.currentTimeMillis())
    }

    @Query("UPDATE tabs SET isActive = 1, lastAccessed = :now, isSleeping = 0 WHERE id = :id")
    suspend fun markActive(id: String, now: Long)

    @Query("UPDATE tabs SET isSleeping = :sleeping WHERE id = :id")
    suspend fun setSleeping(id: String, sleeping: Boolean)

    @Query("UPDATE tabs SET groupId = :groupId, groupName = :groupName WHERE id = :id")
    suspend fun setGroup(id: String, groupId: String?, groupName: String?)

    @Query("UPDATE tabs SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tabs WHERE isPrivate = 1")
    suspend fun deletePrivate()

    @Query("DELETE FROM tabs WHERE isPinned = 0")
    suspend fun deleteUnpinned()

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('downloading', 'pending', 'paused') ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE category = :category ORDER BY createdAt DESC")
    fun observeByCategory(category: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE fileName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun observeSearch(query: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE url = :url ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByUrl(url: String): DownloadEntity?

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'downloading'")
    fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedSize = :downloaded, totalSize = :total WHERE id = :id")
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE downloads SET status = 'completed', completedAt = :now, hash = :hash WHERE id = :id")
    suspend fun markCompleted(id: Long, now: Long, hash: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'completed'")
    suspend fun clearCompleted()

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY domain ASC")
    fun observeAll(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE domain LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' ORDER BY domain ASC")
    fun observeSearch(query: String): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords WHERE domain = :domain ORDER BY lastUsedAt DESC")
    suspend fun findForDomain(domain: String): List<PasswordEntity>

    @Query("SELECT * FROM passwords WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PasswordEntity?

    @Query("SELECT * FROM passwords")
    suspend fun getAll(): List<PasswordEntity>

    @Query("SELECT COUNT(*) FROM passwords")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM passwords WHERE isWeak = 1")
    fun observeWeakCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM passwords WHERE isReused = 1")
    fun observeReusedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM passwords WHERE isCompromised = 1")
    fun observeCompromisedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(password: PasswordEntity): Long

    @Update
    suspend fun update(password: PasswordEntity)

    @Query("UPDATE passwords SET lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query("UPDATE passwords SET isCompromised = :compromised, isWeak = :weak, isReused = :reused, strengthScore = :score WHERE id = :id")
    suspend fun updateHealth(id: Long, compromised: Boolean, weak: Boolean, reused: Boolean, score: Int)

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM passwords")
    suspend fun deleteAll()
}

@Dao
interface AIMemoryDao {
    @Query("SELECT * FROM ai_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AIMemoryEntity>>

    @Query("SELECT * FROM ai_memory WHERE isEnabled = 1 ORDER BY usageCount DESC, updatedAt DESC")
    suspend fun getEnabled(): List<AIMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE isEnabled = 1 AND (relatedSite IS NULL OR relatedSite = :site) ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getForSite(site: String, limit: Int = 20): List<AIMemoryEntity>

    @Query("SELECT * FROM ai_memory WHERE content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun observeSearch(query: String): Flow<List<AIMemoryEntity>>

    @Query("SELECT COUNT(*) FROM ai_memory WHERE isEnabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: AIMemoryEntity): Long

    @Update
    suspend fun update(memory: AIMemoryEntity)

    @Query("UPDATE ai_memory SET isEnabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE ai_memory SET usageCount = usageCount + 1 WHERE id IN (:ids)")
    suspend fun incrementUsage(ids: List<Long>)

    @Query("DELETE FROM ai_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_memory")
    suspend fun deleteAll()
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity): Long

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET isActive = 0")
    suspend fun clearActive()

    @Transaction
    suspend fun activate(id: Long) {
        clearActive()
        markActive(id)
    }

    @Query("UPDATE workspaces SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: Long)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM workspaces")
    suspend fun deleteAll()
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE isEnabled = 1 AND triggerType = :type")
    suspend fun getByTrigger(type: String): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AutomationEntity?

    @Query("SELECT COUNT(*) FROM automations WHERE isEnabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(automation: AutomationEntity): Long

    @Update
    suspend fun update(automation: AutomationEntity)

    @Query("UPDATE automations SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE automations SET lastRunAt = :now, runCount = runCount + 1, lastResult = :result WHERE id = :id")
    suspend fun recordRun(id: Long, now: Long, result: String?)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM automations")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE type = :type ORDER BY updatedAt DESC")
    fun observeByType(type: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun observeSearch(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface SiteSettingsDao {
    @Query("SELECT * FROM site_settings ORDER BY domain ASC")
    fun observeAll(): Flow<List<SiteSettingsEntity>>

    @Query("SELECT * FROM site_settings WHERE domain = :domain LIMIT 1")
    suspend fun getForDomain(domain: String): SiteSettingsEntity?

    @Query("SELECT * FROM site_settings WHERE domain = :domain LIMIT 1")
    fun observeForDomain(domain: String): Flow<SiteSettingsEntity?>

    @Upsert
    suspend fun upsert(settings: SiteSettingsEntity)

    @Query("DELETE FROM site_settings WHERE domain = :domain")
    suspend fun deleteForDomain(domain: String)

    @Query("DELETE FROM site_settings")
    suspend fun deleteAll()
}

@Dao
interface TrackerStatDao {
    @Query("SELECT * FROM tracker_stats ORDER BY blockCount DESC LIMIT :limit")
    fun observeTop(limit: Int = 50): Flow<List<TrackerStatEntity>>

    @Query("SELECT COALESCE(SUM(blockCount), 0) FROM tracker_stats")
    fun observeTotalBlocked(): Flow<Int>

    @Query("SELECT * FROM tracker_stats WHERE trackerDomain = :domain LIMIT 1")
    suspend fun get(domain: String): TrackerStatEntity?

    @Upsert
    suspend fun upsert(stat: TrackerStatEntity)

    @Transaction
    suspend fun increment(domain: String, category: String) {
        val existing = get(domain)
        if (existing == null) {
            upsert(TrackerStatEntity(trackerDomain = domain, category = category, blockCount = 1))
        } else {
            upsert(
                existing.copy(
                    blockCount = existing.blockCount + 1,
                    lastBlockedAt = System.currentTimeMillis()
                )
            )
        }
    }

    @Query("DELETE FROM tracker_stats")
    suspend fun deleteAll()
}

@Dao
interface AIMessageDao {
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeConversation(conversationId: String): Flow<List<AIMessageEntity>>

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getConversation(conversationId: String, limit: Int = 100): List<AIMessageEntity>

    @Query("SELECT DISTINCT conversationId FROM ai_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentConversations(limit: Int = 20): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AIMessageEntity): Long

    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM ai_messages")
    suspend fun deleteAll()
}
