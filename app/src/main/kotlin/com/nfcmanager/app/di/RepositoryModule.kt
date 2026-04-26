package com.nfcmanager.app.di

import com.nfcmanager.app.data.repository.NfcActionRepositoryImpl
import com.nfcmanager.app.domain.repository.NfcActionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindNfcActionRepository(
        impl: NfcActionRepositoryImpl,
    ): NfcActionRepository
}
