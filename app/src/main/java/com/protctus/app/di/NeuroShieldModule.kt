package com.protctus.app.di

import android.content.Context
import com.protctus.app.neuroshield.engine.LexicalFeatureExtractor
import com.protctus.app.neuroshield.engine.NeuroShieldEngine
import com.protctus.app.neuroshield.engine.TrustedDomainCache
import com.protctus.app.neuroshield.engine.VerdictEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NeuroShieldModule {

    @Provides
    @Singleton
    fun provideNeuroShieldEngine(@ApplicationContext context: Context): NeuroShieldEngine {
        val engine = NeuroShieldEngine(context)
        // Initialize immediately on background thread (Hilt usually instantiates this early)
        // For strict performance, this could be lazy, but we want it ready before VPN connects.
        // Actually, we'll let the consumers call initialize() or let it initialize synchronously.
        // Since it's a Singleton, it will be kept alive.
        return engine
    }

    @Provides
    @Singleton
    fun provideLexicalFeatureExtractor(): LexicalFeatureExtractor {
        return LexicalFeatureExtractor()
    }

    @Provides
    @Singleton
    fun provideVerdictEngine(): VerdictEngine {
        return VerdictEngine()
    }

    @Provides
    @Singleton
    fun provideTrustedDomainCache(): TrustedDomainCache {
        return TrustedDomainCache()
    }
}
