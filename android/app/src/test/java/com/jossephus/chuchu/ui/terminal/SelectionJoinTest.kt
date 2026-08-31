package com.jossephus.chuchu.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Nut "copy 1 dòng": noi vung chon nhieu dong thanh mot.
 *
 * Diem then chot la KHONG duoc them dau cach o cho terminal be dong vi het be
 * ngang — do la cho gay lenh/duong dan khi dan ra.
 */
class SelectionJoinTest {

    @Test
    fun widthForcedBreak_JoinsWithoutSpace() {
        // 19 cot: dong dau trai kin -> bi be giua tu, phai dinh lien.
        val raw = "/home/a/chuchu/repo\n/android/app/build"
        assertEquals(19, "/home/a/chuchu/repo".length)
        assertEquals("/home/a/chuchu/repo/android/app/build", joinSelectionLines(raw, 19))
    }

    @Test
    fun realNewline_JoinsWithSingleSpace() {
        // Dong dau ngan hon be ngang -> la xuong dong that.
        val raw = "git commit -m\n\"sua loi copy\""
        assertEquals("git commit -m \"sua loi copy\"", joinSelectionLines(raw, 40))
    }

    @Test
    fun trailingSpaceBeforeBreak_KeepsExactlyOneSpace() {
        // App be dong o ranh gioi tu: dau cach cuoi dong da bi trim, do rong con
        // 19 < 20 -> nhanh "mot dau cach", khong dinh lien hai tu.
        val raw = "ls -la /home/a/chu\nchu"
        assertEquals("ls -la /home/a/chu chu", joinSelectionLines(raw, 20))
    }

    @Test
    fun indentedContinuation_IsTrimmedNotDoubled() {
        // Dong tiep theo thut le: chi con MOT dau cach, khong phai ca cum thut le.
        val raw = "chay lenh nay\n    roi lenh kia"
        assertEquals("chay lenh nay roi lenh kia", joinSelectionLines(raw, 40))
    }

    @Test
    fun blankLine_NeverGluesAcross() {
        val raw = "phan mot day kin het\n\nphan hai"
        assertEquals("phan mot day kin het phan hai", joinSelectionLines(raw, 20))
    }

    @Test
    fun threeRowWrap_GluesEveryForcedBreak() {
        val raw = "aaaaa\nbbbbb\nccccc"
        assertEquals("aaaaabbbbbccccc", joinSelectionLines(raw, 5))
    }

    @Test
    fun wideChars_CountAsTwoCells() {
        // 4 chu Han = 8 o = day kin man 8 cot -> dinh lien.
        val raw = "你好世界\ntiep"
        assertEquals("你好世界tiep", joinSelectionLines(raw, 8))
        // Cung chuoi do tren man 12 cot thi chua kin -> them dau cach.
        assertEquals("你好世界 tiep", joinSelectionLines(raw, 12))
    }

    @Test
    fun combiningMarks_DoNotInflateWidth() {
        // "te" + dau huyen roi (NFD) van chi la 2 o, chua kin man 4 cot.
        val raw = "tè\nsau"
        assertEquals("tè sau", joinSelectionLines(raw, 4))
    }

    @Test
    fun unknownCols_FallsBackToSpacedJoin() {
        val raw = "mot\nhai"
        assertEquals("mot hai", joinSelectionLines(raw, 0))
    }

    @Test
    fun singleLine_Unchanged() {
        assertEquals("chi mot dong", joinSelectionLines("chi mot dong", 20))
    }
}
