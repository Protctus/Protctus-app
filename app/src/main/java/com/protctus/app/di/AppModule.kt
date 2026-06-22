package com.protctus.app.di

import android.content.Context
import com.protctus.app.data.local.LearningSessionManager
import com.protctus.app.data.local.RecentSearchDao
import com.protctus.app.data.local.SettingsManager
import com.protctus.app.data.repository.SearchRepository
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context): SettingsManager {
        return SettingsManager(context)
    }

    @Provides
    @Singleton
    fun provideLearningSessionManager(@ApplicationContext context: Context): LearningSessionManager {
        return LearningSessionManager(context)
    }

    @Provides
    @Singleton
    fun provideSearchRepository(recentSearchDao: RecentSearchDao): SearchRepository {
        return SearchRepository(recentSearchDao)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("protctus_prefs", Context.MODE_PRIVATE)
    }
}
