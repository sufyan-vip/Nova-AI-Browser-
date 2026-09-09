package com.nova.browser.core.di

import android.content.Context
import com.nova.browser.core.database.NovaDatabase
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NovaDatabase =
        NovaDatabase.build(context)

    @Provides fun provideBookmarkDao(db: NovaDatabase): BookmarkDao = db.bookmarkDao()
    @Provides fun provideHistoryDao(db: NovaDatabase): HistoryDao = db.historyDao()
    @Provides fun provideTabDao(db: NovaDatabase): TabDao = db.tabDao()
    @Provides fun provideDownloadDao(db: NovaDatabase): DownloadDao = db.downloadDao()
    @Provides fun providePasswordDao(db: NovaDatabase): PasswordDao = db.passwordDao()
    @Provides fun provideAIMemoryDao(db: NovaDatabase): AIMemoryDao = db.aiMemoryDao()
    @Provides fun provideWorkspaceDao(db: NovaDatabase): WorkspaceDao = db.workspaceDao()
    @Provides fun provideAutomationDao(db: NovaDatabase): AutomationDao = db.automationDao()
    @Provides fun provideNoteDao(db: NovaDatabase): NoteDao = db.noteDao()
    @Provides fun provideSiteSettingsDao(db: NovaDatabase): SiteSettingsDao = db.siteSettingsDao()
    @Provides fun provideTrackerStatDao(db: NovaDatabase): TrackerStatDao = db.trackerStatDao()
    @Provides fun provideAIMessageDao(db: NovaDatabase): AIMessageDao = db.aiMessageDao()
}
