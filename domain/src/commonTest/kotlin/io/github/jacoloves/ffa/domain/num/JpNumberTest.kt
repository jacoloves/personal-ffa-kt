// =============================================================================
// JpNumberTest.kt
// 役割: 和式数値表記 formatJp の仕様(設計書の例をすべて含む)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.num

import kotlin.test.Test
import kotlin.test.assertEquals

class JpNumberTest {

    /** 1万未満はそのまま表示(カンマなし)。 */
    @Test
    fun formatJp_1万未満はそのまま() {
        assertEquals("0", formatJp(0L))
        assertEquals("1", formatJp(1L))
        assertEquals("9999", formatJp(9999L))
    }

    /** 1万ちょうどは「1万」。下位が 0 なので省略される(境界値)。 */
    @Test
    fun formatJp_1万ちょうど() {
        assertEquals("1万", formatJp(10_000L))
    }

    /** 設計書の例: 万の位。 */
    @Test
    fun formatJp_万の例() {
        assertEquals("1万2345", formatJp(12_345L))
        // 下位の桁が 0 で始まる場合も数値として表示する
        assertEquals("1万5", formatJp(10_005L))
        assertEquals("9999万9999", formatJp(99_999_999L))
    }

    /** 設計書の例: 億の位。 */
    @Test
    fun formatJp_億の例() {
        assertEquals("1億2345万", formatJp(123_456_789L))
        assertEquals("1億", formatJp(100_000_000L))
        // 下位 2 単位より下(万未満)は切り捨てて表示しない
        assertEquals("1億", formatJp(100_000_123L))
    }

    /** 設計書の例: 兆の位。 */
    @Test
    fun formatJp_兆の例() {
        assertEquals("1兆2345億", formatJp(1_2345_0000_0000L))
        assertEquals("1兆", formatJp(1_0000_0000_0000L))
    }

    /** 京の位。Long.MAX_VALUE は 922京3372兆。 */
    @Test
    fun formatJp_京とLongの最大値() {
        assertEquals("1京", formatJp(10_000_000_000_000_000L))
        assertEquals("922京3372兆", formatJp(Long.MAX_VALUE))
    }

    /** 負の数は先頭に - を付ける。 */
    @Test
    fun formatJp_負の数() {
        assertEquals("-1", formatJp(-1L))
        assertEquals("-1万2345", formatJp(-12_345L))
        assertEquals("-9999", formatJp(-9999L))
    }

    /** Long.MIN_VALUE は絶対値が Long に収まらないが、正しく扱えること。 */
    @Test
    fun formatJp_Long最小値() {
        assertEquals("-922京3372兆", formatJp(Long.MIN_VALUE))
    }
}
