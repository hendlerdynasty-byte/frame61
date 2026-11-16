package de.hendler.frame61.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Profile — PIN optional, namespaces via profileId
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: Long = 0,
    val name: String,
    val pinHash: String,        // SHA-256 of PIN; empty if no PIN
    val allowedSources: String,   // comma-separated source IDs
    val skinId: String,
    val layoutId: String
)
