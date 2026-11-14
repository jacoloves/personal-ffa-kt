// =============================================================================
// EnemyFactoryTest.kt
// 役割: DLv による敵の強化・報酬倍率と、重み付き抽選の仕様を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.battle

import io.github.jacoloves.ffa.domain.Balance
import io.github.jacoloves.ffa.domain.ScriptedRandom
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.AreaId
import io.github.jacoloves.ffa.domain.data.EnemyDef
import io.github.jacoloves.ffa.domain.data.EnemyId
import io.github.jacoloves.ffa.domain.data.GameData
import io.github.jacoloves.ffa.domain.num.SatMath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EnemyFactoryTest {

    private val def = EnemyDef(
        id = EnemyId.SLIME, name = "テスト敵",
        stats = Stats(hp = 400, atk = 100, def = 50, spd = 20, luk = 10),
        exp = 100, gold = 50, weight = 1,
    )

    /** DLv 1 は倍率 1.0 なので、定義どおりのステータス・名前になる。 */
    @Test
    fun dlv1は定義どおり() {
        val c = EnemyFactory.create(def, 1)
        assertEquals("テスト敵", c.name)
        assertEquals(def.stats, c.stats)
        assertEquals(EnemyReward(exp = 100, gold = 50), EnemyFactory.reward(def, 1))
    }

    /** DLv が上がるとステータスが 1.15^(dlv-1) 倍(切り捨て)になる。 */
    @Test
    fun dlvでステータスが指数的に増える() {
        val ratio = SatMath.pow(Balance.DLV_ENEMY_GROWTH, 4) // dlv=5
        val c = EnemyFactory.create(def, 5)
        assertEquals(def.stats * ratio, c.stats)
        // おおよその範囲も確認(1.15^4 ≒ 1.749)
        assertTrue(c.stats.hp in 698..700, "hp=${c.stats.hp}")
        // 全項目が単調に増えている
        assertTrue(c.stats.atk > def.stats.atk && c.stats.def > def.stats.def)
    }

    /** 報酬は 1.12^(dlv-1) 倍で、敵ステータスより緩やかに増える。 */
    @Test
    fun dlvで報酬が緩やかに増える() {
        val reward = EnemyFactory.reward(def, 5)
        val ratio = SatMath.pow(Balance.DLV_REWARD_GROWTH, 4)
        assertEquals(SatMath.mulRatio(100, ratio), reward.exp)
        assertEquals(SatMath.mulRatio(50, ratio), reward.gold)
        assertTrue(reward.exp.toDouble() / 100 < EnemyFactory.create(def, 5).stats.hp.toDouble() / 400)
    }

    /** DLv が極端に大きくても飽和するだけで、負数にならないこと。 */
    @Test
    fun 巨大なdlvでも飽和する() {
        val c = EnemyFactory.create(def, 100_000)
        assertEquals(Long.MAX_VALUE, c.stats.hp)
        assertEquals(Long.MAX_VALUE, EnemyFactory.reward(def, 100_000).exp)
    }

    /** DLv 0 以下は不正で、日本語の説明つきで失敗すること。 */
    @Test
    fun dlvが0以下は不正() {
        val e = assertFailsWith<IllegalArgumentException> { EnemyFactory.create(def, 0) }
        assertTrue(e.message!!.contains("DLv"))
        assertFailsWith<IllegalArgumentException> { EnemyFactory.reward(def, -3) }
    }

    /** 重み付き抽選: 乱数の値が重みの境界のどちら側かで敵が決まる。 */
    @Test
    fun 重み付き抽選の境界() {
        val area = GameData.area(AreaId.MEADOW)
        val defs = area.enemies.map { GameData.enemy(it) }
        val total = defs.sumOf { it.weight }
        // 0 は先頭の敵
        assertEquals(defs.first().id, EnemyFactory.pickEnemy(area, ScriptedRandom(ints = listOf(0))).id)
        // 先頭の重みちょうどは 2 番目(境界)、1 つ手前は先頭
        val w0 = defs[0].weight
        assertEquals(defs[0].id, EnemyFactory.pickEnemy(area, ScriptedRandom(ints = listOf(w0 - 1))).id)
        assertEquals(defs[1].id, EnemyFactory.pickEnemy(area, ScriptedRandom(ints = listOf(w0))).id)
        // 最大値(total-1)は末尾の敵
        assertEquals(defs.last().id, EnemyFactory.pickEnemy(area, ScriptedRandom(ints = listOf(total - 1))).id)
    }

    /** 十分な回数引けば、エリアの全敵が出現し、他エリアの敵は出ない。 */
    @Test
    fun 抽選で全敵が出現する() {
        val area = GameData.area(AreaId.FOREST)
        val random = Random(42)
        val seen = (1..2000).map { EnemyFactory.pickEnemy(area, random).id }.toSet()
        assertEquals(area.enemies.toSet(), seen)
    }
}
