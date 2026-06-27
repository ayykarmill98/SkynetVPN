package com.skyvpn.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.skyvpn.app.data.local.SkyVPNDatabase
import com.skyvpn.app.data.local.VPNConfigDao
import com.skyvpn.app.data.local.VPNLogDao
import com.skyvpn.app.data.repository.VPNConfigRepositoryImpl
import com.skyvpn.app.data.repository.VPNLogRepositoryImpl
import com.skyvpn.app.data.repository.SettingsRepositoryImpl
import com.skyvpn.app.domain.repository.VPNConfigRepository
import com.skyvpn.app.domain.repository.VPNLogRepository
import com.skyvpn.app.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skyvpn_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SkyVPNDatabase {
        return Room.databaseBuilder(
            context,
            SkyVPNDatabase::class.java,
            "skyvpn_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideConfigDao(db: SkyVPNDatabase): VPNConfigDao = db.vpnConfigDao()

    @Provides
    fun provideLogDao(db: SkyVPNDatabase): VPNLogDao = db.vpnLogDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: VPNConfigRepositoryImpl): VPNConfigRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: VPNLogRepositoryImpl): VPNLogRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
