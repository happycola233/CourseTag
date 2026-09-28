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

    @Test
    fun switchingToUnderscoresPreservesOldSuffixAndOriginalTimestamp() {
        for (known in listOf(false, true)) {
            val parser = PhotoNameParser(TagFormatPreset.Underscore.format, listOf(TagFormat.Default)) {
                known && it == "高等数学"
            }
            val parsed = parser.parse("IMG_20260927_143021（高等数学）.jpg")
            assertEquals("IMG_20260927_143021", parsed.stem)
            assertEquals("高等数学", parsed.course)
            assertEquals(TagFormat.Default, parsed.format)
            assertEquals("IMG_20260927_143021_大学物理.jpg", parser.compose(parsed, "大学物理"))
            assertEquals("IMG_20260927_143021.jpg", parser.compose(parsed, null))
        }
    }

    @Test
    fun ignoredSuffixPreservesTimestampWhenUnderscoreIsCurrentOrPrevious() {
        val formats = listOf(TagFormat.Default, TagFormatPreset.Underscore.format)
        val stem = "IMG_20260927_143021（副本）"
        for (current in formats) {
            val parser = PhotoNameParser(current, formats - current, isIgnored = { it == "副本" }, isKnownCourse = unknown)
            val parsed = parser.parse("$stem.jpg")

            assertEquals(ParsedName(stem, ".jpg", null, null, ignoredTag = "副本"), parsed)
            assertEquals("$stem.jpg", parser.compose(parsed, null))
            val taggedName = parser.compose(parsed, "高等数学")
            assertEquals(current.compose(stem, "高等数学") + ".jpg", taggedName)
            val tagged = parser.parse(taggedName)
            assertEquals("高等数学", tagged.course)
            assertEquals("$stem.jpg", parser.compose(tagged, null))
        }
    }

    @Test
    fun allPresetMigrationsKeepCourseAndOriginalStem() {
        val stem = "IMG_20260927_143021"
        val course = "大学物理A（下）"
        for (old in TagFormatPreset.entries) for (current in TagFormatPreset.entries) {
            val parser = PhotoNameParser(current.format, listOf(old.format)) { it == course }
            val parsed = parser.parse(old.format.compose(stem, course) + ".jpg")
            assertEquals("${old.name} -> ${current.name}", stem, parsed.stem)
            assertEquals(course, parsed.course)
            assertEquals(current.format.compose(stem, course) + ".jpg", parser.compose(parsed, course))
        }
    }

    @Test
    fun knownCourseCanContainItsDelimiter() {
        val course = "Computer_Science"
        val parser = PhotoNameParser(TagFormatPreset.Underscore.format, emptyList()) { it == course }
        val parsed = parser.parse("IMG_0001_Computer_Science.jpg")
        assertEquals("IMG_0001", parsed.stem)
        assertEquals(course, parsed.course)
    }

    @Test
    fun knownCurrentCourseOutranksAnInnerOldFormat() {
        val course = "大学物理（下）"
        for (ignoreInnerSuffix in listOf(false, true)) {
            val parser = PhotoNameParser(
                TagFormatPreset.Underscore.format,
                listOf(TagFormat.Default),
                isIgnored = { ignoreInnerSuffix && it == "下" },
                isKnownCourse = { it == course },
            )
            val parsed = parser.parse("IMG_0001_大学物理（下）.jpg")
            assertEquals("IMG_0001", parsed.stem)
            assertEquals(course, parsed.course)
            assertEquals(TagFormatPreset.Underscore.format, parsed.format)
            assertNull(parsed.ignoredTag)
        }
    }
}
