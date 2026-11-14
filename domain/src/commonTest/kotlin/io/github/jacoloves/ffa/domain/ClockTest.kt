// =============================================================================
// ClockTest.kt
// 役割: Clock が fun interface としてラムダで固定時刻を注入できること(テスト容易性の要件)を確認する。
// =============================================================================
package io.github.jacoloves.ffa.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ClockTest {

    /** ラムダで固定時刻の Clock を作れること。 */
    @Test
    fun ラムダで固定時刻を注入できる() {
        val clock = Clock { 1_700_000_000_000L }
        assertEquals(1_700_000_000_000L, clock.nowMillis())
    }

    /** 状態を持つ偽 Clock で時間経過を再現できること(クールタイムのテストで使う想定)。 */
    @Test
    fun 時間経過を再現できる() {
        var now = 0L
        val clock = Clock { now }
        now += 5_000
        assertEquals(5_000L, clock.nowMillis())
    }
}
