// =============================================================================
// ScriptedRandom.kt
// 役割: テスト専用の「決まった値を順に返す」乱数。
//       会心・回避・追撃など、確率で分岐する経路を seed に頼らず確実に通すために使う。
//
// 使い方:
//   - nextDouble() は doubles の先頭から順に返し、尽きたら defaultDouble(既定 0.5)を返す。
//   - nextInt(until) は ints の先頭から順に返し、尽きたら 0 を返す。
//   - 既定の 0.5 は BattleEngine の確率判定で「何も起きない」値になるよう選んである:
//       回避率の上限 0.30 < 0.5 → 回避しない / 会心率の上限 0.25 < 0.5 → 会心しない /
//       追撃率の上限 0.5 は「0.5 < 0.5」が偽 → 追撃しない / ダメージ乱数 0.9 + 0.5×0.2 = 1.0 倍。
// =============================================================================
package io.github.jacoloves.ffa.domain

import kotlin.random.Random

/** 決まった値を順に返すテスト用 Random。 */
class ScriptedRandom(
    doubles: List<Double> = emptyList(),
    ints: List<Int> = emptyList(),
    private val defaultDouble: Double = 0.5,
) : Random() {
    private val doubleQueue = ArrayDeque(doubles)
    private val intQueue = ArrayDeque(ints)

    /** 消費した nextDouble の回数(乱数の呼び出し順序を検証するために公開)。 */
    var doubleCalls = 0
        private set

    override fun nextBits(bitCount: Int): Int = 0

    override fun nextDouble(): Double {
        doubleCalls++
        return doubleQueue.removeFirstOrNull() ?: defaultDouble
    }

    override fun nextInt(until: Int): Int = intQueue.removeFirstOrNull() ?: 0
}
