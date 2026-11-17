#!/usr/bin/env python3
"""Kleiner HTML-Baum + CSS-Teilmenge fuer den Pack-Worker.

Unterstuetzt genau die Syntax, die die Source-Packs verwenden:
  * `tag`, `.class`, `#id`, `[attr]`, `[attr=value]`, `tag.class`
  * Nachfahren-Kombinator (Leerzeichen), z. B. `.folge-list .folge`
  * `*`
Keine Pseudo-Klassen, kein `>`/`+`, keine Attributoperatoren. Das ist Absicht:
  der Worker soll nur belegen, was der Pack-Interpreter (Jsoup) auch nutzt.
"""

from __future__ import annotations

import re
from html.parser import HTMLParser

VOID_TAGS = {
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
    "meta", "param", "source", "track", "wbr",
}


class Node:
    __slots__ = ("tag", "attrs", "children", "parent", "text_parts")

    def __init__(self, tag: str, attrs: dict[str, str], parent: "Node | None" = None):
        self.tag = tag
        self.attrs = attrs
        self.children: list[Node] = []
        self.parent = parent
        self.text_parts: list[str] = []

    def text(self) -> str:
        parts = list(self.text_parts)
        for child in self.children:
            parts.append(child.text())
        return re.sub(r"\s+", " ", " ".join(parts)).strip()

    def attr(self, name: str) -> str:
        return self.attrs.get(name, "")

    def classes(self) -> list[str]:
        return self.attr("class").split()

    def descendants(self):
        for child in self.children:
            yield child
            yield from child.descendants()

    def __repr__(self) -> str:  # pragma: no cover - debug aid
        return f"<{self.tag} {self.attrs}>"


class _TreeBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.root = Node("#document", {})
        self.stack = [self.root]

    def handle_starttag(self, tag, attrs):
        node = Node(tag.lower(), {k.lower(): (v or "") for k, v in attrs}, self.stack[-1])
        self.stack[-1].children.append(node)
        if tag.lower() not in VOID_TAGS:
            self.stack.append(node)

    def handle_startendtag(self, tag, attrs):
        node = Node(tag.lower(), {k.lower(): (v or "") for k, v in attrs}, self.stack[-1])
        self.stack[-1].children.append(node)

    def handle_endtag(self, tag):
        tag = tag.lower()
        for index in range(len(self.stack) - 1, 0, -1):
            if self.stack[index].tag == tag:
                del self.stack[index:]
                return

    def handle_data(self, data):
        if data.strip():
            self.stack[-1].text_parts.append(data)


def parse(html: str) -> Node:
    builder = _TreeBuilder()
    builder.feed(html)
    builder.close()
    return builder.root


_SIMPLE = re.compile(r"^([a-zA-Z0-9_*-]*)((?:[.#][\w-]+|\[[^\]]+\])*)$")


def _matches(node: Node, simple: str) -> bool:
    match = _SIMPLE.match(simple)
    if not match:
        return False
    tag, rest = match.group(1), match.group(2)
    if tag and tag != "*" and node.tag != tag:
        return False
    for token in re.findall(r"[.#][\w-]+|\[[^\]]+\]", rest):
        if token.startswith("."):
            if token[1:] not in node.classes():
                return False
        elif token.startswith("#"):
            if node.attr("id") != token[1:]:
                return False
        elif token.startswith("["):
            body = token[1:-1]
            if "=" in body:
                name, value = body.split("=", 1)
                if node.attr(name.strip().lower()) != value.strip().strip("\"'"):
                    return False
            elif not node.attr(body.strip().lower()):
                return False
    return True


def select(root: Node, selector: str) -> list[Node]:
    """Descendant-only CSS subset; returns document order, no duplicates."""
    steps = [step for step in selector.strip().split() if step]
    if not steps:
        return []
    current: list[Node] = [root]
    for step in steps:
        next_nodes: list[Node] = []
        for parent in current:
            for node in parent.descendants():
                if _matches(node, step) and node not in next_nodes:
                    next_nodes.append(node)
        current = next_nodes
        if not current:
            return []
    return current


def select_first(root: Node, selector: str) -> Node | None:
    hits = select(root, selector)
    return hits[0] if hits else None
