package com.jossephus.chuchu.ui.screens.Files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/**
 * Chon thu muc dich khi gui file len host.
 *
 * Bug 3/9/2026: "/" lot qua duoc, thanh ra app di tao "/inbox" roi upload chet
 * voi "SFTP open protocol error". Cac test nay khoa dung cho do.
 */
class PickRemoteHomeTest {

    @Test
    fun rootIsNeverHome() {
        assertNull(pickRemoteHome("/"))
        assertNull(pickRemoteHome("/", "//", "   "))
    }

    @Test
    fun blankAndNullAreSkipped() {
        assertEquals("/home/a", pickRemoteHome(null, "", "   ", "/home/a"))
    }

    @Test
    fun firstUsableWins() {
        assertEquals("/home/a/chuchu", pickRemoteHome("/home/a/chuchu", "/home/a"))
    }

    @Test
    fun trailingSlashTrimmed() {
        assertEquals("/home/a", pickRemoteHome("/home/a/"))
    }

    @Test
    fun rootSkippedInFavourOfRealPath() {
        // Dung canh huong that: cache tab giu "/", nhung realpath tra ve home.
        assertEquals("/home/a", pickRemoteHome("/", "/home/a"))
    }

    @Test
    fun everythingUnusableGivesNull() {
        assertNull(pickRemoteHome(null, "", "/"))
    }
}
