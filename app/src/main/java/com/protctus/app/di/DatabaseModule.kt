package com.protctus.app.di

import android.content.Context
import com.protctus.app.data.local.AppDatabase
import com.protctus.app.data.local.RecentSearchDao
import com.protctus.app.data.local.SettingsDao
import com.protctus.app.data.local.ThreatDao
import com.protctus.app.data.local.dao.NotificationDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideThreatDao(db: AppDatabase): ThreatDao = db.threatDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideRecentSearchDao(db: AppDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()

    @Provides
    fun provideQuestDao(db: AppDatabase): com.protctus.app.data.local.dao.QuestDao = db.questDao()

    @Provides
    fun provideMicrolearningCardDao(db: AppDatabase): com.protctus.app.data.local.dao.MicrolearningCardDao = db.microlearningCardDao()
}
