import unittest

import cssmini

HTML = """
<html><body>
  <div class="movie-list">
    <article class="movie-item" data-id="42">
      <h3><a href="/film/42">Film Zwei</a></h3>
      <span class="jahr">2021</span>
    </article>
    <article class="movie-item" data-id="43">
      <h3><a href="/film/43">Film Drei</a></h3>
      <span class="jahr">2022</span>
    </article>
  </div>
  <div id="player"><a class="source" data-health="0.9" href="/stream/1">HD</a></div>
</body></html>
"""


class CssMiniTest(unittest.TestCase):
    def setUp(self):
        self.doc = cssmini.parse(HTML)

    def test_class_and_descendant(self):
        self.assertEqual(2, len(cssmini.select(self.doc, ".movie-item")))
        self.assertEqual(2, len(cssmini.select(self.doc, ".movie-list .movie-item")))
        self.assertEqual(0, len(cssmini.select(self.doc, ".player .movie-item")))

    def test_tag_and_child_of_class(self):
        links = cssmini.select(self.doc, "h3 a")
        self.assertEqual(["Film Zwei", "Film Drei"], [link.text() for link in links])

    def test_attribute_and_id(self):
        source = cssmini.select_first(self.doc, "a.source[data-health]")
        self.assertIsNotNone(source)
        self.assertEqual("/stream/1", source.attr("href"))
        self.assertIsNotNone(cssmini.select_first(self.doc, "#player a"))

    def test_tag_class_combination(self):
        items = cssmini.select(self.doc, "article.movie-item")
        self.assertEqual(2, len(items))
        self.assertEqual("42", items[0].attr("data-id"))


if __name__ == "__main__":
    unittest.main()
