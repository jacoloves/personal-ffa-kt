// =============================================================================
// GameDataItemsTest.kt
// 役割: アイテム定義とドロップ表の整合性(データ入力ミスの検出)を検証する。
// =============================================================================
package io.github.jacoloves.ffa.domain.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameDataItemsTest {

    @Test
    fun すべてのItemIdに定義がある() {
        for (id in ItemId.entries) assertEquals(id, GameData.item(id).id)
        assertEquals(ItemId.entries.size, GameData.items.size)
    }

    /** 各エリアに、武器・防具・装身具が 1 つずつ売られている。 */
    @Test
    fun 各エリアに3枠のショップ商品() {
        for (area in AreaId.entries) {
            val shop = GameData.shopItems(area)
            assertEquals(Slot.entries.toSet(), shop.map { it.slot }.toSet(), "$area の商品の枠")
            assertEquals(3, shop.size)
        }
    }

    /** 売り物は価格とエリアの両方を持ち、非売品はどちらも持たない。 */
    @Test
    fun 価格とショップエリアの整合() {
        for (item in GameData.items.values) {
            if (item.price != null) {
                assertTrue(item.price > 0, "${item.name} の価格")
                assertNotNull(item.shopArea, "${item.name} は価格があるのに shopArea が無い")
            } else {
                assertNull(item.shopArea, "${item.name} は非売品なのに shopArea がある")
            }
        }
    }

    /** 各エリアにドロップ限定品が 1〜2 個ある。 */
    @Test
    fun 各エリアにドロップ限定品() {
        val dropOnly = GameData.items.values.filter { it.price == null }
        assertEquals(4, dropOnly.size)
        // どの敵がどの限定品を落とすか追跡し、各エリアの敵のどれかが落とすことを確認する
        for (area in GameData.areas.values) {
            val dropped = area.enemies.flatMap { GameData.enemy(it).drops }.map { it.item }.toSet()
            assertTrue(dropped.any { GameData.item(it).price == null }, "${area.name} にドロップ限定品が落ちる敵がいない")
        }
    }

    /** 価格・ステータスはエリアが進むほど大きくなる。 */
    @Test
    fun エリアが進むほど装備が強く高価() {
        val order = listOf(AreaId.MEADOW, AreaId.FOREST, AreaId.CAVE, AreaId.VOLCANO)
        val weaponAtk = order.map { a -> GameData.shopItems(a).first { it.slot == Slot.WEAPON }.stats.atk }
        val price = order.map { a -> GameData.shopItems(a).sumOf { it.price!! } }
        assertEquals(weaponAtk.sorted(), weaponAtk)
        assertEquals(price.sorted(), price)
        assertEquals(4, weaponAtk.toSet().size)
    }

    /** すべての装備の枠が一致するステータスを持つ(全 0 の装備は無い)。 */
    @Test
    fun 装備のステータスは0ではない() {
        for (item in GameData.items.values) {
            val s = item.stats
            assertTrue(s.hp + s.atk + s.def + s.spd + s.luk > 0, "${item.name} のステータスが全て 0")
        }
    }

    /** ドロップ表が参照するアイテムは必ず定義済みで、確率は 0 より大きく 1 以下。 */
    @Test
    fun ドロップ表は定義済みアイテムだけを参照() {
        for (enemy in GameData.enemies.values) {
            assertTrue(enemy.drops.isNotEmpty(), "${enemy.name} のドロップ表が空")
            for (d in enemy.drops) {
                assertNotNull(GameData.items[d.item], "${enemy.name} が未定義のアイテム ${d.item} を落とす")
                assertTrue(d.chance > 0.0 && d.chance <= 1.0)
            }
            assertEquals(enemy.drops.size, enemy.drops.map { it.item }.toSet().size, "${enemy.name} のドロップ表に重複")
        }
    }

    /** 初期装備の木の剣は草原のショップ商品でもある。 */
    @Test
    fun 木の剣は草原の武器() {
        val sword = GameData.item(ItemId.WOODEN_SWORD)
        assertEquals(Slot.WEAPON, sword.slot)
        assertEquals(AreaId.MEADOW, sword.shopArea)
    }
}
