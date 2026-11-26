package de.hendler.frame61.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogItemMetadataTest {

    @Test
    fun `backdrop und cast werden optional gelesen`() {
        val repo = CatalogRepository.fromJson(
            """
            {
              "items": [{
                "id": "s1", "title": "Serie", "type": "series",
                "backdrop": "https://example.invalid/backdrop.jpg",
                "cast": [{"name":"Person A","character":"Rolle A"},{"name":"Person B"}]
              }]
            }
            """.trimIndent()
        )
        val item = repo.byId("s1")!!
        assertEquals("https://example.invalid/backdrop.jpg", item.backdrop)
        assertEquals(listOf("Person A", "Person B"), item.cast.map { it.name })
    }

    @Test
    fun `alte Kataloge erhalten saubere Defaults`() {
        val repo = CatalogRepository.fromJson(
            """{"items":[{"id":"m1","title":"Film","type":"movie"}]}"""
        )
        val item = repo.byId("m1")!!
        assertEquals("", item.backdrop)
        assertTrue(item.cast.isEmpty())
    }
}
