// =============================================================================
// ProgressionTest.kt
// 役割: キャラ EXP / 職業 EXP の曲線とレベルアップ・マスター判定の仕様を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.data.JobProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressionTest {

    // ---------- キャラ EXP ----------

    /** 必要 EXP = ceil(10 × lv^1.5)。 */
    @Test
    fun キャラの必要EXP曲線() {
        assertEquals(10L, Progression.charExpToNext(1))
        assertEquals(29L, Progression.charExpToNext(2)) // 10 × 2.828... = 28.28 → 29(切り上げ)
        assertEquals(80L, Progression.charExpToNext(4))
        assertEquals(270L, Progression.charExpToNext(9))
        assertEquals(10_000L, Progression.charExpToNext(100))
    }

    /** 巨大なレベルでも Long で飽和し、負数にならない。 */
    @Test
    fun キャラの必要EXPは飽和する() {
        assertEquals(Long.MAX_VALUE, Progression.charExpToNext(Long.MAX_VALUE))
        assertTrue(Progression.charExpToNext(Long.MAX_VALUE / 2) > 0)
    }

    /** レベル 1 未満は不正。 */
    @Test
    fun キャラの必要EXPはLv1未満で失敗() {
        val e = assertFailsWith<IllegalArgumentException> { Progression.charExpToNext(0) }
        assertTrue(e.message!!.contains("レベル"))
    }

    /** 足りなければレベルは上がらず、EXP だけ溜まる。 */
    @Test
    fun EXPが足りなければ上がらない() {
        val r = Progression.gainCharExp(level = 1, exp = 0, gained = 9)
        assertEquals(CharExpResult(level = 1, exp = 9, levelsGained = 0), r)
    }

    /** ちょうど必要 EXP に達したらレベルアップし、EXP は 0(境界値)。 */
    @Test
    fun ちょうどでレベルアップ() {
        assertEquals(CharExpResult(2, 0, 1), Progression.gainCharExp(1, 0, 10))
    }

    /** 持ち越し EXP も含めて判定する。 */
    @Test
    fun 持ち越しEXPを含めて判定() {
        assertEquals(CharExpResult(2, 3, 1), Progression.gainCharExp(1, 8, 5)) // 13 - 10 = 3
    }

    /** 1 回の獲得で複数レベル上がる(10 + 29 = 39 で Lv3、40 なら余り 1)。 */
    @Test
    fun 一度に複数レベル上がる() {
        assertEquals(CharExpResult(3, 0, 2), Progression.gainCharExp(1, 0, 39))
        assertEquals(CharExpResult(3, 1, 2), Progression.gainCharExp(1, 0, 40))
    }

    /** 獲得 0 は何も変わらない。負は不正。 */
    @Test
    fun 獲得EXPの境界() {
        assertEquals(CharExpResult(5, 7, 0), Progression.gainCharExp(5, 7, 0))
        assertFailsWith<IllegalArgumentException> { Progression.gainCharExp(1, 0, -1) }
    }

    /** レベルが Long の上限ならそれ以上上がらず、EXP は飽和して溜まる(無限ループしない)。 */
    @Test
    fun レベル上限では上がらない() {
        val r = Progression.gainCharExp(Long.MAX_VALUE, 0, 100)
        assertEquals(Long.MAX_VALUE, r.level)
        assertEquals(0, r.levelsGained)
        assertEquals(100L, r.exp)
        val r2 = Progression.gainCharExp(Long.MAX_VALUE, Long.MAX_VALUE - 1, 100)
        assertEquals(Long.MAX_VALUE, r2.exp) // 飽和
    }

    // ---------- 職業 EXP ----------

    /** 必要 EXP = 2 + jobLv / 5(整数除算)。 */
    @Test
    fun 職業の必要EXP曲線() {
        assertEquals(2L, Progression.jobExpToNext(1))
        assertEquals(2L, Progression.jobExpToNext(4))
        assertEquals(3L, Progression.jobExpToNext(5))
        assertEquals(7L, Progression.jobExpToNext(29))
    }

    @Test
    fun 職業EXPの獲得でレベルアップ() {
        val r = Progression.gainJobExp(JobProgress(), gained = 1)
        assertEquals(JobProgress(level = 1, exp = 1), r.progress)
        assertEquals(0, r.levelsGained)
        val r2 = Progression.gainJobExp(r.progress, gained = 1)
        assertEquals(JobProgress(level = 2, exp = 0), r2.progress)
        assertEquals(1, r2.levelsGained)
    }

    /** 一度に複数レベル(必要 EXP 2 × 3 回 = 6 で Lv4)。 */
    @Test
    fun 職業EXPで複数レベル() {
        val r = Progression.gainJobExp(JobProgress(), gained = 6)
        assertEquals(4, r.progress.level)
        assertEquals(3, r.levelsGained)
    }

    /** Lv30 に到達した瞬間にマスターになる。 */
    @Test
    fun 職業Lv30でマスター() {
        val r = Progression.gainJobExp(JobProgress(level = 29), gained = 7)
        assertEquals(30, r.progress.level)
        assertTrue(r.progress.mastered)
        assertTrue(r.newlyMastered)
    }

    /** Lv29 では未マスター(境界値)。 */
    @Test
    fun Lv29では未マスター() {
        val r = Progression.gainJobExp(JobProgress(level = 28), gained = 7)
        assertEquals(29, r.progress.level)
        assertFalse(r.progress.mastered)
        assertFalse(r.newlyMastered)
    }

    /** マスター後もレベルは上がるが、newlyMastered は 1 回だけ。 */
    @Test
    fun マスターは1回だけ() {
        val mastered = JobProgress(level = 30, exp = 0, mastered = true)
        val r = Progression.gainJobExp(mastered, gained = 8) // 30 → 必要 8
        assertEquals(31, r.progress.level)
        assertTrue(r.progress.mastered)
        assertFalse(r.newlyMastered)
    }

    /** 一気に 30 を飛び越えても、マスターは成立し 1 回だけ通知される。 */
    @Test
    fun 一気に飛び越えてもマスター() {
        val r = Progression.gainJobExp(JobProgress(level = 29), gained = 100)
        assertTrue(r.progress.level > 30)
        assertTrue(r.newlyMastered)
    }

    @Test
    fun 職業EXPの負の獲得は不正() {
        assertFailsWith<IllegalArgumentException> { Progression.gainJobExp(JobProgress(), -1) }
    }
}
