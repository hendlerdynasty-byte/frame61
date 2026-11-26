#!/usr/bin/env python3
"""Leichter Test für die Richtigkeits-Auswertung (kein Android/Netz)."""
import contextlib
import importlib.util
import io
import pathlib
import tempfile
import unittest

TOOL = pathlib.Path(__file__).with_name("analyse_richtig.py")
SPEC = importlib.util.spec_from_file_location("analyse_richtig", TOOL)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AnalyseRichtigTest(unittest.TestCase):
    def run_tool(self, xml: str) -> str:
        with tempfile.TemporaryDirectory() as tmp:
            pathlib.Path(tmp, "latest.xml").write_text(xml, encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                code = MODULE.main(tmp, "train")
            self.assertEqual(0, code)
            return output.getvalue()

    def test_liest_entity_und_alle_zeilen(self):
        xml = """<hierarchy><node text="QUOTA IDENT train idx=1 id=aw:a group=aniworld played=ja ident=CORRECT score=1.00 evidenz=quelle:source&#10;QUOTA IDENT train idx=2 id=aw:b group=aniworld played=nein ident=- score=0.00 evidenz=-&#10;QUOTA RIGHT SUMMARY mode=train richtig=1/2 quote=50 gespielt=1/2 falsch=[]" /></hierarchy>"""
        out = self.run_tool(xml)
        self.assertIn("Titel mit IDENT-Zeile: 2", out)
        self.assertIn("gespielt: 1  richtig: 1", out)
        self.assertIn("aniworld", out)

    def test_spielbarer_spaeterer_dump_gewinnt(self):
        xml = """<hierarchy><node text="QUOTA IDENT train idx=1 id=aw:a group=aniworld played=nein ident=- score=0.00 evidenz=-&#10;QUOTA IDENT train idx=1 id=aw:a group=aniworld played=ja ident=CORRECT score=1.00 evidenz=quelle:source" /></hierarchy>"""
        out = self.run_tool(xml)
        self.assertIn("Titel mit IDENT-Zeile: 1", out)
        self.assertIn("gespielt: 1  richtig: 1", out)

    def test_falscher_titel_wird_gezaehlt(self):
        xml = """<hierarchy><node text="QUOTA IDENT train idx=1 id=ss:x group=serienstream played=ja ident=WRONG_CONTENT score=0.50 evidenz=quelle:source grund=Titel passt nicht" /></hierarchy>"""
        out = self.run_tool(xml)
        self.assertIn("gespielt: 1  richtig: 0", out)
        self.assertIn("WRONG_CONTENT:1", out)


if __name__ == "__main__":
    unittest.main()
