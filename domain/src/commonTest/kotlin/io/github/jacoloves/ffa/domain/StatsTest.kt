// =============================================================================
// StatsTest.kt
// 役割: Stats / StatRatios の演算(飽和・倍率・合算)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class StatsTest {

    private val a = Stats(hp = 10, atk = 20, def = 30, spd = 40, luk = 50)
    private val b = Stats(hp = 1, atk = 2, def = 3, spd = 4, luk = 5)

    /** ZERO は全項目 0。 */
    @Test
    fun ZEROは全項目0() {
        assertEquals(Stats(0, 0, 0, 0, 0), Stats.ZERO)
    }

    /** plus は項目ごとの加算で、ZERO は単位元になること。 */
    @Test
    fun plus_項目ごとに加算() {
        assertEquals(Stats(11, 22, 33, 44, 55), a + b)
        assertEquals(a, a + Stats.ZERO)
    }

    /** plus は飽和する(項目ごとに独立して MAX で止まる)。 */
    @Test
    fun plus_飽和する() {
        val big = Stats(Long.MAX_VALUE, 0, 0, 0, 0)
        val r = big + Stats(1, 1, 0, 0, 0)
        assertEquals(Long.MAX_VALUE, r.hp)
        assertEquals(1L, r.atk)
    }

    /** times(Double) は全項目に同じ倍率をかける(切り捨て)。 */
    @Test
    fun times_全項目に倍率() {
        assertEquals(Stats(15, 30, 45, 60, 75), a * 1.5)
        assertEquals(Stats(5, 10, 15, 20, 25), a * 0.5)
        // 切り捨ての確認: 1 × 0.9 = 0
        assertEquals(Stats.ZERO, b * 0.1)
    }

    /** times(Double) も飽和し、NaN は 0 になる。 */
    @Test
    fun times_飽和とNaN() {
        val big = Stats(Long.MAX_VALUE, 1, 1, 1, 1)
        assertEquals(Long.MAX_VALUE, (big * 2.0).hp)
        assertEquals(Stats.ZERO, a * Double.NaN)
    }

    /** times(StatRatios) は項目ごとに別の倍率をかける。 */
    @Test
    fun times_項目ごとの倍率() {
        val ratios = StatRatios(hp = 2.0, atk = 1.0, def = 0.5, spd = 0.0, luk = 1.1)
        assertEquals(Stats(20, 20, 15, 0, 55), a * ratios)
    }

    /** StatRatios.ZERO は全項目 0.0、plus で項目ごとに合算できる。 */
    @Test
    fun StatRatios_合算() {
        assertEquals(StatRatios(0.0, 0.0, 0.0, 0.0, 0.0), StatRatios.ZERO)
        val atk5 = StatRatios(atk = 0.05)
        val atk10spd5 = StatRatios(atk = 0.10, spd = 0.05)
        val sum = atk5 + atk10spd5
        assertEquals(0.15000000000000002, sum.atk, 1e-9)
        assertEquals(0.05, sum.spd, 1e-9)
        assertEquals(0.0, sum.hp)
        assertEquals(atk5, atk5 + StatRatios.ZERO)
    }
}
