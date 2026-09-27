package com.happycola233.coursetag.data.naming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagFormatTest {
    private val unknown: (String) -> Boolean = { false }

    @Test
    fun extractsNestedFullWidthParentheses() {
        val match = TagFormat.Default.extract("IMG_20260927_143021（大学物理A（下））", unknown)
        assertEquals(TagMatch("IMG_20260927_143021", "大学物理A（下）"), match)
    }

    @Test
    fun ignoresSystemDuplicateSuffix() {
        val parentheses = TagFormatPreset.Parentheses.format
        assertNull(parentheses.extract("IMG_0001 (1)", unknown))
        assertNull(parentheses.extract("Screenshot (edited)", unknown))
        assertEquals("Calculus", parentheses.extract("IMG_0001(Calculus)") { it == "Calculus" }?.course)
    }

    @Test
    fun formatsWithoutClosingRequireRecognizableCourse() {
        val underscore = TagFormatPreset.Underscore.format
        assertEquals("高等数学", underscore.extract("IMG_20260927_143021_高等数学", unknown)?.course)
        assertNull(underscore.extract("IMG_20260927_143021", unknown))
        assertNull(underscore.extract("Screenshot_2026_com.tencent.mm", unknown))
    }

    @Test
    fun parserFallsBackToPreviousFormatsAndComposesWithCurrent() {
        val parser = PhotoNameParser(
            current = TagFormatPreset.LenticularBrackets.format,
            previous = listOf(TagFormat.Default),
            isKnownCourse = unknown,
        )
        val parsed = parser.parse("IMG_0001（线性代数）.jpg")
        assertEquals("线性代数", parsed.course)
        assertEquals(TagFormat.Default, parsed.format)
        assertEquals("IMG_0001【线性代数】.jpg", parser.compose(parsed, parsed.course))
        assertEquals("IMG_0001.jpg", parser.compose(parsed, null))
    }

    @Test
    fun sanitizesCharactersRejectedByStorage() {
        assertEquals("C／C++ 程序设计：上", FileNames.sanitizeCourseName("  C/C++   程序设计:上 "))
    }
}
