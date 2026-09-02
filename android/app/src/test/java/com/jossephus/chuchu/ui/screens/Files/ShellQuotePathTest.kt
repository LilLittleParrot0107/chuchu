package com.jossephus.chuchu.ui.screens.Files

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuotePathTest {

    @Test
    fun plainPath_LeftAlone() {
        assertEquals(
            "/home/a/chuchu/inbox/Screenshot_20260831.jpg",
            shellQuotePath("/home/a/chuchu/inbox/Screenshot_20260831.jpg"),
        )
    }

    @Test
    fun spacedName_IsQuoted() {
        assertEquals(
            "'/home/a/chuchu/inbox/Anh chup man hinh.png'",
            shellQuotePath("/home/a/chuchu/inbox/Anh chup man hinh.png"),
        )
    }

    @Test
    fun singleQuoteInName_IsEscaped() {
        // Ten co dau nhay don: dong lai, chen \' roi mo tiep — kieu chuan cua sh.
        assertEquals(
            "'/tmp/it'\\''s here.png'",
            shellQuotePath("/tmp/it's here.png"),
        )
    }

    @Test
    fun shellMetaChars_AreQuoted() {
        assertEquals("'/tmp/a&b(1).png'", shellQuotePath("/tmp/a&b(1).png"))
        assertEquals("'/tmp/\$HOME.png'", shellQuotePath("/tmp/\$HOME.png"))
    }
}
