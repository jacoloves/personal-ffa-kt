// =============================================================================
// EnemyFactory.kt
// 役割: 敵の定義(EnemyDef)から、DLv に応じた戦闘用の敵(Combatant)と報酬を作り、
//       エリア内で重み付き抽選で出現する敵を選ぶ。
//
// 設計判断:
//   - 敵のステータス倍率(1.15^(dlv-1))と報酬倍率(1.12^(dlv-1))を分けた。
//     報酬の伸びを敵より緩やかにして、DLv を上げるほど「割に合わなくなる」過熱を抑えるため。
//   - 倍率は SatMath.pow → mulRatio で計算するので、DLv が極端に大きくても Long の上限で止まる。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.data.AreaDef
import io.github.jacoloves.ffa.domain.data.EnemyDef
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.SatMath
import kotlin.random.Random

/** 敵を倒したときの基本報酬(DLv 倍率適用済み)。 */
data class EnemyReward(val exp: Long, val gold: Long)

/** 敵の生成・抽選を担う。状態を持たない。 */
object EnemyFactory {

    /**
     * DLv に応じて強化した敵を作る。ステータス = `def.stats × 1.15^(dlv-1)`。
     *
     * @throws IllegalArgumentException dlv が 1 未満のとき
     */
    fun create(def: EnemyDef, dlv: Int): Combatant {
        requireValidDlv(dlv)
        val ratio = SatMath.pow(Balance.DLV_ENEMY_GROWTH, dlv - 1)
        return Combatant(def.name, def.stats * ratio)
    }

    /**
     * DLv に応じた報酬を返す。EXP・ゴールド = 基礎値 × 1.12^(dlv-1)。
     *
     * @throws IllegalArgumentException dlv が 1 未満のとき
     */
    fun reward(def: EnemyDef, dlv: Int): EnemyReward {
        requireValidDlv(dlv)
        val ratio = SatMath.pow(Balance.DLV_REWARD_GROWTH, dlv - 1)
        return EnemyReward(
            exp = SatMath.mulRatio(def.exp, ratio),
            gold = SatMath.mulRatio(def.gold, ratio),
        )
    }

    /**
     * エリアに出現する敵を、重み(weight)に比例した確率で 1 体選ぶ。
     *
     * 方法: 重みの合計 total に対し 0 until total の整数を 1 つ引き、
     *       先頭から重みを引いていって 0 未満になった敵を選ぶ(整数のみなので誤差がない)。
     *
     * @throws IllegalStateException エリアの敵が空、または重みの合計が 0 以下のとき
     */
    fun pickEnemy(area: AreaDef, random: Random): EnemyDef {
        val candidates = area.enemies.map { GameData.enemy(it) }
        val total = candidates.sumOf { it.weight }
        check(candidates.isNotEmpty() && total > 0) {
            "エリア「${area.name}」から敵を選べません(敵の数=${candidates.size}、重みの合計=$total)。" +
                "GameData でこのエリアに重み 1 以上の敵を 1 体以上登録してください。"
        }
        var roll = random.nextInt(total)
        for (def in candidates) {
            roll -= def.weight
            if (roll < 0) return def
        }
        // 到達しない(roll < total なので必ずどこかで 0 未満になる)。型の都合で最後の敵を返す。
        return candidates.last()
    }

    private fun requireValidDlv(dlv: Int) {
        require(dlv >= 1) {
            "DLv に $dlv が指定されました。DLv は 1 以上の整数です。" +
                "呼び出し元で選択中の DLv が 1 以上になるよう検証してください。"
        }
    }
}
