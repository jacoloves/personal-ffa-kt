// =============================================================================
// Stats.kt
// 役割: キャラクター・敵・装備が持つ 5 つのステータス(Stats)と、
//       その倍率(StatRatios)を表す不変の値オブジェクト。
//
// 設計上の判断:
//   - すべて Long + 飽和演算(SatMath)。インフレ型でも折り返さないため。
//   - data class で不変にし、演算は新しいインスタンスを返す(純粋関数で扱いやすく、テストしやすい)。
//   - StatRatios を別型にしたのは「値」と「割合」を型で取り違えないため。
// =============================================================================
package io.github.jacoloves.ffa.domain

import io.github.jacoloves.ffa.domain.num.SatMath

/**
 * HP / 攻撃 / 防御 / 素早さ / 運 の 5 ステータス。
 */
data class Stats(
    /** 体力。0 以下になると戦闘に負ける。 */
    val hp: Long,
    /** 攻撃力。 */
    val atk: Long,
    /** 防御力。 */
    val def: Long,
    /** 素早さ。先攻・追撃・回避に影響する。 */
    val spd: Long,
    /** 運。会心率・ドロップ率に影響する。 */
    val luk: Long,
) {
    /** 項目ごとの飽和加算。 */
    operator fun plus(other: Stats): Stats = Stats(
        hp = SatMath.add(hp, other.hp),
        atk = SatMath.add(atk, other.atk),
        def = SatMath.add(def, other.def),
        spd = SatMath.add(spd, other.spd),
        luk = SatMath.add(luk, other.luk),
    )

    /** 全項目に同じ倍率をかける(切り捨て・飽和・NaN は 0)。 */
    operator fun times(ratio: Double): Stats = Stats(
        hp = SatMath.mulRatio(hp, ratio),
        atk = SatMath.mulRatio(atk, ratio),
        def = SatMath.mulRatio(def, ratio),
        spd = SatMath.mulRatio(spd, ratio),
        luk = SatMath.mulRatio(luk, ratio),
    )

    /** 全項目に整数を飽和乗算する(成長値 × レベルのような計算用)。 */
    operator fun times(n: Long): Stats = Stats(
        hp = SatMath.mul(hp, n),
        atk = SatMath.mul(atk, n),
        def = SatMath.mul(def, n),
        spd = SatMath.mul(spd, n),
        luk = SatMath.mul(luk, n),
    )

    /** 項目ごとに別々の倍率をかける(マスターボーナスなど)。 */
    operator fun times(other: StatRatios): Stats = Stats(
        hp = SatMath.mulRatio(hp, other.hp),
        atk = SatMath.mulRatio(atk, other.atk),
        def = SatMath.mulRatio(def, other.def),
        spd = SatMath.mulRatio(spd, other.spd),
        luk = SatMath.mulRatio(luk, other.luk),
    )

    companion object {
        /** 全項目 0。加算の単位元。 */
        val ZERO = Stats(0, 0, 0, 0, 0)
    }
}

/**
 * ステータスごとの割合(倍率)。マスターボーナス「攻撃 +5%」なら `StatRatios(atk = 0.05)`。
 * 省略した項目は 0.0。[plus] で合算できる。
 */
data class StatRatios(
    val hp: Double = 0.0,
    val atk: Double = 0.0,
    val def: Double = 0.0,
    val spd: Double = 0.0,
    val luk: Double = 0.0,
) {
    /** 項目ごとの加算(複数の職業のボーナスを合計するのに使う)。 */
    operator fun plus(other: StatRatios): StatRatios = StatRatios(
        hp = hp + other.hp,
        atk = atk + other.atk,
        def = def + other.def,
        spd = spd + other.spd,
        luk = luk + other.luk,
    )

    companion object {
        /** 全項目 0.0。合算の単位元。 */
        val ZERO = StatRatios()

        /** 全項目 1.0(等倍)。「1 + ボーナス」を作る土台。 */
        val ONE = StatRatios(1.0, 1.0, 1.0, 1.0, 1.0)
    }
}
