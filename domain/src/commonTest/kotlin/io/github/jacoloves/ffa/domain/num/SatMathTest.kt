// =============================================================================
// SatMathTest.kt
// 役割: 飽和演算 SatMath の仕様を検証する(TDD: 実装より先に書いたテスト)。
//       オーバーフロー境界・NaN・Infinity など「壊れやすい端」を重点的に確認する。
// =============================================================================
package io.github.jacoloves.ffa.domain.num

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SatMathTest {

    // ---------- add ----------

    /** 通常の加算は普通の足し算と同じ結果になること。 */
    @Test
    fun add_通常の加算() {
        assertEquals(5L, SatMath.add(2L, 3L))
        assertEquals(-1L, SatMath.add(2L, -3L))
    }

    /** ちょうど MAX に届く加算は飽和ではなく正確な値になること(境界値)。 */
    @Test
    fun add_ちょうどMAXは飽和しない() {
        assertEquals(Long.MAX_VALUE, SatMath.add(Long.MAX_VALUE - 1, 1L))
    }

    /** MAX を超える加算は MAX で止まること。 */
    @Test
    fun add_オーバーフローはMAXで止まる() {
        assertEquals(Long.MAX_VALUE, SatMath.add(Long.MAX_VALUE, 1L))
        assertEquals(Long.MAX_VALUE, SatMath.add(Long.MAX_VALUE, Long.MAX_VALUE))
    }

    /** MIN を下回る加算は MIN で止まること。 */
    @Test
    fun add_アンダーフローはMINで止まる() {
        assertEquals(Long.MIN_VALUE, SatMath.add(Long.MIN_VALUE, -1L))
        assertEquals(Long.MIN_VALUE, SatMath.add(Long.MIN_VALUE, Long.MIN_VALUE))
    }

    /** MAX + MIN のように符号が逆の組み合わせは飽和せず -1 になること。 */
    @Test
    fun add_符号が逆なら飽和しない() {
        assertEquals(-1L, SatMath.add(Long.MAX_VALUE, Long.MIN_VALUE))
    }

    // ---------- mul ----------

    /** 通常の乗算。0 や負数も含む。 */
    @Test
    fun mul_通常の乗算() {
        assertEquals(12L, SatMath.mul(3L, 4L))
        assertEquals(0L, SatMath.mul(0L, Long.MAX_VALUE))
        assertEquals(0L, SatMath.mul(Long.MIN_VALUE, 0L))
        assertEquals(-12L, SatMath.mul(-3L, 4L))
        assertEquals(12L, SatMath.mul(-3L, -4L))
    }

    /** 正 × 正 のオーバーフローは MAX になること。 */
    @Test
    fun mul_正のオーバーフローはMAX() {
        assertEquals(Long.MAX_VALUE, SatMath.mul(Long.MAX_VALUE, 2L))
        assertEquals(Long.MAX_VALUE, SatMath.mul(3_037_000_500L, 3_037_000_500L))
        // 負 × 負 でも結果は正なので MAX
        assertEquals(Long.MAX_VALUE, SatMath.mul(Long.MIN_VALUE, -1L))
        assertEquals(Long.MAX_VALUE, SatMath.mul(-1L, Long.MIN_VALUE))
    }

    /** 正 × 負 のオーバーフローは MIN になること。 */
    @Test
    fun mul_負のオーバーフローはMIN() {
        assertEquals(Long.MIN_VALUE, SatMath.mul(Long.MAX_VALUE, -2L))
        assertEquals(Long.MIN_VALUE, SatMath.mul(-2L, Long.MAX_VALUE))
    }

    /** ちょうど収まる乗算は飽和しないこと(境界値)。 */
    @Test
    fun mul_ぎりぎり収まる値は飽和しない() {
        assertEquals(Long.MAX_VALUE, SatMath.mul(Long.MAX_VALUE, 1L))
        assertEquals(Long.MIN_VALUE, SatMath.mul(Long.MIN_VALUE, 1L))
        assertEquals(-Long.MAX_VALUE, SatMath.mul(Long.MAX_VALUE, -1L))
    }

    // ---------- mulRatio ----------

    /** 小数部は切り捨て(0 方向)になること。 */
    @Test
    fun mulRatio_小数部は切り捨て() {
        assertEquals(5L, SatMath.mulRatio(10L, 0.55))
        assertEquals(15L, SatMath.mulRatio(10L, 1.5))
        assertEquals(-2L, SatMath.mulRatio(-5L, 0.5))
    }

    /** 範囲外は MAX / MIN に丸めること。 */
    @Test
    fun mulRatio_範囲外は飽和() {
        assertEquals(Long.MAX_VALUE, SatMath.mulRatio(Long.MAX_VALUE, 2.0))
        assertEquals(Long.MAX_VALUE, SatMath.mulRatio(Long.MAX_VALUE, 1.0))
        assertEquals(Long.MIN_VALUE, SatMath.mulRatio(Long.MAX_VALUE, -2.0))
        assertEquals(Long.MIN_VALUE, SatMath.mulRatio(Long.MIN_VALUE, 1.0))
        assertEquals(Long.MAX_VALUE, SatMath.mulRatio(Long.MIN_VALUE, -2.0))
    }

    /** 無限大の倍率でも MAX / MIN で止まること。 */
    @Test
    fun mulRatio_Infinityは飽和() {
        assertEquals(Long.MAX_VALUE, SatMath.mulRatio(1L, Double.POSITIVE_INFINITY))
        assertEquals(Long.MIN_VALUE, SatMath.mulRatio(1L, Double.NEGATIVE_INFINITY))
        assertEquals(Long.MIN_VALUE, SatMath.mulRatio(-1L, Double.POSITIVE_INFINITY))
    }

    /** NaN は 0 になること。0 × Infinity も NaN になるので 0。 */
    @Test
    fun mulRatio_NaNは0() {
        assertEquals(0L, SatMath.mulRatio(100L, Double.NaN))
        assertEquals(0L, SatMath.mulRatio(0L, Double.POSITIVE_INFINITY))
    }

    /** 倍率 0 は 0。 */
    @Test
    fun mulRatio_倍率0は0() {
        assertEquals(0L, SatMath.mulRatio(Long.MAX_VALUE, 0.0))
    }

    // ---------- pow ----------

    /** 通常の累乗(誤差を許容して比較)。 */
    @Test
    fun pow_通常の累乗() {
        assertEquals(1.0, SatMath.pow(1.15, 0))
        assertEquals(1.15, SatMath.pow(1.15, 1))
        assertTrue(kotlin.math.abs(SatMath.pow(1.15, 2) - 1.3225) < 1e-9)
        assertEquals(1024.0, SatMath.pow(2.0, 10))
    }

    /** 巨大な指数は Infinity になってよい(mulRatio 側で飽和させる契約)。 */
    @Test
    fun pow_巨大な指数はInfinity() {
        assertEquals(Double.POSITIVE_INFINITY, SatMath.pow(10.0, 400))
        // 契約どおり、Infinity を mulRatio に渡すと MAX で止まる
        assertEquals(Long.MAX_VALUE, SatMath.mulRatio(1L, SatMath.pow(10.0, 400)))
    }

    /** 負の指数は逆数になること。 */
    @Test
    fun pow_負の指数は逆数() {
        assertEquals(0.5, SatMath.pow(2.0, -1))
    }
}
