package com.dreamboard.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dreamboard.app.data.demo.DemoFinanceRepository
import com.dreamboard.app.domain.repository.FinanceRepository
import dagger.Binds
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
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("dreamboard_prefs") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    /** Phase 2 swaps this binding for the Room-backed repository. */
    @Binds
    @Singleton
    abstract fun bindFinanceRepository(impl: DemoFinanceRepository): FinanceRepository
}
