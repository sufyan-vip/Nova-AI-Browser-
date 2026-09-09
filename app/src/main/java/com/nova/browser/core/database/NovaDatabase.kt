package com.nova.browser.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nova.browser.core.database.dao.AIMemoryDao
import com.nova.browser.core.database.dao.AIMessageDao
import com.nova.browser.core.database.dao.AutomationDao
import com.nova.browser.core.database.dao.BookmarkDao
import com.nova.browser.core.database.dao.DownloadDao
import com.nova.browser.core.database.dao.HistoryDao
import com.nova.browser.core.database.dao.NoteDao
import com.nova.browser.core.database.dao.PasswordDao
import com.nova.browser.core.database.dao.SiteSettingsDao
import com.nova.browser.core.database.dao.TabDao
import com.nova.browser.core.database.dao.TrackerStatDao
import com.nova.browser.core.database.dao.WorkspaceDao
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
import com.nova.browser.core.utils.Constants

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        TabEntity::class,
        DownloadEntity::class,
        PasswordEntity::class,
        AIMemoryEntity::class,
        WorkspaceEntity::class,
        AutomationEntity::class,
        NoteEntity::class,
        SiteSettingsEntity::class,
        TrackerStatEntity::class,
        AIMessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun tabDao(): TabDao
    abstract fun downloadDao(): DownloadDao
    abstract fun passwordDao(): PasswordDao
    abstract fun aiMemoryDao(): AIMemoryDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun automationDao(): AutomationDao
    abstract fun noteDao(): NoteDao
    abstract fun siteSettingsDao(): SiteSettingsDao
    abstract fun trackerStatDao(): TrackerStatDao
    abstract fun aiMessageDao(): AIMessageDao

    companion object {
        /**
         * Builds the database. Corruption or a failed migration falls back to a
         * fresh database instead of crashing on launch (spec 24_ERROR_HANDLING).
         */
        fun build(context: Context): NovaDatabase =
            Room.databaseBuilder(context, NovaDatabase::class.java, Constants.DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Extra covering index for the omnibox suggestion query.
                        try {
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_title ON history(title)")
                        } catch (e: Exception) {
                            // Index is an optimisation only.
                        }
                    }
                })
                .build()
    }
}
