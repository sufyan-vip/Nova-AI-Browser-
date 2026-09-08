package com.nova.browser.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "bookmarks",
    indices = [Index("url"), Index("folderId"), Index("createdAt")]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val folderId: Long? = null,
    val folderName: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isFolder: Boolean = false
)

@Entity(
    tableName = "history",
    indices = [Index("url"), Index("visitedAt")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val visitedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

@Entity(
    tableName = "tabs",
    indices = [Index("groupId"), Index("position"), Index("lastAccessed")]
)
data class TabEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val favicon: String? = null,
    val isActive: Boolean = false,
    val isPinned: Boolean = false,
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val position: Int = 0,
    val parentTabId: String? = null,
    val lastAccessed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val screenshot: String? = null,
    val isSleeping: Boolean = false
)

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt"), Index("url")]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: String = "pending",
    val threadCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val category: String = "Other",
    val hash: String? = null,
    val errorMessage: String? = null
)

@Entity(
    tableName = "passwords",
    indices = [Index("domain"), Index(value = ["domain", "username"], unique = true)]
)
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val username: String,
    val encryptedPassword: String,
    val iv: String,
    val favicon: String? = null,
    val notes: String? = null,
    val category: String? = null,
    val isCompromised: Boolean = false,
    val isWeak: Boolean = false,
    val isReused: Boolean = false,
    val strengthScore: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "ai_memory",
    indices = [Index("type"), Index("isEnabled")]
)
data class AIMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String,
    val isEnabled: Boolean = true,
    val category: String? = null,
    val relatedSite: String? = null,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val tabIds: String = "[]",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "automations",
    indices = [Index("triggerType"), Index("isEnabled")]
)
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val workflowJson: String,
    val triggerType: String,
    val triggerValue: String? = null,
    val isEnabled: Boolean = true,
    val lastRunAt: Long? = null,
    val lastResult: String? = null,
    val runCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notes",
    indices = [Index("type"), Index("updatedAt")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
    val type: String = "note",
    val tags: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Per-site privacy/permission overrides (spec 17_PRIVACY_ENGINE). */
@Entity(tableName = "site_settings", indices = [Index(value = ["domain"], unique = true)])
data class SiteSettingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val blockTrackers: Boolean = true,
    val blockAds: Boolean = true,
    val allowCookies: Boolean = true,
    val allowJavaScript: Boolean = true,
    val locationPermission: String = "ask",
    val cameraPermission: String = "ask",
    val microphonePermission: String = "ask",
    val notificationPermission: String = "ask",
    val desktopMode: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Aggregated blocked-tracker counters used by the privacy dashboard. */
@Entity(tableName = "tracker_stats", indices = [Index(value = ["trackerDomain"], unique = true)])
data class TrackerStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerDomain: String,
    val category: String,
    val blockCount: Int = 0,
    val lastBlockedAt: Long = System.currentTimeMillis()
)

/** Persisted AI conversation turns (spec 06 — conversation history). */
@Entity(tableName = "ai_messages", indices = [Index("conversationId"), Index("createdAt")])
data class AIMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val content: String,
    val mode: String = "chat",
    val model: String? = null,
    val pageUrl: String? = null,
    val tokenCount: Int = 0,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
