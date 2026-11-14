// =============================================================================
// DropsTest.kt
// 役割: ドロップ確率の運補正式と、ドロップ判定の仕様を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.inventory

import io.github.jacoloves.ffa.domain.ScriptedRandom
import io.github.jacoloves.ffa.domain.Stats
import io.github.jacoloves.ffa.domain.data.DropEntry
import io.github.jacoloves.ffa.domain.data.EnemyDef
import io.github.jacoloves.ffa.domain.data.EnemyId
import io.github.jacoloves.ffa.domain.data.ItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropsTest {

    private fun enemy(vararg drops: DropEntry) = EnemyDef(
        EnemyId.SLIME, "テスト敵", Stats(1, 1, 1, 1, 1), exp = 1, gold = 1, weight = 1, drops = drops.toList(),
    )

    /** 確率 = chance × (1 + 0.5 × pLuk/(pLuk+eLuk))。 */
    @Test
    fun 確率の式() {
        assertEquals(0.1, Drops.chance(0.1, playerLuk = 0, enemyLuk = 10), 1e-9)      // 運 0 → 補正なし
        assertEquals(0.1 * 1.25, Drops.chance(0.1, 10, 10), 1e-9)                      // 比率 0.5 → ×1.25
        assertEquals(0.1 * 1.5, Drops.chance(0.1, 1_000_000_000, 0), 1e-9)             // 敵の運 0 → ×1.5
    }

    /** 両者の運が 0 でも 0 除算にならず、補正なし。 */
    @Test
    fun 運が両方0でも壊れない() {
        assertEquals(0.2, Drops.chance(0.2, 0, 0), 1e-9)
    }

    /** 確率は 1.0 を超えない。 */
    @Test
    fun 確率は1を超えない() {
        assertEquals(1.0, Drops.chance(0.9, 1_000_000, 0), 1e-9)
    }

    /** 判定値が確率未満ならドロップし、以上なら落ちない(境界値)。 */
    @Test
    fun 判定の境界() {
        val e = enemy(DropEntry(ItemId.WOODEN_SWORD, 0.5))
        // playerLuk 0 → 確率ちょうど 0.5。0.4999 は落ち、0.5 は落ちない。
        assertEquals(listOf(ItemId.WOODEN_SWORD), Drops.roll(e, 0, 10, ScriptedRandom(doubles = listOf(0.4999))))
        assertEquals(emptyList(), Drops.roll(e, 0, 10, ScriptedRandom(doubles = listOf(0.5))))
    }

    /** 複数のエントリを 1 つずつ独立に判定する(複数落ちることがある)。 */
    @Test
    fun 複数同時にドロップ() {
        val e = enemy(DropEntry(ItemId.WOODEN_SWORD, 0.5), DropEntry(ItemId.LEATHER_ARMOR, 0.5), DropEntry(ItemId.LUCKY_CHARM, 0.5))
        val r = Drops.roll(e, 0, 10, ScriptedRandom(doubles = listOf(0.1, 0.9, 0.2)))
        assertEquals(listOf(ItemId.WOODEN_SWORD, ItemId.LUCKY_CHARM), r)
    }

    /** ドロップ表が空なら乱数も消費せず空。 */
    @Test
    fun ドロップ表が空() {
        val random = ScriptedRandom()
        assertTrue(Drops.roll(enemy(), 5, 5, random).isEmpty())
        assertEquals(0, random.doubleCalls)
    }
}
