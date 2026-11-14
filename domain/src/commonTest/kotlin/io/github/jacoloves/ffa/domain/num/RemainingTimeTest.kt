package io.github.jacoloves.ffa.domain.num

import kotlin.test.Test
import kotlin.test.assertEquals

/** formatRemaining の仕様(切り上げ・分秒・境界)を検証する。 */
class RemainingTimeTest {
    @Test
    fun 秒だけの表示() {
        assertEquals("0秒", formatRemaining(0))
        assertEquals("0秒", formatRemaining(-5))
        assertEquals("1秒", formatRemaining(1))
        assertEquals("1秒", formatRemaining(1_000))
        assertEquals("2秒", formatRemaining(1_001))
        assertEquals("59秒", formatRemaining(59_000))
    }

    @Test
    fun 分と秒の表示() {
        assertEquals("1分0秒", formatRemaining(60_000))
        assertEquals("1分1秒", formatRemaining(60_001))
        assertEquals("1分29秒", formatRemaining(88_100))
        assertEquals("1分30秒", formatRemaining(90_000))
        assertEquals("3分0秒", formatRemaining(180_000))
    }
}
