package de.hendler.frame61.data.database

/**
 * Additive 2 → 3 migration. Kept as individual statements so Android SQLite
 * executes each one separately and the same SQL can be smoke-tested off-device.
 */
internal object StreamEvidenceMigration {

    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS `stream_evidence` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `catalogId` TEXT NOT NULL,
            `season` INTEGER NOT NULL,
            `episode` INTEGER NOT NULL,
            `sourceId` TEXT NOT NULL,
            `sourceKey` TEXT NOT NULL,
            `checkedAt` INTEGER NOT NULL,
            `outcome` TEXT NOT NULL,
            `identityScore` REAL NOT NULL,
            `httpStatus` INTEGER,
            `container` TEXT,
            `redactedUrlHash` TEXT,
            `reason` TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE UNIQUE INDEX IF NOT EXISTS `index_stream_evidence_catalogId_season_episode_sourceId_sourceKey`
        ON `stream_evidence` (`catalogId`, `season`, `episode`, `sourceId`, `sourceKey`)
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS `index_stream_evidence_catalogId_season_episode_outcome`
        ON `stream_evidence` (`catalogId`, `season`, `episode`, `outcome`)
        """.trimIndent(),
        """
        CREATE INDEX IF NOT EXISTS `index_stream_evidence_outcome`
        ON `stream_evidence` (`outcome`)
        """.trimIndent()
    )
}
