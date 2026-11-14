// =============================================================================
// SatMath.kt
// 役割: Long の「飽和演算」(オーバーフローしたら折り返さず上限/下限で止まる演算)を提供する。
//
// なぜ飽和演算か:
//   このゲームはインフレ型で、数値が Long の範囲(約 922 京)に迫る。
//   通常の Long 演算だと、オーバーフローで突然負の値に化けて(折り返して)ゲームが壊れる。
//   上限で止まる方がプレイヤーにとって自然で、バグも起きにくい(plan.md の決定事項)。
//
// なぜ Double ではなく Long か:
//   丸め誤差のない整数で状態を持ちたいため。倍率計算だけ一時的に Double を使い、
//   結果は mulRatio で必ず Long に戻す(範囲外は飽和、NaN は 0)。
// =============================================================================
package io.github.jacoloves.ffa.domain.num

import kotlin.math.pow

/**
 * Long の飽和演算をまとめたオブジェクト。
 * すべて純粋関数で、状態を持たない。
 */
object SatMath {

    /** Double で表せる 2^63(Long.MAX_VALUE + 1 に相当)。これ以上は Long に収まらない。 */
    private const val TWO_POW_63 = 9.223372036854775807E18

    /**
     * 飽和加算。
     *
     * オーバーフローしたら [Long.MAX_VALUE]、アンダーフローしたら [Long.MIN_VALUE] を返す。
     */
    fun add(a: Long, b: Long): Long {
        val sum = a + b
        // 「a と b が同符号なのに、結果の符号が違う」ときだけオーバーフローしている。
        // (a xor sum) と (b xor sum) の両方の符号ビットが立つ = 両方と結果の符号が違う、という判定。
        val overflowed = ((a xor sum) and (b xor sum)) < 0
        if (!overflowed) return sum
        // オーバーフロー時、a の符号が飽和方向を表す(正なら上限、負なら下限)。
        return if (a < 0) Long.MIN_VALUE else Long.MAX_VALUE
    }

    /**
     * 飽和乗算。
     *
     * 結果が範囲外なら、結果の符号に応じて MAX / MIN で止まる。
     */
    fun mul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val product = a * b
        // 除算で元に戻せなければオーバーフローしている。
        // ただし MIN_VALUE / -1 は除算自体がオーバーフローして誤判定するので、その 2 通りを先に弾く。
        val overflowed =
            (a == -1L && b == Long.MIN_VALUE) ||
                (b == -1L && a == Long.MIN_VALUE) ||
                product / b != a
        if (!overflowed) return product
        // 結果の符号は「2 つの符号が違えば負」。
        return if ((a < 0) != (b < 0)) Long.MIN_VALUE else Long.MAX_VALUE
    }

    /**
     * 倍率をかけて Long に戻す。
     *
     * - 小数部は 0 方向に切り捨てる(正の数なら床、負の数なら天井)。
     * - Long の範囲外(Infinity を含む)は MAX / MIN に丸める。
     * - NaN は 0 にする(0 × Infinity のような不定値でゲームを壊さないため)。
     */
    fun mulRatio(a: Long, ratio: Double): Long {
        val scaled = a.toDouble() * ratio
        return when {
            scaled.isNaN() -> 0L
            scaled >= TWO_POW_63 -> Long.MAX_VALUE
            scaled < -TWO_POW_63 -> Long.MIN_VALUE
            else -> scaled.toLong() // 範囲内なので安全。toLong は 0 方向への切り捨て。
        }
    }

    /**
     * 倍率計算用の累乗(`base^exp`)。
     *
     * 結果が Infinity になってもよい。呼び出し側の [mulRatio] が飽和させる契約。
     */
    fun pow(base: Double, exp: Int): Double = base.pow(exp)
}
