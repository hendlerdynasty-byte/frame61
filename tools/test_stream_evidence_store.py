#!/usr/bin/env python3
"""Offline SQLite smoke test for the Room stream-evidence migration and queries.

Run with: python3 -I tools/test_stream_evidence_store.py
No Gradle, Android SDK or device is involved. SQL is read from the production
migration file so this test cannot silently use a divergent copy.
"""

from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = (
    ROOT
    / "core/data/src/main/java/de/hendler/frame61/data/database/StreamEvidenceMigration.kt"
)


def migration_statements():
    source = MIGRATION.read_text(encoding="utf-8")
    statements = [re.sub(r"\s+", " ", sql).strip() for sql in re.findall(r'"""(.*?)"""', source, re.S)]
    assert len(statements) == 4, statements
    assert all(sql.upper().startswith("CREATE ") for sql in statements)
    assert not re.search(r"\b(DROP|DELETE|ALTER|REPLACE)\b", " ".join(statements), re.I)
    return statements


def add(db, row_id, episode, outcome, checked_at, score=0.9):
    db.execute(
        """
        INSERT INTO stream_evidence (
            id, catalogId, season, episode, sourceId, sourceKey, checkedAt,
            outcome, identityScore, httpStatus, container, redactedUrlHash, reason
        ) VALUES (?, 'aw:title', 1, ?, 'aniworld', 'embed-1', ?, ?, ?, 200, 'hls', ?, 'test')
        ON CONFLICT(id) DO UPDATE SET
            checkedAt = excluded.checkedAt,
            outcome = excluded.outcome,
            identityScore = excluded.identityScore,
            httpStatus = excluded.httpStatus,
            container = excluded.container,
            redactedUrlHash = excluded.redactedUrlHash,
            reason = excluded.reason
        """,
        (row_id, episode, checked_at, outcome, score, "a" * 64),
    )


def main():
    statements = migration_statements()
    db = sqlite3.connect(":memory:")
    db.execute("CREATE TABLE legacy_titles (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
    db.execute("INSERT INTO legacy_titles VALUES (7, 'preserve me')")
    db.execute("PRAGMA user_version = 2")
    for statement in statements:
        db.execute(statement)
    db.execute("PRAGMA user_version = 3")

    assert db.execute("SELECT id, name FROM legacy_titles").fetchall() == [(7, "preserve me")]
    assert db.execute("PRAGMA user_version").fetchone()[0] == 3
    columns = {row[1] for row in db.execute("PRAGMA table_info(stream_evidence)")}
    assert columns == {
        "id", "catalogId", "season", "episode", "sourceId", "sourceKey", "checkedAt",
        "outcome", "identityScore", "httpStatus", "container", "redactedUrlHash", "reason",
    }
    assert not columns & {"url", "rawUrl", "token", "cookie", "cookies", "headers"}
    table_sql = db.execute(
        "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'stream_evidence'"
    ).fetchone()[0]
    assert "AUTOINCREMENT" in table_sql.upper()

    indexes = {row[1]: row[2] for row in db.execute("PRAGMA index_list(stream_evidence)")}
    assert indexes["index_stream_evidence_catalogId_season_episode_sourceId_sourceKey"] == 1
    assert indexes["index_stream_evidence_catalogId_season_episode_outcome"] == 0
    assert indexes["index_stream_evidence_outcome"] == 0

    add(db, 101, 1, "CORRECT", 1_000, 0.98)
    add(db, 101, 1, "WRONG_CONTENT", 2_000, 0.40)
    add(db, 102, 2, "CORRECT", 3_000, 0.99)

    assert db.execute("SELECT COUNT(*) FROM stream_evidence").fetchone()[0] == 2
    assert db.execute(
        "SELECT outcome, identityScore FROM stream_evidence WHERE id = 101"
    ).fetchone() == ("WRONG_CONTENT", 0.4)

    episode_one = db.execute(
        "SELECT episode, outcome FROM stream_evidence "
        "WHERE catalogId = 'aw:title' AND season = 1 AND episode = 1"
    ).fetchall()
    assert episode_one == [(1, "WRONG_CONTENT")]

    filtered = db.execute(
        "SELECT episode FROM stream_evidence "
        "WHERE catalogId = 'aw:title' AND season = 1 AND episode = 1 "
        "AND outcome = 'CORRECT'"
    ).fetchall()
    assert filtered == []
    print("PASS stream_evidence: additive migration, legacy preservation, upsert, episode separation, outcome filter")


if __name__ == "__main__":
    main()
