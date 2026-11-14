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

    /**
     * 累計関数 C(lv) = ceil(4 × ((lv−0.5)^2.5 − 0.5^2.5)) から、必要 EXP = C(lv+1) − C(lv)。
     * 従来の ceil(10 × lv^1.5)(Lv9: 270、Lv100: 10000 など)とほぼ同じ形になる。
     */
    @Test
    fun キャラの必要EXP曲線() {
        assertEquals(0L, Progression.charExpCumulative(1))
        assertEquals(11L, Progression.charExpCumulative(2))
        assertEquals(39L, Progression.charExpCumulative(3))
        assertEquals(11L, Progression.charExpToNext(1))
        assertEquals(28L, Progression.charExpToNext(2))
        assertEquals(81L, Progression.charExpToNext(4))
        assertEquals(270L, Progression.charExpToNext(9))
        assertEquals(10_000L, Progression.charExpToNext(100))
    }

    /** 従来の曲線 ceil(10 × lv^1.5) と ±1% 以内(バランスがほとんど動かないことの確認。Lv1 は絶対差 1 まで許す)。 */
    @Test
    fun 曲線は従来とほぼ同じ形() {
        for (lv in listOf(1L, 2L, 3L, 5L, 10L, 50L, 100L, 1_000L, 100_000L)) {
            val old = kotlin.math.ceil(10.0 * lv * kotlin.math.sqrt(lv.toDouble()))
            val diff = kotlin.math.abs(Progression.charExpToNext(lv) - old)
            assertTrue(diff <= 1.0 || diff / old < 0.01, "lv=$lv new=${Progression.charExpToNext(lv)} old=$old")
        }
    }

    /** 累計は Lv1 以上の到達可能な全範囲で狭義単調増加(逆引きの二分探索が成り立つ前提)。 */
    @Test
    fun 累計曲線は狭義単調増加() {
        var prev = Progression.charExpCumulative(1)
        for (lv in 2L..300_000L) {
            val c = Progression.charExpCumulative(lv)
            assertTrue(c > prev, "lv=$lv C=$c prev=$prev")
            prev = c
        }
        // 巨大なレベルでも(Long に収まる範囲の)隣り合うレベルで単調
        for (lv in listOf(1_000_000L, 5_000_000L, 10_000_000L, 14_000_000L)) {
            assertTrue(Progression.charExpCumulative(lv + 1) > Progression.charExpCumulative(lv), "lv=$lv")
        }
    }

    /** 巨大なレベルでも Long で飽和し、負数にならない。 */
    @Test
    fun キャラの必要EXPは飽和する() {
        assertEquals(Long.MAX_VALUE, Progression.charExpToNext(Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, Progression.charExpCumulative(Long.MAX_VALUE))
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
        assertEquals(CharExpResult(1, 10, 0), Progression.gainCharExp(1, 0, 10)) // 必要 11 の 1 手前
        assertEquals(CharExpResult(2, 0, 1), Progression.gainCharExp(1, 0, 11))
    }

    /** 持ち越し EXP も含めて判定する。 */
    @Test
    fun 持ち越しEXPを含めて判定() {
        assertEquals(CharExpResult(2, 2, 1), Progression.gainCharExp(1, 8, 5)) // 13 - 11 = 2
    }

    /** 1 回の獲得で複数レベル上がる(11 + 28 = 39 で Lv3、40 なら余り 1)。 */
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

    /** 1 レベルずつ上げる基準実装(累計関数ではなく charExpToNext の積み上げ)。小さな獲得の照合に使う。 */
    private fun stepByStep(level: Long, exp: Long, gained: Long): CharExpResult {
        var lv = level
        var cur = exp + gained
        while (cur >= Progression.charExpToNext(lv)) {
            cur -= Progression.charExpToNext(lv)
            lv++
        }
        return CharExpResult(lv, cur, lv - level)
    }

    /** 小さな獲得は、1 レベルずつのループと完全に一致する(開始レベル・持ち越し EXP を変えて多数)。 */
    @Test
    fun 小さな獲得はステップ計算と一致() {
        for (start in listOf(1L, 2L, 7L, 50L, 999L)) {
            for (carry in listOf(0L, 1L, 5L)) {
                for (gained in listOf(0L, 1L, 10L, 11L, 100L, 1_234L, 99_999L, 1_000_000L)) {
                    assertEquals(
                        stepByStep(start, carry, gained),
                        Progression.gainCharExp(start, carry, gained),
                        "start=$start carry=$carry gained=$gained",
                    )
                }
            }
        }
    }

    /**
     * 5 兆の獲得は正確(近似ではない)。累計関数 C に対する基準:
     * 到達レベル lv は C(lv) <= 総量 < C(lv+1) をちょうど満たす最大のレベルで、exp = 総量 − C(lv)。
     * 基準側は二分探索ではなく線形探索で求め、実装と独立に突き合わせる。
     */
    @Test
    fun 巨大な獲得でも正確() {
        val gained = 5_000_000_000_000L // 5 兆(約 16 万レベル分)
        val r = Progression.gainCharExp(1, 0, gained)
        var lv = 1L
        while (Progression.charExpCumulative(lv + 1) <= gained) lv++ // 線形探索の基準
        assertEquals(lv, r.level)
        assertEquals(gained - Progression.charExpCumulative(lv), r.exp)
        assertEquals(lv - 1, r.levelsGained)
        assertTrue(r.exp >= 0 && r.exp < Progression.charExpToNext(r.level))
        // 途中から(Lv500、持ち越し 123)でも同様
        val r2 = Progression.gainCharExp(500, 123, gained)
        val total = Progression.charExpCumulative(500) + 123 + gained
        var lv2 = 500L
        while (Progression.charExpCumulative(lv2 + 1) <= total) lv2++
        assertEquals(CharExpResult(lv2, total - Progression.charExpCumulative(lv2), lv2 - 500), r2)
    }

    /** 分割して獲得しても、まとめて獲得しても同じ結果になる(厳密な累計である証拠)。 */
    @Test
    fun 分割獲得とまとめ獲得が一致() {
        val whole = Progression.gainCharExp(1, 0, 3_000_000_000L)
        val a = Progression.gainCharExp(1, 0, 1_234_567_890L)
        val b = Progression.gainCharExp(a.level, a.exp, 3_000_000_000L - 1_234_567_890L)
        assertEquals(whole.level, b.level)
        assertEquals(whole.exp, b.exp)
        assertEquals(whole.levelsGained, a.levelsGained + b.levelsGained)
    }

    /** Long の上限級の獲得でもハングせず、不変条件(0 <= exp < 必要 EXP)を満たして飽和する。 */
    @Test
    fun 上限級の獲得でも終了する() {
        val r = Progression.gainCharExp(1, 0, Long.MAX_VALUE)
        assertTrue(r.level > 1_000_000L, "level=${r.level}")
        assertTrue(r.exp >= 0 && r.exp < Progression.charExpToNext(r.level))
        assertEquals(r.level - 1, r.levelsGained)
        // 獲得が多いほどレベルは下がらない(単調)
        val smaller = Progression.gainCharExp(1, 0, Long.MAX_VALUE / 2)
        assertTrue(smaller.level <= r.level)
        // 既にかなり高いレベルから上限を足しても終了する(累計が飽和する)
        val high = Progression.gainCharExp(10_000_000, Long.MAX_VALUE - 5, Long.MAX_VALUE)
        assertTrue(high.level >= 10_000_000 && high.exp >= 0)
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
