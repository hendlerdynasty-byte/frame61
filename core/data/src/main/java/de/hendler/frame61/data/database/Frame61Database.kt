package de.hendler.frame61.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import de.hendler.frame61.data.database.entities.Episode
import de.hendler.frame61.data.database.entities.Mirror
import de.hendler.frame61.data.database.entities.PlaybackState
import de.hendler.frame61.data.database.entities.Profile
import de.hendler.frame61.data.database.entities.RecVector
import de.hendler.frame61.data.database.entities.Title

@Database(
    entities = [
        Title::class,
        Mirror::class,
        Episode::class,
        PlaybackState::class,
        Profile::class,
        RecVector::class
    ],
    version = 1,
    exportSchema = true
)
abstract class Frame61Database : RoomDatabase() {

    abstract fun titleDao(): TitleDao
    abstract fun mirrorDao(): MirrorDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun profileDao(): ProfileDao
    abstract fun recVectorDao(): RecVectorDao

    companion object {
        const val DATABASE_NAME = "frame61.db"
    }
}