// =============================================================================
// StatCalculatorTest.kt
// 役割: 最終ステータスの計算式
//       ((base + growth × (lv−1)) × 1.03^(lv−1)) × (1 + マスターボーナス) + 装備 を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.progression

import io.github.jacoloves.ffa.domain.StatRatios
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.data.JobId
import io.github.jacoloves.ffa.domain.data.JobProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatCalculatorTest {

    private val warrior = GameData.job(JobId.WARRIOR) // base 30/8/4/5/3, growth 6/3/1/1/1

    /** Lv1 は基礎値そのもの。 */
    @Test
    fun Lv1は基礎値() {
        assertEquals(Stats(30, 8, 4, 5, 3), StatCalculator.calculate(warrior, 1))
    }

    /** Lv2: (base + growth) × 1.03 を切り捨て。 */
    @Test
    fun Lv2の計算() {
        // hp 36×1.03=37.08, atk 11×1.03=11.33, def 5×1.03=5.15, spd 6×1.03=6.18, luk 4×1.03=4.12
        assertEquals(Stats(37, 11, 5, 6, 4), StatCalculator.calculate(warrior, 2))
    }

    /** レベルが上がるほど全項目が単調に増える。 */
    @Test
    fun レベルで単調増加() {
        var prev = StatCalculator.calculate(warrior, 1)
        for (lv in listOf(2L, 5L, 10L, 30L, 100L)) {
            val cur = StatCalculator.calculate(warrior, lv)
            assertTrue(cur.hp > prev.hp && cur.atk > prev.atk && cur.def > prev.def, "lv=$lv")
            prev = cur
        }
    }

    /** マスターボーナスは (1 + 合計) 倍。 */
    @Test
    fun マスターボーナス() {
        val r = StatCalculator.calculate(warrior, 1, masterBonus = StatRatios(atk = 0.5, hp = 1.0))
        assertEquals(Stats(60, 12, 4, 5, 3), r)
    }

    /** 装備は最後に加算される(倍率の対象外)。 */
    @Test
    fun 装備は加算のみ() {
        val r = StatCalculator.calculate(warrior, 1, masterBonus = StatRatios(atk = 1.0), equipment = Stats(1, 2, 3, 4, 5))
        assertEquals(Stats(31, 18, 7, 9, 8), r) // atk は 8×2=16 に装備 +2
    }

    /** 極端に高いレベルでも飽和するだけで、負数・0 に化けない。 */
    @Test
    fun 巨大レベルで飽和() {
        val r = StatCalculator.calculate(warrior, Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, r.hp)
        assertEquals(Long.MAX_VALUE, r.atk)
    }

    /** Lv0 以下は不正。 */
    @Test
    fun Lv0は不正() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { StatCalculator.calculate(warrior, 0) }
    }

    /** マスター済みの全職業のボーナスが合算される。 */
    @Test
    fun マスターボーナス合計() {
        val jobs = JobRules.initialJobs().toMutableMap()
        assertEquals(StatRatios.ZERO, StatCalculator.masterBonusTotal(jobs))
        jobs[JobId.WARRIOR] = JobProgress(level = 30, mastered = true)
        jobs[JobId.SWORD_MASTER] = JobProgress(level = 30, mastered = true)
        jobs[JobId.THIEF] = JobProgress(level = 29) // 未マスターは含めない
        val total = StatCalculator.masterBonusTotal(jobs)
        assertEquals(0.15, total.atk, 1e-9) // 0.05 + 0.10
        assertEquals(0.05, total.spd, 1e-9)
        assertEquals(0.0, total.hp)
    }
}
