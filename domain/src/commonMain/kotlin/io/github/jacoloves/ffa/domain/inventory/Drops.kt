// =============================================================================
// Drops.kt
// 役割: 敵のドロップ判定。ドロップ表の各行を 1 つずつ独立に判定する(複数落ちることがある)。
//
// 確率 = chance × (1 + DROP_LUK_BONUS_MAX × pLuk / (pLuk + eLuk))、ただし最大 1.0。
//   プレイヤーの運が相手より高いほど、最大 +50% まで確率が上がる。
// 乱数: 行ごとに nextDouble() を 1 回(`値 < 確率` でドロップ)。ドロップ表が空なら消費しない。
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.data.EnemyDef
import io.github.jacoloves.ffa.domain.data.ItemId
import kotlin.math.min
import kotlin.random.Random

/** ドロップ判定。 */
object Drops {

    /** 運で補正したドロップ確率(0.0〜1.0)。両者の運が 0 以下なら補正なし。 */
    fun chance(baseChance: Double, playerLuk: Long, enemyLuk: Long): Double {
        val p = playerLuk.coerceAtLeast(0).toDouble()
        val e = enemyLuk.coerceAtLeast(0).toDouble()
        val ratio = if (p + e <= 0.0) 0.0 else p / (p + e) // 0 除算を避ける
        return min(1.0, baseChance * (1.0 + Balance.DROP_LUK_BONUS_MAX * ratio))
    }

    /**
     * 敵のドロップ表を判定し、落ちたアイテムを(表の順に)返す。
     *
     * @param enemyLuk DLv で強化済みの敵の運(Combatant.stats.luk)
     */
    fun roll(enemy: EnemyDef, playerLuk: Long, enemyLuk: Long, random: Random): List<ItemId> =
        enemy.drops
            .filter { random.nextDouble() < chance(it.chance, playerLuk, enemyLuk) }
            .map { it.item }
}
