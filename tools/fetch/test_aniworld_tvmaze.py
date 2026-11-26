from __future__ import annotations

import json
import unittest
from pathlib import Path

import aniworld_tvmaze as tool

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_IDS = [
    "aw:your-name",
    "aw:a-tale-of-the-secret-saint",
    "aw:wedding-peach",
    "aw:roll-over-and-die",
    "aw:the-vampire-dies-in-no-time",
    "aw:when-will-ayumu-make-his-move",
    "aw:machikado-mazoku",
    "aw:afro-samurai",
    "aw:super-psychic-policeman-chojo",
    "aw:air-master",
    "aw:origin-spirits-of-the-past",
    "aw:this-monster-wants-to-eat-me",
    "aw:mix-meisei-story",
    "aw:elegant-yokai-apartment-life",
    "aw:food-for-the-soul",
    "aw:relife",
    "aw:beautiful-bones-sakurakos-investigation",
    "aw:watari-kuns-is-about-to-collapse",
    "aw:hal",
    "aw:slam-dunk",
]


class AniworldTargetsTest(unittest.TestCase):
    def test_exactly_the_twenty_owner_targets(self) -> None:
        targets = tool.load_targets(ROOT)
        self.assertEqual(EXPECTED_IDS, [target["id"] for target in targets])
        self.assertEqual(20, len({target["id"] for target in targets}))

    def test_html_entities_are_decoded_before_markup_is_removed(self) -> None:
        self.assertEqual(
            "Watari-kun's ****** Is about to Collapse",
            tool.clean_text("Watari-kun&#039;s ****** Is about to Collapse"),
        )
        self.assertEqual("Rock & Roll", tool.clean_text("<b>Rock &amp; Roll</b>"))

    def test_exact_animation_match_beats_a_stronger_fuzzy_hit(self) -> None:
        target = {
            "id": "aw:beautiful-bones-sakurakos-investigation",
            "aliases": ["Sakurako-san no Ashimoto ni wa Shitai ga Umatteiru"],
        }
        results = [
            {"score": 99, "show": {"id": 1, "name": "Sakurako Quest", "type": "Scripted"}},
            {"score": 0.91, "show": {"id": 2, "name": "Sakurako-san no Ashimoto ni wa Shitai ga Umatteiru", "type": "Animation"}},
        ]
        show, score = tool.select_tvmaze(results, target)
        self.assertEqual(2, show["id"])
        self.assertEqual(0.91, score)

    def test_meta_index_merge_preserves_unrelated_entries_and_order(self) -> None:
        existing = {
            "schema": 1,
            "generatedAt": "old",
            "entries": [
                {"id": "tvmaze-2993", "title": "Stranger Things"},
                {"id": "aw:your-name", "title": "old"},
            ],
        }
        updates = [
            {"id": "aw:your-name", "title": "Your Name", "year": 2016, "type": "movie", "poster": "file:///android_asset/posters/aw-your-name.jpg"},
            {"id": "aw:hal", "title": "Hal", "year": 2013, "type": "movie", "poster": "file:///android_asset/posters/aw-hal.jpg"},
        ]
        merged = tool.merge_index(existing, updates, "new")
        self.assertEqual("new", merged["generatedAt"])
        self.assertEqual(["tvmaze-2993", "aw:your-name", "aw:hal"], [entry["id"] for entry in merged["entries"]])
        self.assertEqual("Stranger Things", merged["entries"][0]["title"])
        self.assertEqual("Your Name", merged["entries"][1]["title"])


class AniworldAssetsTest(unittest.TestCase):
    def test_twenty_local_metadata_and_image_deep_paths(self) -> None:
        reports = tool.validate_outputs(ROOT)
        self.assertEqual(20, len(reports))
        by_id = {report["id"]: report for report in reports}
        self.assertEqual(set(EXPECTED_IDS), set(by_id))
        for target in tool.load_targets(ROOT):
            report = by_id[target["id"]]
            self.assertGreater(report["poster"]["width"], 199)
            self.assertGreater(report["poster"]["height"], 299)
            self.assertGreaterEqual(report["backdrop"]["width"], 320)
            self.assertGreaterEqual(report["backdrop"]["height"], 180)
            self.assertGreater(report["cast"], 0)
            if target["type"] == "series":
                self.assertGreater(report["episodes"], 0)
            else:
                self.assertEqual(0, report["episodes"])
            self.assertEqual(64, len(report["poster"]["sha256"]))
            self.assertEqual(64, len(report["backdrop"]["sha256"]))

    def test_existing_meta_index_entries_survive(self) -> None:
        index_path = ROOT / "app/src/main/assets/meta/index.json"
        index = json.loads(index_path.read_text(encoding="utf-8"))
        ids = {entry["id"] for entry in index["entries"]}
        self.assertIn("tvmaze-2993", ids)
        self.assertIn("tvmaze-169", ids)
        self.assertTrue(set(EXPECTED_IDS).issubset(ids))
        self.assertGreaterEqual(len(ids), 1000)


if __name__ == "__main__":
    unittest.main()
