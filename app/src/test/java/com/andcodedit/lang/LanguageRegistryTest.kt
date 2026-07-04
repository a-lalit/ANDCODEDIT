package com.andcodedit.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRegistryTest {

    @Test
    fun `ids are unique`() {
        val ids = LanguageRegistry.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `file extensions are unique`() {
        val exts = LanguageRegistry.all.map { it.fileExtension.lowercase() }
        assertEquals(exts.size, exts.distinct().size)
    }

    @Test
    fun `byId finds languages case-insensitively`() {
        assertEquals("python", LanguageRegistry.byId("Python")?.id)
        assertEquals("cpp", LanguageRegistry.byId("CPP")?.id)
    }

    @Test
    fun `byId returns null for unknown language`() {
        assertNull(LanguageRegistry.byId("brainfuck"))
    }

    @Test
    fun `byExtension handles leading dot and case`() {
        assertEquals("python", LanguageRegistry.byExtension(".py")?.id)
        assertEquals("python", LanguageRegistry.byExtension("PY")?.id)
        assertEquals("kotlin", LanguageRegistry.byExtension("kt")?.id)
        assertNull(LanguageRegistry.byExtension(".unknown"))
    }

    @Test
    fun `monacoIdFor falls back to plaintext`() {
        assertEquals("python", LanguageRegistry.monacoIdFor("python"))
        assertEquals("shell", LanguageRegistry.monacoIdFor("bash"))
        assertEquals("plaintext", LanguageRegistry.monacoIdFor("nope"))
    }

    @Test
    fun `compiled languages with a compile step reference their output`() {
        LanguageRegistry.all
            .filter { it.compileTemplate != null }
            .forEach { lang ->
                assertNotNull(lang.compileTemplate)
                assertTrue(
                    "compile template for ${lang.id} must reference {file}",
                    lang.compileTemplate!!.contains("{file}")
                )
            }
    }

    @Test
    fun `run templates are non-blank unless the language runs in-process`() {
        LanguageRegistry.all.forEach { lang ->
            if (lang.requiredBinaries.isEmpty()) return@forEach // bundled/in-process
            assertTrue("run template for ${lang.id} must not be blank", lang.runTemplate.isNotBlank())
        }
    }
}
