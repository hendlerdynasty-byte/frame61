package de.hendler.frame61.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hendler.frame61.data.database.DatabaseProvider
import de.hendler.frame61.data.database.Frame61Database
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): Frame61Database {
        return DatabaseProvider(context).database
    }

    @Provides
    fun provideTitleDao(db: Frame61Database) = db.titleDao()

    @Provides
    fun provideMirrorDao(db: Frame61Database) = db.mirrorDao()

    @Provides
    fun provideEpisodeDao(db: Frame61Database) = db.episodeDao()

    @Provides
    fun providePlaybackStateDao(db: Frame61Database) = db.playbackStateDao()

    @Provides
    fun provideProfileDao(db: Frame61Database) = db.profileDao()

    @Provides
    fun provideRecVectorDao(db: Frame61Database) = db.recVectorDao()

    @Provides
    fun provideStreamEvidenceDao(db: Frame61Database) = db.streamEvidenceDao()
}