// =============================================================================
// JpNumber.kt
// 役割: Long を和式表記(万・億・兆・京)の文字列にする formatJp を提供する。
//
// 表記ルール:
//   - 1 万未満はそのまま(カンマなし)。
//   - 1 万以上は「上位 2 単位まで」を表示し、下位の単位が 0 なら省略する。
//     例: 123456789 → "1億2345万"、100000123 → "1億"
//   - 負の数は先頭に "-"。Long.MIN_VALUE も扱える。
// =============================================================================
package io.github.jacoloves.ffa.domain.num

/** 単位 1 つ分の定義(値と表示名)。 */
private class JpUnit(val value: ULong, val label: String)

/**
 * 大きい順の単位表。最後の「1」は万の下の端数(単位名なし)を表す番兵。
 * 上位単位とその 1 つ下の単位のペアで「上位 2 単位」を作る。
 */
private val JP_UNITS = listOf(
    JpUnit(10_000_000_000_000_000uL, "京"),
    JpUnit(1_000_000_000_000uL, "兆"),
    JpUnit(100_000_000uL, "億"),
    JpUnit(10_000uL, "万"),
    JpUnit(1uL, ""),
)

/**
 * Long を和式表記にする。
 *
 * @param n 表示する値(負でもよい)
 * @return 例: `12345` → `"1万2345"`、`Long.MAX_VALUE` → `"922京3372兆"`
 */
fun formatJp(n: Long): String {
    // 絶対値を ULong で求める。Long.MIN_VALUE は -n が Long に収まらないため、
    // 「(-(n+1)) + 1」という迂回で安全に符号なしの大きさへ変換する。
    val magnitude: ULong = if (n < 0) (-(n + 1)).toULong() + 1uL else n.toULong()
    val body = formatMagnitude(magnitude)
    return if (n < 0) "-$body" else body
}

/** 符号なしの大きさを和式表記にする(符号の処理は formatJp 側)。 */
private fun formatMagnitude(magnitude: ULong): String {
    // 1 万未満はそのまま。
    if (magnitude < 10_000uL) return magnitude.toString()

    // 大きい単位から探し、最初に収まる単位を「上位単位」にする。
    // 万以上であることは確認済みなので、必ず「京〜万」のどれかが見つかる。
    val index = JP_UNITS.indexOfFirst { magnitude >= it.value }
    val upper = JP_UNITS[index]
    val lower = JP_UNITS[index + 1] // 1 つ下の単位(万の場合は端数)

    val upperCount = magnitude / upper.value
    val lowerCount = (magnitude % upper.value) / lower.value

    // 下位の単位が 0 なら省略する(例: 1億0000万 → "1億")。
    return if (lowerCount == 0uL) {
        "$upperCount${upper.label}"
    } else {
        "$upperCount${upper.label}$lowerCount${lower.label}"
    }
}
