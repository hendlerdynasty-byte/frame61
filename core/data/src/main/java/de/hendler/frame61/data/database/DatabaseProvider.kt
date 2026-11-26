package de.hendler.frame61.data.database

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val database: Frame61Database by lazy {
        Room.databaseBuilder(
            context,
            Frame61Database::class.java,
            Frame61Database.DATABASE_NAME
        )
            .addMigrations(
                Frame61Database.MIGRATION_1_2,
                Frame61Database.MIGRATION_2_3
            )
            .build()
    }
}