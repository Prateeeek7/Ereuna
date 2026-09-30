package com.researchradar.core.data.di

import com.researchradar.core.data.repository.DefaultMapRepository
import com.researchradar.core.data.repository.MapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMapRepository(impl: DefaultMapRepository): MapRepository

    @Binds
    @Singleton
    abstract fun bindAuthTokenStore(
        impl: com.researchradar.core.data.session.SessionRepository,
    ): com.researchradar.core.network.AuthTokenStore

    @Binds
    @Singleton
    abstract fun bindUserPreferences(
        impl: com.researchradar.core.data.preferences.DataStoreUserPreferencesRepository,
    ): com.researchradar.core.data.preferences.UserPreferencesRepository
}
