package de.hendler.frame61.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.hendler.frame61.data.database.entities.Episode
import de.hendler.frame61.data.database.entities.Mirror
import de.hendler.frame61.data.database.entities.PlaybackState
import de.hendler.frame61.data.database.entities.Profile
import de.hendler.frame61.data.database.entities.RecVector
import de.hendler.frame61.data.database.entities.StreamEvidence
import de.hendler.frame61.data.database.entities.Title

@Database(
    entities = [
        Title::class,
        Mirror::class,
        Episode::class,
        PlaybackState::class,
        Profile::class,
        RecVector::class,
        StreamEvidence::class
    ],
    version = 3,
    exportSchema = true
)
abstract class Frame61Database : RoomDatabase() {

    abstract fun titleDao(): TitleDao
    abstract fun mirrorDao(): MirrorDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun profileDao(): ProfileDao
    abstract fun recVectorDao(): RecVectorDao
    abstract fun streamEvidenceDao(): StreamEvidenceDao

    companion object {
        const val DATABASE_NAME = "frame61.db"

        /**
         * 1 → 2: resume is per episode. The old unique index was
         * (profileId,titleId), so watching S01E03 deleted S01E02's position.
         * The new index includes episodeId.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_playback_states_profileId_titleId")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_playback_states_profileId_titleId_episodeId " +
                        "ON playback_states (profileId, titleId, episodeId)"
                )
            }
        }

        /** Additive: creates stream evidence and leaves every version-2 table untouched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                StreamEvidenceMigration.STATEMENTS.forEach(db::execSQL)
            }
        }
    }
}
