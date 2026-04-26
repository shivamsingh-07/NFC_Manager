package com.nfcmanager.app.di

import android.content.Context
import androidx.room.Room
import com.nfcmanager.app.data.local.NfcActionDao
import com.nfcmanager.app.data.local.SavedMessageDao
import com.nfcmanager.app.data.local.NfcDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): NfcDatabase =
        Room.databaseBuilder(context, NfcDatabase::class.java, NfcDatabase.NAME)
            .addMigrations(NfcDatabase.MIGRATION_1_2, NfcDatabase.MIGRATION_2_3, NfcDatabase.MIGRATION_3_4)
            .build()

    @Provides
    fun provideNfcActionDao(db: NfcDatabase): NfcActionDao = db.nfcActionDao()

    @Provides
    fun provideSavedMessageDao(db: NfcDatabase): SavedMessageDao = db.savedMessageDao()
}
