package dev.matejgroombridge.voquab.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryLookupTest {

    private val sample = """
        [{"word":"lugubrious","meanings":[{"partOfSpeech":"adjective",
          "definitions":[{"definition":"Looking or sounding sad and dismal.",
            "example":"his face looked even more lugubrious than usual","synonyms":["mournful"]}],
          "synonyms":["gloomy","sombre"]}]}]
    """.trimIndent()

    @Test
    fun `parses the first sense into a word`() {
        val w = DictionaryLookup.parse("lugubrious", sample)!!
        assertEquals("custom-lugubrious", w.id)
        assertEquals("adjective", w.pos)
        assertEquals("mournful", w.gloss)
        assertEquals("Looking or sounding sad and dismal.", w.definition)
        assertEquals(listOf("his face looked even more *lugubrious* than usual"), w.examples)
        assertTrue("mournful" in w.synonyms)
    }

    @Test
    fun `falls back to a trimmed definition for the gloss`() {
        val body = """[{"meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"A very long explanation of something obscure."}]}]}]"""
        val w = DictionaryLookup.parse("thing", body)!!
        assertTrue(w.gloss.length <= 18)
        assertTrue(w.gloss.endsWith("…"))
        assertTrue(w.examples.isEmpty())
    }

    @Test
    fun `not-found responses give null`() {
        assertNull(DictionaryLookup.parse("zzz", """{"title":"No Definitions Found"}"""))
    }

    @Test
    fun `parses wiktionary html into plain text with the word marked`() {
        val body = """{"en":[{"partOfSpeech":"Adjective","definitions":[{"definition":"<a href=\"/wiki/gloomy\">Gloomy</a>, <a>mournful</a> or dismal.","examples":["His client’s <b>lugubrious</b> expression tipped him off."]}]}]}"""
        val w = DictionaryLookup.parseWiktionary("lugubrious", body)!!
        assertEquals("adjective", w.pos)
        assertEquals("Gloomy, mournful or dismal.", w.definition)
        assertEquals("gloomy", w.gloss)
        assertEquals(listOf("His client’s *lugubrious* expression tipped him off."), w.examples)
    }

    @Test
    fun `ids are slugged`() {
        assertEquals("custom-mod-cons", DictionaryLookup.customId(" Mod Cons "))
    }
}
